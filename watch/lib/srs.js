// Algoritmo de repetição espaçada do relógio. É o mesmo do celular
// (android/core/.../Sm2Scheduler.kt): as contas seguem a mesma ordem e a ease é
// arredondada a duas casas, para que Kotlin e JS produzam resultados idênticos.
// Os dois são conferidos contra shared/srs-test-vectors.json.

export const ALGORITHM_ID = 'sm2-v1'
export const MINUTE_MS = 60000
export const DAY_MS = 86400000

/** Código da resposta no protocolo = índice + 1. */
export const RATINGS = ['AGAIN', 'HARD', 'GOOD', 'EASY']

/** Código do estado no protocolo = índice. */
export const STATES = ['NEW', 'LEARNING', 'REVIEW', 'RELEARNING']

export const DEFAULT_CONFIG = Object.freeze({
  learningStepsMinutes: [1, 10],
  relearningStepsMinutes: [10],
  graduatingIntervalDays: 1,
  easyIntervalDays: 4,
  startingEase: 2.5,
  minimumEase: 1.3,
  easyBonus: 1.3,
  hardMultiplier: 1.2,
  lapseIntervalMultiplier: 0,
  minimumLapseIntervalDays: 1,
  maximumIntervalDays: 36500,
})

const AGAIN_EASE_PENALTY = 0.2
const HARD_EASE_PENALTY = 0.15
const EASY_EASE_BONUS = 0.15

/**
 * @param current {state, dueAt, intervalDays, easeFactor, repetitions, lapses, learningStep}
 * @param rating 'AGAIN' | 'HARD' | 'GOOD' | 'EASY'
 * @returns novo estado (o original não é alterado)
 */
export function schedule(current, rating, now, config = DEFAULT_CONFIG) {
  let next
  switch (current.state) {
    case 'NEW':
      next = learning(copy(current, { easeFactor: config.startingEase, learningStep: 0, intervalDays: 0 }), rating, now, config)
      break
    case 'LEARNING':
      next = learning(current, rating, now, config)
      break
    case 'REVIEW':
      next = review(current, rating, now, config)
      break
    case 'RELEARNING':
      next = relearning(current, rating, now, config)
      break
    default:
      throw new Error('Estado desconhecido: ' + current.state)
  }
  next.lastReviewedAt = now
  return next
}

/** Intervalo em dias que conta como "intervalo de revisão" (zero nas etapas curtas). */
export function reviewInterval(state) {
  return state.state === 'REVIEW' ? state.intervalDays : 0
}

function learning(s, rating, now, config) {
  const steps = config.learningStepsMinutes
  const step = clampStep(s.learningStep, steps)
  switch (rating) {
    case 'AGAIN':
      return copy(s, { state: 'LEARNING', learningStep: 0, intervalDays: 0, dueAt: now + minutes(steps[0]) })
    case 'HARD':
      return copy(s, { state: 'LEARNING', learningStep: step, intervalDays: 0, dueAt: now + minutes(hardDelay(steps, step)) })
    case 'GOOD':
      if (step + 1 < steps.length) {
        return copy(s, { state: 'LEARNING', learningStep: step + 1, intervalDays: 0, dueAt: now + minutes(steps[step + 1]) })
      }
      return toReview(s, config.graduatingIntervalDays, s.easeFactor, now)
    case 'EASY':
      return toReview(s, config.easyIntervalDays, s.easeFactor, now)
    default:
      throw new Error('Resposta desconhecida: ' + rating)
  }
}

function review(s, rating, now, config) {
  const interval = Math.max(1, s.intervalDays)
  const ease = s.easeFactor
  const daysLate = Math.max(0, Math.floor((now - s.dueAt) / DAY_MS))

  const hardRaw = Math.max(interval + 1, Math.round(interval * config.hardMultiplier))
  const goodRaw = Math.max(hardRaw + 1, Math.round((interval + daysLate / 2) * ease))
  const easyRaw = Math.max(goodRaw + 1, Math.round((interval + daysLate) * ease * config.easyBonus))

  switch (rating) {
    case 'AGAIN':
      return lapse(s, interval, now, config)
    case 'HARD':
      return toReview(s, clampInterval(hardRaw, config), adjustEase(ease - HARD_EASE_PENALTY, config), now)
    case 'GOOD':
      return toReview(s, clampInterval(goodRaw, config), ease, now)
    case 'EASY':
      return toReview(s, clampInterval(easyRaw, config), adjustEase(ease + EASY_EASE_BONUS, config), now)
    default:
      throw new Error('Resposta desconhecida: ' + rating)
  }
}

function lapse(s, interval, now, config) {
  const lapseInterval = clampInterval(
    Math.max(config.minimumLapseIntervalDays, Math.round(interval * config.lapseIntervalMultiplier)),
    config,
  )
  const lapsed = copy(s, {
    easeFactor: adjustEase(s.easeFactor - AGAIN_EASE_PENALTY, config),
    lapses: s.lapses + 1,
    repetitions: 0,
    intervalDays: lapseInterval,
    learningStep: 0,
  })
  const steps = config.relearningStepsMinutes
  if (steps.length === 0) return copy(lapsed, { state: 'REVIEW', dueAt: now + days(lapseInterval) })
  return copy(lapsed, { state: 'RELEARNING', dueAt: now + minutes(steps[0]) })
}

function relearning(s, rating, now, config) {
  const steps = config.relearningStepsMinutes
  const interval = clampInterval(Math.max(1, s.intervalDays), config)
  // Sem etapas (a configuração mudou no meio do caminho): volta direto para revisão.
  if (steps.length === 0) return toReview(s, interval, s.easeFactor, now)

  const step = clampStep(s.learningStep, steps)
  switch (rating) {
    case 'AGAIN':
      return copy(s, { learningStep: 0, dueAt: now + minutes(steps[0]) })
    case 'HARD':
      return copy(s, { learningStep: step, dueAt: now + minutes(hardDelay(steps, step)) })
    case 'GOOD':
      if (step + 1 < steps.length) return copy(s, { learningStep: step + 1, dueAt: now + minutes(steps[step + 1]) })
      return toReview(s, interval, s.easeFactor, now)
    case 'EASY':
      return toReview(s, clampInterval(interval + 1, config), s.easeFactor, now)
    default:
      throw new Error('Resposta desconhecida: ' + rating)
  }
}

function toReview(s, intervalDays, easeFactor, now) {
  return copy(s, {
    state: 'REVIEW',
    learningStep: 0,
    intervalDays,
    easeFactor,
    repetitions: s.repetitions + 1,
    dueAt: now + days(intervalDays),
  })
}

/** DIFÍCIL na primeira etapa usa a média das duas primeiras; nas demais, repete a etapa. */
function hardDelay(steps, step) {
  return step === 0 && steps.length > 1 ? (steps[0] + steps[1]) / 2 : steps[step]
}

function clampStep(step, steps) {
  return Math.min(Math.max(step, 0), steps.length - 1)
}

function adjustEase(value, config) {
  return Math.max(config.minimumEase, Math.round(value * 100) / 100)
}

function clampInterval(value, config) {
  return Math.min(value, config.maximumIntervalDays)
}

function minutes(value) {
  return Math.round(value * MINUTE_MS)
}

function days(value) {
  return value * DAY_MS
}

function copy(s, changes) {
  return Object.assign({}, s, changes)
}
