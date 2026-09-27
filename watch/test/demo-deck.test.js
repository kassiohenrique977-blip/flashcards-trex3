import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DEMO_DECK_ID, DEMO_CARD_COUNT, ensureDemoDeck, isDemoDeck, demoChanges } from '../lib/demo-deck.js'
import { createStore } from '../lib/store.js'
import { loadDeckForStudy, StudySession } from '../lib/study.js'
import { englishText, ENGLISH, makeText } from '../lib/strings.js'
import { PORTUGUES } from '../scripts/strings-pt.mjs'
import { memoryBackend, sequence } from './helpers.js'

const AGORA = Date.parse('2026-09-27T12:00:00Z')
const portugues = makeText((chave) => PORTUGUES[chave])

function novoStore() {
  return createStore(memoryBackend().open)
}

test('a primeira abertura cria o deck de demonstração pronto para estudar', () => {
  const store = novoStore()
  assert.equal(ensureDemoDeck(store, englishText, AGORA), true)

  const decks = store.decks()
  assert.equal(decks.length, 1)
  assert.equal(decks[0].id, DEMO_DECK_ID)
  assert.equal(decks[0].name, ENGLISH['demo.name'])
  assert.equal(decks[0].count, DEMO_CARD_COUNT)

  const cards = store.loadCards(DEMO_DECK_ID)
  assert.equal(cards.length, DEMO_CARD_COUNT)
  for (const card of cards) {
    assert.equal(card.s, 0, 'todo cartão começa novo')
    assert.equal(card.d, AGORA)
    assert.ok(card.f.length > 0 && card.b.length > 0)
  }
  assert.equal(cards[0].f, ENGLISH['demo.q1'])
  assert.equal(cards[0].b, ENGLISH['demo.a1'])
})

test('o deck de demonstração nasce uma única vez', () => {
  const store = novoStore()
  ensureDemoDeck(store, englishText, AGORA)
  store.saveCards(DEMO_DECK_ID, store.loadCards(DEMO_DECK_ID).slice(0, 3))

  assert.equal(ensureDemoDeck(store, englishText, AGORA + 60000), false)
  assert.equal(store.loadCards(DEMO_DECK_ID).length, 3, 'não recria o que o usuário já estudou')
})

test('trocar o idioma do relógio troca os textos sem perder o agendamento', () => {
  const store = novoStore()
  ensureDemoDeck(store, englishText, AGORA)
  const estudados = store.loadCards(DEMO_DECK_ID).map((card, i) =>
    i === 0 ? Object.assign({}, card, { s: 2, d: AGORA + 86400000, iv: 1, r: 1, lr: AGORA }) : card,
  )
  store.saveCards(DEMO_DECK_ID, estudados)

  assert.equal(ensureDemoDeck(store, portugues, AGORA + 3600000), true)

  assert.equal(store.decks()[0].name, PORTUGUES['demo.name'])
  const cards = store.loadCards(DEMO_DECK_ID)
  assert.equal(cards.length, DEMO_CARD_COUNT)
  assert.equal(cards[0].f, PORTUGUES['demo.q1'])
  assert.equal(cards[0].b, PORTUGUES['demo.a1'])
  assert.equal(cards[0].s, 2, 'o estado do cartão fica de pé')
  assert.equal(cards[0].d, AGORA + 86400000)
  assert.equal(cards[0].iv, 1)
  assert.equal(cards[0].lr, AGORA)
})

test('sem troca de idioma, abrir a tela inicial não grava nada', () => {
  const backend = memoryBackend()
  const store = createStore(backend.open)
  ensureDemoDeck(store, englishText, AGORA)
  const antes = JSON.stringify(Array.from(backend.files.get('fc_deck_' + DEMO_DECK_ID + '.json')))

  assert.equal(ensureDemoDeck(store, englishText, AGORA + 1000), false)
  assert.equal(JSON.stringify(Array.from(backend.files.get('fc_deck_' + DEMO_DECK_ID + '.json'))), antes)
})

test('o deck de demonstração dá uma sessão de estudo completa', () => {
  const store = novoStore()
  ensureDemoDeck(store, englishText, AGORA)

  const data = loadDeckForStudy(store, DEMO_DECK_ID, AGORA)
  assert.equal(data.queue.length, DEMO_CARD_COUNT)

  const session = new StudySession(data.queue, {
    clock: () => AGORA,
    newId: sequence('rev'),
    config: data.config,
    deckId: DEMO_DECK_ID,
  })
  for (let i = 0; i < DEMO_CARD_COUNT; i++) session.answer('EASY')
  assert.equal(session.isFinished, true)
  assert.equal(session.summary().cards, DEMO_CARD_COUNT)
})

test('o deck de demonstração continua no relógio quando o celular manda os dele', () => {
  const store = novoStore()
  ensureDemoDeck(store, englishText, AGORA)

  store.applyChanges([
    { t: 'd', id: 'deck-do-celular', n: 'Inglês' },
    { t: 'c', id: 'c1', k: 'deck-do-celular', f: 'cat', b: 'gato', s: 0, d: AGORA, iv: 0, ef: 2.5, r: 0, l: 0, ls: 0, lr: 0 },
  ])

  const ids = store.decks().map((d) => d.id)
  assert.ok(ids.includes(DEMO_DECK_ID))
  assert.ok(ids.includes('deck-do-celular'))
  assert.equal(store.loadCards(DEMO_DECK_ID).length, DEMO_CARD_COUNT)
})

test('as perguntas e respostas da demonstração existem nos dois idiomas', () => {
  const changes = demoChanges(englishText, AGORA)
  assert.equal(changes.length, DEMO_CARD_COUNT + 1)
  for (let n = 1; n <= DEMO_CARD_COUNT; n++) {
    assert.ok(ENGLISH['demo.q' + n], 'falta demo.q' + n + ' em inglês')
    assert.ok(PORTUGUES['demo.a' + n], 'falta demo.a' + n + ' em português')
  }
  assert.equal(isDemoDeck(DEMO_DECK_ID), true)
  assert.equal(isDemoDeck('outro'), false)
})
