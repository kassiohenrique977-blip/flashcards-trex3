import { test } from 'node:test'
import assert from 'node:assert/strict'
import { TUTORIAL_STEPS, QR_STEP, isLastStep, nextStep } from '../lib/onboarding.js'
import { APP_DOWNLOAD_LABEL, APP_DOWNLOAD_URL } from '../lib/config.js'
import { createStore } from '../lib/store.js'
import { ENGLISH, englishText } from '../lib/strings.js'
import { PORTUGUES } from '../scripts/strings-pt.mjs'
import { memoryBackend } from './helpers.js'

test('todo passo tem título e texto traduzidos', () => {
  assert.ok(TUTORIAL_STEPS.length >= 3)
  for (const step of TUTORIAL_STEPS) {
    assert.ok(['text', 'qr', 'confirm'].includes(step.kind), step.kind)
    for (const chave of [step.title, step.text]) {
      assert.ok(ENGLISH[chave], 'falta ' + chave + ' em inglês')
      assert.ok(PORTUGUES[chave], 'falta ' + chave + ' em português')
      assert.notEqual(englishText(chave), chave)
    }
  }
})

test('os botões do tutorial também são traduzidos', () => {
  for (const chave of ['tour.next', 'tour.yes', 'tour.notYet']) {
    assert.ok(ENGLISH[chave], chave)
    assert.ok(PORTUGUES[chave], chave)
  }
})

test('o tutorial termina na pergunta de confirmação, depois do QR code', () => {
  assert.equal(TUTORIAL_STEPS[TUTORIAL_STEPS.length - 1].kind, 'confirm')
  assert.ok(QR_STEP > 0)
  assert.ok(QR_STEP < TUTORIAL_STEPS.length - 1)
  assert.equal(TUTORIAL_STEPS.filter((s) => s.kind === 'confirm').length, 1)
})

test('avançar para no último passo', () => {
  assert.equal(nextStep(0), 1)
  assert.equal(isLastStep(0), false)
  const last = TUTORIAL_STEPS.length - 1
  assert.equal(nextStep(last), last)
  assert.equal(isLastStep(last), true)
})

test('o endereço mostrado na tela é a URL sem o protocolo', () => {
  assert.ok(APP_DOWNLOAD_URL.startsWith('https://'))
  assert.equal(APP_DOWNLOAD_LABEL, APP_DOWNLOAD_URL.slice('https://'.length))
})

test('o tutorial só é marcado como visto depois da confirmação', () => {
  const backend = memoryBackend()
  const store = createStore(backend.open)

  assert.equal(store.isOnboarded(), false)
  store.setOnboarded(true)

  assert.equal(store.isOnboarded(), true)
  assert.equal(createStore(backend.open).isOnboarded(), true)
})
