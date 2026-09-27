import { schedule, reviewInterval, RATINGS, STATES, ALGORITHM_ID, DEFAULT_CONFIG, MINUTE_MS } from './srs.js'
import { schedulingOf, withScheduling } from './codec.js'
import { uuid } from './ids.js'

// Mesmas regras de android/core/.../StudyPlanner.kt e StudySession.kt.

export const LEARN_AHEAD_MS = 20 * MINUTE_MS
export const REQUEUE_GAP = 3

const NEW = 0
const LEARNING = 1
const REVIEW = 2
const RELEARNING = 3

const inSteps = (stateCode) => stateCode === LEARNING || stateCode === RELEARNING

export function compareIds(a, b) {
  return a < b ? -1 : a > b ? 1 : 0
}

const byDue = (a, b) => a.d - b.d || compareIds(a.i, b.i)

/** Dia de estudo que contém [now], virando às [cutoffHour] horas locais. */
export function studyDayWindow(now, cutoffHour) {
  const date = new Date(now)
  date.setHours(cutoffHour, 0, 0, 0)
  if (date.getTime() > now) date.setDate(date.getDate() - 1)
  const start = date.getTime()
  date.setDate(date.getDate() + 1)
  return { start, end: date.getTime() }
}

/**
 * Fila da sessão: etapas curtas vencidas (ou vencendo em 20 min), revisões até o fim do
 * dia (as mais atrasadas primeiro) e cartões novos, respeitando os limites diários.
 */
export function planSession(cards, now, dayEnd, settings, newIntroducedToday = 0, reviewsDoneToday = 0) {
  const learning = cards.filter((c) => inSteps(c.s) && c.d <= now + LEARN_AHEAD_MS).sort(byDue)
  const reviewQuota = Math.max(0, settings.maxReviewsPerDay - reviewsDoneToday)
  const reviews = cards.filter((c) => c.s === REVIEW && c.d < dayEnd).sort(byDue).slice(0, reviewQuota)
  const newQuota = Math.max(0, settings.newCardsPerDay - newIntroducedToday)
  const fresh = cards.filter((c) => c.s === NEW).sort(byDue).slice(0, newQuota)
  return learning.concat(reviews, fresh)
}

/**
 * Reaplica respostas da outbox mais novas que o estado gravado do cartão. Assim, se o
 * app fechar antes de gravar o deck, nenhum progresso se perde.
 */
export function applyPending(cards, outbox, deckId, config = DEFAULT_CONFIG) {
  const pending = outbox
    .filter((r) => r.k === deckId)
    .sort((a, b) => a.t - b.t || compareIds(a.id, b.id))
  if (pending.length === 0) return { cards, changed: false }

  const result = cards.slice()
  const index = {}
  result.forEach((card, i) => (index[card.i] = i))
  let changed = false
  for (const review of pending) {
    const i = index[review.c]
    if (i === undefined) continue
    const card = result[i]
    if (review.t <= (card.lr || 0)) continue
    result[i] = withScheduling(card, schedule(schedulingOf(card), RATINGS[review.r - 1], review.t, config))
    changed = true
  }
  return { cards: result, changed }
}

/** Tudo o que a Home e a página de estudo precisam de um deck. */
export function loadDeckForStudy(store, deckId, now) {
  const settings = store.settings()
  const config = settings.scheduler || DEFAULT_CONFIG
  const loaded = applyPending(store.loadCards(deckId), store.outbox(), deckId, config)
  const day = studyDayWindow(now, settings.dayCutoffHour)
  const today = store.todayCounts(deckId, day.start)
  const queue = planSession(loaded.cards, now, day.end, settings, today.newIntroduced, today.reviewsDone)
  return { cards: loaded.cards, changed: loaded.changed, queue, day, config }
}

/**
 * Sessão de estudo. Um cartão que continua em etapas curtas volta depois de
 * [REQUEUE_GAP] outros cartões (ou no fim, se sobrarem menos).
 */
export class StudySession {
  constructor(cards, options) {
    this.clock = options.clock || Date.now
    this.newId = options.newId || (() => uuid())
    this.config = options.config || DEFAULT_CONFIG
    this.deckId = options.deckId
    this.sessionId = options.sessionId || uuid()
    this.requeueGap = options.requeueGap === undefined ? REQUEUE_GAP : options.requeueGap

    const seen = {}
    this.queue = []
    for (const card of cards) {
      if (seen[card.i]) continue
      seen[card.i] = true
      this.queue.push(card)
    }
    this.total = this.queue.length
    this.studied = {}
    this.studiedCount = 0
    this.startedAt = this.clock()
    this.shownAt = this.startedAt
    this.finishedAt = this.queue.length === 0 ? this.startedAt : null
    this.counts = { answers: 0, correct: 0, hard: 0, again: 0 }
  }

  get current() {
    return this.queue.length > 0 ? this.queue[0] : null
  }

  get isFinished() {
    return this.queue.length === 0
  }

  /** Número do cartão atual para "12 / 30"; um cartão que volta mantém o número. */
  get position() {
    const card = this.current
    if (!card) return this.total
    return Math.min(this.studiedCount + (this.studied[card.i] ? 0 : 1), this.total)
  }

  /** @returns { review, card, requeued } — review já no formato da outbox/protocolo. */
  answer(rating) {
    const card = this.queue.shift()
    if (!card) throw new Error('A sessão já terminou.')
    const now = this.clock()
    const before = schedulingOf(card)
    const after = schedule(before, rating, now, this.config)
    const updated = withScheduling(card, after)

    const review = {
      id: this.newId(),
      c: card.i,
      k: this.deckId,
      r: RATINGS.indexOf(rating) + 1,
      t: now,
      ms: Math.max(0, now - this.shownAt),
      sid: this.sessionId,
      ps: STATES.indexOf(before.state),
      ns: STATES.indexOf(after.state),
      pi: reviewInterval(before),
      ni: reviewInterval(after),
      a: ALGORITHM_ID,
    }

    const requeued = after.state === 'LEARNING' || after.state === 'RELEARNING'
    if (requeued) this.queue.splice(Math.min(this.requeueGap, this.queue.length), 0, updated)

    if (!this.studied[card.i]) {
      this.studied[card.i] = true
      this.studiedCount++
    }
    this.counts.answers++
    if (rating === 'AGAIN') this.counts.again++
    else if (rating === 'HARD') this.counts.hard++
    else this.counts.correct++

    this.shownAt = now
    if (this.queue.length === 0) this.finishedAt = now
    return { review, card: updated, requeued }
  }

  summary() {
    return {
      cards: this.studiedCount,
      answers: this.counts.answers,
      correct: this.counts.correct,
      hard: this.counts.hard,
      again: this.counts.again,
      durationMs: (this.finishedAt === null ? this.clock() : this.finishedAt) - this.startedAt,
    }
  }
}
