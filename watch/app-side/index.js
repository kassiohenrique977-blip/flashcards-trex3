import { BaseSideService, settingsLib } from '@zeppos/zml/base-side'
import { createPhoneClient } from './phone-client.js'

// Side service: roda dentro do Zepp App no celular. Recebe os pedidos do relógio
// (BLE, via ZML) e os repassa ao app Android Flashcards por HTTP em 127.0.0.1.

AppSideService(
  BaseSideService({
    onInit() {
      this.phone = createPhoneClient({
        fetch: (options) => this.fetch(options),
        getSetting: (key) => settingsLib.getItem(key),
      })
    },

    onRequest(req, res) {
      const handler = this.phone && this.phone[req.method]
      if (!handler) {
        res({ code: 'UNKNOWN_METHOD', message: 'Método desconhecido: ' + req.method })
        return
      }
      handler(req.params || {}).then(
        (data) => res(null, data),
        (error) => res(error),
      )
    },

    onRun() {},

    onDestroy() {},
  }),
)
