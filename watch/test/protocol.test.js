import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { StudySession } from '../lib/study.js'
import { createStore } from '../lib/store.js'
import { confirmedIds } from '../lib/sync.js'
import { memoryBackend, card } from './helpers.js'

// Mesmo arquivo usado pelos testes Kotlin (ProtocolExamplesTest): se um lado mudar o
// formato, os dois lados quebram.
const examples = JSON.parse(readFileSync(new URL('../../shared/protocol-examples.json', import.meta.url), 'utf8'))

test('a resposta gerada no relógio tem exatamente os campos do protocolo', () => {
  const session = new StudySession([card('card-1')], { deckId: 'deck-1', clock: () => 1 })
  const { review } = session.answer('GOOD')
  const expected = examples.push.request.reviews[0]
  assert.deepEqual(Object.keys(review).sort(), Object.keys(expected).sort())
  for (const key of Object.keys(expected)) assert.equal(typeof review[key], typeof expected[key], key)
})

test('o relógio aplica a página de mudanças do exemplo', () => {
  const store = createStore(memoryBackend().open)
  store.applyChanges([{ t: 'd', id: 'deck-2', n: 'Velho' }])
  const page = examples.changes.response

  store.applyChanges(page.changes, page.settings)

  assert.deepEqual(store.decks(), [{ id: 'deck-1', name: 'Inglês', count: 2 }])
  const saved = store.loadCards('deck-1').find((c) => c.i === 'card-1')
  assert.equal(saved.f, 'What does "although" mean?')
  assert.equal(saved.iv, 4)
  assert.equal(saved.lr, 1700000004000)
  assert.equal(store.settings().scheduler.maximumIntervalDays, 36500)
})

test('o relógio entende a resposta do envio do exemplo', () => {
  const response = examples.push.response
  const batch = [{ id: response.accepted[0] }, { id: response.rejected[0].id }, { id: 'outra' }]
  assert.deepEqual(confirmedIds(batch, response), [response.accepted[0], response.rejected[0].id])
})
