import { test } from 'node:test'
import assert from 'node:assert/strict'
import { runSync, confirmedIds, syncErrorKey, SyncError } from '../lib/sync.js'
import { englishText } from '../lib/strings.js'
import { createStore } from '../lib/store.js'
import { StudySession, loadDeckForStudy } from '../lib/study.js'
import { createFakePhone } from './fake-phone.js'
import { memoryBackend } from './helpers.js'

function setup() {
  const phone = createFakePhone()
  phone.addDeck('d1', 'Inglês')
  for (let n = 1; n <= 5; n++) phone.addCard('d1', 'c' + n, 'front ' + n, 'back ' + n)
  const backend = memoryBackend()
  const store = createStore(backend.open)
  return { phone, backend, store }
}

const sync = (phone, store, extra = {}) => runSync({ transport: phone.transport, store, now: () => 42, ...extra })

test('primeira sincronização baixa decks e cartões e confirma o cursor', async () => {
  const { phone, store } = setup()
  const result = await sync(phone, store, { pullPage: 2 })
  assert.deepEqual(result, { pushed: 0, received: 6 })
  assert.deepEqual(store.decks(), [{ id: 'd1', name: 'Inglês', count: 5 }])
  assert.equal(store.cursor(), 6)
  assert.equal(phone.ackedCursor, 6)
  assert.equal(store.lastSyncAt(), 42)
  assert.deepEqual(
    phone.requests.map((r) => r.method),
    ['sync.hello', 'sync.pull', 'sync.pull', 'sync.pull', 'sync.ack'],
  )
})

test('respostas offline são enviadas antes de receber os cartões recalculados', async () => {
  const { phone, store } = setup()
  await sync(phone, store)
  // Estudo offline no relógio.
  const study = loadDeckForStudy(store, 'd1', 1000)
  const session = new StudySession(study.queue, { deckId: 'd1', clock: () => 1000 })
  const answered = [session.answer('EASY'), session.answer('GOOD')]
  answered.forEach((a) => store.addToOutbox(a.review))
  phone.requests.length = 0

  const result = await sync(phone, store)

  assert.equal(result.pushed, 2)
  assert.deepEqual(store.outbox(), [])
  assert.deepEqual(phone.requests.map((r) => r.method), ['sync.hello', 'sync.push', 'sync.pull', 'sync.ack'])
  const pulledSince = phone.requests[2].params.since
  assert.equal(pulledSince, 6, 'só pede o que mudou depois do cursor')
  assert.equal(result.received, 2, 'recebe de volta só os 2 cartões recalculados')
  assert.equal(store.loadCards('d1').find((c) => c.i === answered[0].card.i).r, 1)
})

test('queda no meio do download continua da última página gravada', async () => {
  const { phone, store } = setup()
  phone.failWhen = (method, params, callIndex) => method === 'sync.pull' && callIndex === 2
  await assert.rejects(sync(phone, store, { pullPage: 2 }), (e) => e.code === 'NO_SERVER')
  assert.equal(store.cursor(), 2, 'a primeira página ficou gravada')
  assert.equal(phone.ackedCursor, null)

  phone.failWhen = null
  phone.requests.length = 0
  const result = await sync(phone, store, { pullPage: 2 })

  assert.equal(phone.requests[1].params.since, 2)
  assert.equal(result.received, 4)
  assert.equal(store.decks()[0].count, 5)
})

test('queda depois do envio não duplica respostas no celular', async () => {
  const { phone, store } = setup()
  await sync(phone, store)
  store.addToOutbox({ id: 'r1', c: 'c1', k: 'd1', r: 3, t: 1, ns: 1 })
  phone.failWhen = (method, params) => {
    if (method !== 'sync.push' || phone.reviews.size > 0) return false
    phone.reviews.set(params.reviews[0].id, params.reviews[0]) // o celular grava...
    return true // ...mas a resposta se perde no caminho de volta
  }
  await assert.rejects(sync(phone, store))
  assert.equal(store.outbox().length, 1)

  phone.failWhen = null
  const result = await sync(phone, store)
  assert.equal(result.pushed, 1)
  assert.deepEqual(store.outbox(), [])
  assert.equal(phone.reviews.size, 1)
})

