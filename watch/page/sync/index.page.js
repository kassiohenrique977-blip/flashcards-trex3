import { BasePage } from '@zeppos/zml/base-page'
import { back } from '@zos/router'
import { setPageBrightTime } from '@zos/display'
import { createDeviceStore } from '../../lib/device-store.js'
import { runSync, syncErrorMessage } from '../../lib/sync.js'
import { countLabel } from '../../lib/format.js'
import { COLOR, label, button, setText, removeAll } from '../ui.js'

// Sincroniza só enquanto esta tela está aberta: nada roda em segundo plano no relógio.
// O relógio fala com o side service (no Zepp App) via BLE; o side service fala com o
// app Android via HTTP local.

const REQUEST_TIMEOUT_MS = 30000

Page(
  BasePage({
    state: {},

    build() {
      setPageBrightTime({ brightTime: 60000 })
      label({ x: 90, y: 50, w: 300, h: 50, text: 'SINCRONIZAR', size: 30, color: COLOR.accent })
      this.state.status = label({ x: 50, y: 120, w: 380, h: 110, text: '', size: 32, wrap: true })
      this.state.detail = label({ x: 60, y: 235, w: 360, h: 110, text: '', size: 26, color: COLOR.muted, wrap: true })
      this.state.buttons = []
      this.startSync()
    },

    startSync() {
      const state = this.state
      removeAll(state.buttons)
      setText(state.status, 'Conectando...')
      setText(state.detail, '')
      runSync({
        store: createDeviceStore(),
        transport: {
          request: (method, params) => this.request({ method, params }, { timeout: REQUEST_TIMEOUT_MS }),
        },
        onProgress: (progress) => this.showProgress(progress),
      })
        .then((result) => {
          setText(state.status, 'Sincronizado!')
          setText(
            state.detail,
            'Enviadas: ' + countLabel(result.pushed, 'resposta', 'respostas') +
              '\nRecebidas: ' + countLabel(result.received, 'alteração', 'alterações'),
          )
          this.showButtons(false)
        })
        .catch((error) => {
          setText(state.status, 'Não sincronizou')
          setText(state.detail, syncErrorMessage(error))
          this.showButtons(true)
        })
    },

    showProgress(progress) {
      const state = this.state
      if (progress.step === 'connect') setText(state.status, 'Conectando...')
      if (progress.step === 'push') setText(state.status, 'Enviando respostas ' + progress.done + '/' + progress.total)
      if (progress.step === 'pull') setText(state.status, 'Recebendo cartões... ' + progress.done)
      if (progress.step === 'finish') setText(state.status, 'Concluindo...')
    },

    showButtons(withRetry) {
      const buttons = this.state.buttons
      if (withRetry) {
        buttons.push(button({ x: 80, y: 360, w: 155, h: 64, text: 'TENTAR', size: 28, color: COLOR.primary, onClick: () => this.startSync() }))
        buttons.push(button({ x: 245, y: 360, w: 155, h: 64, text: 'VOLTAR', size: 28, color: COLOR.neutral, onClick: () => back() }))
      } else {
        buttons.push(button({ x: 140, y: 360, w: 200, h: 64, text: 'OK', size: 32, color: COLOR.good, onClick: () => back() }))
      }
    },
  }),
)
