import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { schedule, reviewInterval, DEFAULT_CONFIG, ALGORITHM_ID } from '../lib/srs.js'

// Os mesmos vetores usados pelos testes Kotlin do celular.
const vectors = JSON.parse(readFileSync(new URL('../../shared/srs-test-vectors.json', import.meta.url), 'utf8'))

test('a configuração padrão é a dos vetores', () => {
  assert.deepEqual({ ...DEFAULT_CONFIG }, vectors.config)
})

test('o id do algoritmo é o dos vetores', () => {
  assert.equal(ALGORITHM_ID, vectors.algorithm)
})

for (const vector of vectors.cases) {
  test('vetor ' + vector.name, () => {
    const actual = schedule(vector.before, vector.rating, vector.now, vectors.config)
    assert.deepEqual(actual, { ...vector.after, lastReviewedAt: vector.now })
  })
}

test('o estado original não é alterado', () => {
  const before = { state: 'REVIEW', dueAt: 0, intervalDays: 10, easeFactor: 2.5, repetitions: 3, lapses: 0, learningStep: 0 }
  const snapshot = { ...before }
  schedule(before, 'AGAIN', 1000)
  assert.deepEqual(before, snapshot)
})

test('respostas em revisão ficam em ordem difícil < bom < fácil', () => {
  for (const interval of [1, 3, 15, 120]) {
    for (const ease of [1.3, 2.5, 3.1]) {
      const state = { state: 'REVIEW', dueAt: 0, intervalDays: interval, easeFactor: ease, repetitions: 2, lapses: 0, learningStep: 0 }
      const [hard, good, easy] = ['HARD', 'GOOD', 'EASY'].map((r) => schedule(state, r, 0).intervalDays)
      assert.ok(interval < hard && hard < good && good < easy, `iv=${interval} ease=${ease}: ${hard} ${good} ${easy}`)
    }
  }
})

test('intervalo de revisão é zero nas etapas curtas', () => {
  assert.equal(reviewInterval({ state: 'RELEARNING', intervalDays: 3 }), 0)
  assert.equal(reviewInterval({ state: 'REVIEW', intervalDays: 3 }), 3)
})

test('resposta desconhecida é recusada', () => {
  assert.throws(() => schedule({ state: 'NEW', dueAt: 0, intervalDays: 0, easeFactor: 2.5, repetitions: 0, lapses: 0, learningStep: 0 }, 'MAYBE', 0))
})
