import { test } from 'node:test'
import assert from 'node:assert/strict'
import { TUTORIAL_STEPS, QR_STEP, isLastStep, nextStep } from '../lib/onboarding.js'
import { APP_DOWNLOAD_LABEL, APP_DOWNLOAD_URL } from '../lib/config.js'
import { createStore } from '../lib/store.js'
import { memoryBackend } from './helpers.js'

test('todo passo tem título e texto', () => {
  assert.ok(TUTORIAL_STEPS.length >= 3)
  for (const step of TUTORIAL_STEPS) {
    assert.ok(step.title.length > 0, JSON.stringify(step))
    assert.ok(step.text.length > 0, step.title)
    assert.ok(['text', 'qr', 'confirm'].includes(step.kind), step.kind)
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
