import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createStore, DEFAULT_SETTINGS } from '../lib/store.js'
import { memoryBackend, card } from './helpers.js'

const cardChange = (id, deck, extra = {}) =>
  Object.assign({ t: 'c', id, k: deck, f: 'f ' + id, b: 'b ' + id, s: 0, d: 1, iv: 0, ef: 2.5, r: 0, l: 0, ls: 0, lr: 0 }, extra)

test('id do relógio é criado uma vez e fica igual', () => {
  const backend = memoryBackend()
  const id = createStore(backend.open).deviceId()
  assert.match(id, /^watch-[0-9a-f-]{36}$/)
  assert.equal(createStore(backend.open).deviceId(), id)
})

test('valores padrão num relógio novo', () => {
  const store = createStore(memoryBackend().open)
  assert.deepEqual(store.decks(), [])
  assert.equal(store.cursor(), 0)
  assert.deepEqual(store.settings(), DEFAULT_SETTINGS)
  assert.deepEqual(store.outbox(), [])
})

test('mudanças criam decks em ordem alfabética com contagem de cartões', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([
    { t: 'd', id: 'b', n: 'Programação' },
    { t: 'd', id: 'a', n: 'Inglês' },
    cardChange('c1', 'a'),
    cardChange('c2', 'a'),
    cardChange('c3', 'b'),
  ])
  assert.deepEqual(store.decks(), [
    { id: 'a', name: 'Inglês', count: 2 },
    { id: 'b', name: 'Programação', count: 1 },
  ])
  assert.deepEqual(store.loadCards('a').map((c) => c.i), ['c1', 'c2'])
})

test('atualização e remoção de cartões', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([{ t: 'd', id: 'a', n: 'Inglês' }, cardChange('c1', 'a'), cardChange('c2', 'a')])
  store.applyChanges([cardChange('c1', 'a', { f: 'nova frente', s: 2, iv: 4 }), { t: 'c', id: 'c2', k: 'a', del: true }])
  const cards = store.loadCards('a')
  assert.equal(cards.length, 1)
  assert.equal(cards[0].f, 'nova frente')
  assert.equal(cards[0].iv, 4)
  assert.equal(store.decks()[0].count, 1)
})

test('deck removido some junto com os cartões; cartões de deck desconhecido são ignorados', () => {
  const backend = memoryBackend()
  const store = createStore(backend.open)
  store.applyChanges([{ t: 'd', id: 'a', n: 'Inglês' }, cardChange('c1', 'a')])
  store.applyChanges([{ t: 'd', id: 'a', del: true }, cardChange('c2', 'a'), cardChange('x', 'fantasma')])
  assert.deepEqual(store.decks(), [])
  assert.deepEqual(store.loadCards('a'), [])
  assert.deepEqual(store.loadCards('fantasma'), [])
})

test('renomear deck mantém a contagem', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([{ t: 'd', id: 'a', n: 'Ingles' }, cardChange('c1', 'a')])
  store.applyChanges([{ t: 'd', id: 'a', n: 'Inglês' }])
  assert.deepEqual(store.decks(), [{ id: 'a', name: 'Inglês', count: 1 }])
})

test('configurações do celular são guardadas com os padrões preenchidos', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([], { newCardsPerDay: 10, scheduler: { easyBonus: 1.5 } })
  const settings = store.settings()
  assert.equal(settings.newCardsPerDay, 10)
  assert.equal(settings.maxReviewsPerDay, 200)
  assert.equal(settings.scheduler.easyBonus, 1.5)
  assert.deepEqual(settings.scheduler.learningStepsMinutes, [1, 10])
})

test('outbox guarda e remove só as respostas confirmadas', () => {
  const store = createStore(memoryBackend().open)
  store.addToOutbox({ id: 'r1' })
  store.addToOutbox({ id: 'r2' })
  store.addToOutbox({ id: 'r3' })
  store.removeFromOutbox(['r1', 'r3'])
  assert.deepEqual(store.outbox(), [{ id: 'r2' }])
})

test('contadores do dia zeram quando o dia muda', () => {
  const store = createStore(memoryBackend().open)
  store.recordToday('a', 100, 0) // novo
  store.recordToday('a', 100, 2) // revisão
  store.recordToday('a', 100, 1) // etapa curta: não conta
  assert.deepEqual(store.todayCounts('a', 100), { newIntroduced: 1, reviewsDone: 1 })
  assert.deepEqual(store.todayCounts('b', 100), { newIntroduced: 0, reviewsDone: 0 })
  store.recordToday('a', 200, 0)
  assert.deepEqual(store.todayCounts('a', 200), { newIntroduced: 1, reviewsDone: 0 })
})

test('saveCards grava o deck e atualiza a contagem', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([{ t: 'd', id: 'a', n: 'Inglês' }])
  store.saveCards('a', [card('c1'), card('c2'), card('c3')])
  assert.equal(store.decks()[0].count, 3)
  assert.equal(store.loadCards('a').length, 3)
})