test('respostas de cartões apagados no celular saem da outbox', async () => {
  const { phone, store } = setup()
  await sync(phone, store)
  phone.deleteCard('d1', 'c3')
  store.addToOutbox({ id: 'r1', c: 'c3', k: 'd1', r: 3, t: 1, ns: 1 })

  const result = await sync(phone, store)

  assert.equal(result.pushed, 1)
  assert.deepEqual(store.outbox(), [])
  assert.deepEqual(store.loadCards('d1').map((c) => c.i).sort(), ['c1', 'c2', 'c4', 'c5'])
})

test('deck apagado no celular sai do relógio', async () => {
  const { phone, store } = setup()
  await sync(phone, store)
  phone.deleteDeck('d1')
  await sync(phone, store)
  assert.deepEqual(store.decks(), [])
  assert.deepEqual(store.loadCards('d1'), [])
})

test('versão de protocolo diferente para tudo antes de mexer nos dados', async () => {
  const { phone, store } = setup()
  const transport = { request: async (method) => (method === 'sync.hello' ? { protocol: 99 } : assert.fail(method)) }
  await assert.rejects(runSync({ transport, store }), (e) => e instanceof SyncError && e.code === 'PROTOCOL')
  assert.deepEqual(store.decks(), [])
})

test('lote sem nenhuma confirmação interrompe o envio', async () => {
  const { store } = setup()
  store.addToOutbox({ id: 'r1' })
  const transport = {
    request: async (method) => (method === 'sync.hello' ? { protocol: 1 } : { accepted: [], duplicates: [], rejected: [] }),
  }
  await assert.rejects(runSync({ transport, store }), (e) => e.code === 'PUSH_REJECTED')
  assert.equal(store.outbox().length, 1)
})

test('página sem mudanças visíveis mas com hasMore continua baixando', async () => {
  const { store } = setup()
  const pages = [
    { changes: [], nextSince: 5, hasMore: true }, // só cartões de um deck fora do relógio
    { changes: [{ t: 'd', id: 'd9', n: 'Novo' }], nextSince: 6, hasMore: false },
  ]
  const transport = {
    request: async (method) => {
      if (method === 'sync.hello') return { protocol: 1 }
      if (method === 'sync.pull') return pages.shift()
      return { ok: true }
    },
  }
  const result = await runSync({ transport, store })
  assert.equal(result.received, 1)
  assert.equal(store.cursor(), 6)
  assert.deepEqual(store.decks().map((d) => d.id), ['d9'])
})

test('cursor que não anda com hasMore é erro, não laço infinito', async () => {
  const { store } = setup()
  const transport = {
    request: async (method) => (method === 'sync.hello' ? { protocol: 1 } : { changes: [], nextSince: 0, hasMore: true }),
  }
  await assert.rejects(runSync({ transport, store }), (e) => e.code === 'BAD_RESPONSE')
})

test('confirmedIds considera aceitas, repetidas e recusadas', () => {
  const batch = [{ id: 'a' }, { id: 'b' }, { id: 'c' }, { id: 'd' }]
  assert.deepEqual(confirmedIds(batch, { accepted: ['a'], duplicates: ['c'], rejected: [{ id: 'd' }] }), ['a', 'c', 'd'])
  assert.deepEqual(confirmedIds(batch, null), [])
})

test('cada erro tem um texto de tela, inclusive o erro desconhecido', () => {
  assert.equal(syncErrorKey({ code: 'UNAUTHORIZED' }), 'error.unauthorized')
  assert.equal(syncErrorKey({ code: 'NO_SERVER' }), 'error.noServer')
  assert.equal(syncErrorKey({ code: 'PROTOCOL' }), 'error.protocol')
  assert.equal(syncErrorKey({ code: 'PUSH_REJECTED' }), 'error.pushRejected')
  assert.equal(syncErrorKey({ code: 'BAD_RESPONSE' }), 'error.badResponse')
  assert.equal(syncErrorKey(new Error('timeout')), 'error.generic')
  // A chave sempre tem texto: nenhuma tela mostra "error.algo" para o usuário.
  for (const codigo of ['UNAUTHORIZED', 'NO_SERVER', 'PROTOCOL', 'PUSH_REJECTED', 'BAD_RESPONSE', 'QUALQUER']) {
    const texto = englishText(syncErrorKey({ code: codigo }))
    assert.ok(texto.indexOf('error.') !== 0, codigo)
  }
})
