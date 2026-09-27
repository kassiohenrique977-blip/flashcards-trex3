import { BasePage } from '@zeppos/zml/base-page'
import { back } from '@zos/router'
import { setPageBrightTime } from '@zos/display'
import { createDeviceStore } from '../../lib/device-store.js'
import { runSync, syncErrorKey } from '../../lib/sync.js'
import { countLabel } from '../../lib/format.js'
import { t } from '../text.js'
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
      label({ x: 90, y: 50, w: 300, h: 50, text: t('sync.title'), size: 30, color: COLOR.accent })
      this.state.status = label({ x: 50, y: 120, w: 380, h: 110, text: '', size: 32, wrap: true })
      this.state.detail = label({ x: 60, y: 235, w: 360, h: 110, text: '', size: 26, color: COLOR.muted, wrap: true })
      this.state.buttons = []
      this.startSync()
    },

    startSync() {
      const state = this.state
      removeAll(state.buttons)
      setText(state.status, t('sync.connecting'))
      setText(state.detail, '')
      runSync({
        store: createDeviceStore(),
        transport: {
          request: (method, params) => this.request({ method, params }, { timeout: REQUEST_TIMEOUT_MS }),
        },
        onProgress: (progress) => this.showProgress(progress),
      })
        .then((result) => {
          setText(state.status, t('sync.done'))
          setText(
            state.detail,
            t('sync.sent') + countLabel(result.pushed, t('answer.one'), t('answer.many')) +
              '\n' + t('sync.received') + countLabel(result.received, t('change.one'), t('change.many')),
          )
          this.showButtons(false)
        })
        .catch((error) => {
          setText(state.status, t('sync.failed'))
          setText(state.detail, t(syncErrorKey(error)))
          this.showButtons(true)
        })
    },

    showProgress(progress) {
      const state = this.state
      if (progress.step === 'connect') setText(state.status, t('sync.connecting'))
      if (progress.step === 'push') setText(state.status, t('sync.sending') + ' ' + progress.done + '/' + progress.total)
      if (progress.step === 'pull') setText(state.status, t('sync.receiving') + ' ' + progress.done)
      if (progress.step === 'finish') setText(state.status, t('sync.finishing'))
    },

    showButtons(withRetry) {
      const buttons = this.state.buttons
      if (withRetry) {
        buttons.push(button({ x: 80, y: 360, w: 155, h: 64, text: t('sync.retry'), size: 28, color: COLOR.primary, onClick: () => this.startSync() }))
        buttons.push(button({ x: 245, y: 360, w: 155, h: 64, text: t('sync.back'), size: 28, color: COLOR.neutral, onClick: () => back() }))
      } else {
        buttons.push(button({ x: 140, y: 360, w: 200, h: 64, text: t('sync.ok'), size: 32, color: COLOR.good, onClick: () => back() }))
      }
    },
  }),
)
