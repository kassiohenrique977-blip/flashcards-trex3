import { back } from '@zos/router'
import { onKey, offKey, KEY_SELECT, KEY_EVENT_CLICK } from '@zos/interaction'
import { cardCountLabel, countLabel, formatDuration, parseParams } from '../../lib/format.js'
import { COLOR, label, button } from '../ui.js'

// "SESSÃO CONCLUÍDA" · "12 cartões" · "9 acertos" · "3 difíceis" · "Tempo: 8:32"

Page({
  state: {},

  onInit(params) {
    this.state.summary = parseParams(params)
  },

  build() {
    const s = this.state.summary
    label({ x: 60, y: 50, w: 360, h: 50, text: 'SESSÃO CONCLUÍDA', size: 30, color: COLOR.accent })
    label({ x: 60, y: 115, w: 360, h: 46, text: cardCountLabel(s.cards || 0), size: 32 })
    label({ x: 60, y: 165, w: 360, h: 46, text: countLabel(s.correct || 0, 'acerto', 'acertos'), size: 32, color: COLOR.good })
    label({ x: 60, y: 215, w: 360, h: 46, text: countLabel(s.hard || 0, 'difícil', 'difíceis'), size: 32, color: COLOR.hard })
    label({ x: 60, y: 265, w: 360, h: 46, text: countLabel(s.again || 0, 'erro', 'erros'), size: 32, color: COLOR.again })
    label({ x: 60, y: 315, w: 360, h: 46, text: 'Tempo: ' + formatDuration(s.durationMs || 0), size: 30, color: COLOR.muted })
    button({ x: 140, y: 375, w: 200, h: 64, text: 'OK', size: 32, color: COLOR.primary, onClick: () => back() })
    onKey({
      callback: (key, event) => {
        if (key === KEY_SELECT && event === KEY_EVENT_CLICK) {
          back()
          return true
        }
        return false
      },
    })
  },

  onDestroy() {
    offKey()
  },
})
