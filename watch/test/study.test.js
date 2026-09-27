import { test } from 'node:test'
import assert from 'node:assert/strict'
import { planSession, StudySession, applyPending, studyDayWindow, loadDeckForStudy } from '../lib/study.js'
import { createStore } from '../lib/store.js'
import { memoryBackend, sequence, card } from './helpers.js'

const MIN = 60000
const DAY = 86400000
const now = 1700000000000
const dayEnd = now + 6 * 60 * MIN
const settings = { newCardsPerDay: 2, maxReviewsPerDay: 200, dayCutoffHour: 4 }

test('fila: etapas curtas, depois revisões, depois novos (igual ao Kotlin)', () => {
  const cards = [
    card('new-2', 0, 2),
    card('review-tomorrow', 2, dayEnd + 1),
    card('learning-later', 1, now + 60 * MIN),
    card('review-late', 2, now - 3 * DAY),
    card('new-1', 0, 1),
    card('relearning-due', 3, now - MIN),
    card('review-tonight', 2, now + 2 * 60 * MIN),
    card('learning-soon', 1, now + 5 * MIN),
    card('new-3', 0, 3),
  ]
  assert.deepEqual(
    planSession(cards, now, dayEnd, settings).map((c) => c.i),
    ['relearning-due', 'learning-soon', 'review-late', 'review-tonight', 'new-1', 'new-2'],
  )
  assert.deepEqual(
    planSession(cards, now, dayEnd, { ...settings, newCardsPerDay: 0, maxReviewsPerDay: 5 }, 0, 4).map((c) => c.i),
    ['relearning-due', 'learning-soon', 'review-late'],
  )
  assert.deepEqual(planSession(cards, now, dayEnd, settings, 1).filter((c) => c.s === 0).map((c) => c.i), ['new-1'])
})

function session(cards, clock = () => now) {
  return new StudySession(cards, { clock, newId: sequence('r'), deckId: 'deck', sessionId: 's1' })
}

test('cartão esquecido volta depois de três outros', () => {
  const s = session([1, 2, 3, 4, 5].map((n) => card('c' + n, 2, now)))
  const shown = [s.current.i]
  const positions = [s.position]
  s.answer('AGAIN')
  while (!s.isFinished) {
    shown.push(s.current.i)
    positions.push(s.position)
    s.answer('GOOD')
  }
  assert.deepEqual(shown, ['c1', 'c2', 'c3', 'c4', 'c1', 'c5'])
  assert.deepEqual(positions, [1, 2, 3, 4, 4, 5])
})

test('resumo conta acertos, difíceis, erros e para o tempo no fim', () => {
  let time = now
  const s = session([1, 2, 3, 4, 5].map((n) => card('c' + n, 2, now)), () => time)
  s.answer('AGAIN')
  time += 5000
  s.answer('HARD')
  time += 5000
  s.answer('GOOD')
  s.answer('EASY')
  s.answer('GOOD')
  time += 2000
  s.answer('GOOD')
  time += 60000
  assert.deepEqual(s.summary(), { cards: 5, answers: 6, correct: 4, hard: 1, again: 1, durationMs: 12000 })
})

test('a revisão gerada usa os códigos do protocolo', () => {
  let time = now
  const s = session([card('c1', 2, now)], () => time)
  time += 4000
  const { review, card: updated, requeued } = s.answer('GOOD')
  assert.deepEqual(review, {
    id: 'r-1', c: 'c1', k: 'deck', r: 3, t: now + 4000, ms: 4000, sid: 's1',
    ps: 2, ns: 2, pi: 5, ni: updated.iv, a: 'sm2-v1',
  })
  assert.equal(requeued, false)
  assert.equal(updated.lr, now + 4000)
})

test('sessão vazia termina na hora', () => {
  const s = session([])
  assert.equal(s.isFinished, true)
  assert.equal(s.current, null)
  assert.throws(() => s.answer('GOOD'))
})

test('respostas pendentes mais novas que o deck gravado são reaplicadas', () => {
  const cards = [card('c1', 0, 0), card('c2', 2, now, { lr: now + 10 })]
  const outbox = [
    { id: 'a', c: 'c1', k: 'deck', r: 4, t: now },
    { id: 'b', c: 'c2', k: 'deck', r: 1, t: now }, // mais velha que o gravado: ignora
    { id: 'c', c: 'c9', k: 'deck', r: 3, t: now }, // cartão que não existe mais
    { id: 'd', c: 'c1', k: 'outro', r: 1, t: now }, // outro deck
  ]
  const result = applyPending(cards, outbox, 'deck')
  assert.equal(result.changed, true)
  assert.equal(result.cards[0].s, 2) // FÁCIL gradua
  assert.equal(result.cards[0].iv, 4)
  assert.deepEqual(result.cards[1], cards[1])
})

test('dia de estudo vira às 4h no fuso local', () => {
  const previous = process.env.TZ
  process.env.TZ = 'America/Sao_Paulo'
  try {
    const at = (iso) => new Date(iso).getTime()
    assert.deepEqual(studyDayWindow(at('2026-09-14T03:00:00-03:00'), 4), {
      start: at('2026-09-13T04:00:00-03:00'),
      end: at('2026-09-14T04:00:00-03:00'),
    })
    assert.equal(studyDayWindow(at('2026-09-14T04:00:00-03:00'), 4).start, at('2026-09-14T04:00:00-03:00'))
    assert.equal(studyDayWindow(at('2026-09-14T23:59:00-03:00'), 4).start, at('2026-09-14T04:00:00-03:00'))
  } finally {
    process.env.TZ = previous
  }
})

test('loadDeckForStudy junta deck, outbox e limites do dia', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([{ t: 'd', id: 'deck', n: 'Inglês' }], { newCardsPerDay: 1, maxReviewsPerDay: 100, dayCutoffHour: 4 })
  store.saveCards('deck', [card('n1', 0, 1), card('n2', 0, 2)])
  const first = loadDeckForStudy(store, 'deck', now)
  assert.deepEqual(first.queue.map((c) => c.i), ['n1'])

  store.recordToday('deck', first.day.start, 0)
  assert.deepEqual(loadDeckForStudy(store, 'deck', now).queue.map((c) => c.i), [])
})
