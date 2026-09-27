import { replace, back } from '@zos/router'
import { onKey, offKey, KEY_SELECT, KEY_EVENT_CLICK } from '@zos/interaction'
import { createWidget, widget } from '@zos/ui'
import { px } from '@zos/utils'
import { createDeviceStore } from '../../lib/device-store.js'
import { TUTORIAL_STEPS, QR_STEP, isLastStep, nextStep } from '../../lib/onboarding.js'
import { APP_DOWNLOAD_LABEL } from '../../lib/config.js'
import { parseParams } from '../../lib/format.js'
import { COLOR, label, button, removeAll } from '../ui.js'

// Primeira abertura: tutorial, QR code do app do celular e a pergunta de confirmação.
// Aberta de novo pelo botão "COMO USAR" da tela inicial (params.revisit).

Page({
  state: { index: 0, revisit: false, widgets: [] },

  onInit(params) {
    this.state.revisit = parseParams(params).revisit === true
  },

  build() {
    this.state.store = createDeviceStore()
    onKey({
      callback: (key, event) => {
        if (key === KEY_SELECT && event === KEY_EVENT_CLICK) {
          this.advance()
          return true
        }
        return false
      },
    })
    this.render()
  },

  onDestroy() {
    offKey()
  },

  render() {
    const state = this.state
    removeAll(state.widgets)
    const step = TUTORIAL_STEPS[state.index]
    const add = (w) => state.widgets.push(w)
    add(label({ x: 40, y: 28, w: 400, h: 44, text: step.title, size: 28, color: COLOR.accent }))

    if (step.kind === 'qr') {
      add(label({ x: 50, y: 74, w: 380, h: 40, text: step.text, size: 22, color: COLOR.muted, wrap: true }))
      add(createWidget(widget.IMG, { x: px(130), y: px(118), w: px(220), h: px(220), src: 'qr-app.png' }))
      add(label({ x: 40, y: 344, w: 400, h: 32, text: APP_DOWNLOAD_LABEL, size: 20, color: COLOR.muted }))
      add(button({ x: 130, y: 382, w: 220, h: 58, text: 'PRÓXIMO', size: 26, color: COLOR.primary, onClick: () => this.advance() }))
      return
    }

    if (step.kind === 'confirm') {
      add(label({ x: 50, y: 110, w: 380, h: 180, text: step.text, size: 32, wrap: true }))
      add(button({ x: 90, y: 300, w: 300, h: 70, text: 'SIM, COMEÇAR', size: 30, color: COLOR.good, onClick: () => this.finish() }))
      add(button({ x: 120, y: 384, w: 240, h: 56, text: 'AINDA NÃO', size: 24, color: COLOR.neutral, onClick: () => this.goTo(QR_STEP) }))
      return
    }

    add(label({ x: 50, y: 110, w: 380, h: 220, text: step.text, size: 30, wrap: true }))
    add(button({ x: 130, y: 346, w: 220, h: 58, text: 'PRÓXIMO', size: 26, color: COLOR.primary, onClick: () => this.advance() }))
    add(label({
      x: 180,
      y: 414,
      w: 120,
      h: 30,
      text: state.index + 1 + ' / ' + TUTORIAL_STEPS.length,
      size: 20,
      color: COLOR.muted,
    }))
  },

  goTo(index) {
    this.state.index = index
    this.render()
  },

  advance() {
    if (isLastStep(this.state.index)) return
    this.goTo(nextStep(this.state.index))
  },

  /** Só sai do tutorial por aqui: a confirmação é sempre a última tela. */
  finish() {
    if (this.state.revisit) {
      back()
      return
    }
    this.state.store.setOnboarded(true)
    replace({ url: 'page/home/index.page' })
  },
})
