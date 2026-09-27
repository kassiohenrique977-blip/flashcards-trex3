import { replace } from '@zos/router'
import { onKey, offKey, KEY_UP, KEY_DOWN, KEY_SELECT, KEY_EVENT_CLICK } from '@zos/interaction'
import { setPageBrightTime } from '@zos/display'
import { createDeviceStore } from '../../lib/device-store.js'
import { loadDeckForStudy, StudySession } from '../../lib/study.js'
import { isDemoDeck } from '../../lib/demo-deck.js'
import { parseParams } from '../../lib/format.js'
import { uuid } from '../../lib/ids.js'
import { t } from '../text.js'
import { COLOR, label, button, scrollingText, setText, removeAll } from '../ui.js'

// Frente: "12 / 30" · pergunta · [MOSTRAR] (ou SELECT).
// Verso: resposta · [ERREI] [DIFÍCIL] [BOM] [FÁCIL].
// Botões físicos no verso: SELECT = BOM, UP = FÁCIL, DOWN = ERREI. BACK não é interceptado.

const SAVE_EVERY = 10
const SCREEN_ON_MS = 30000

Page({
  state: {},

  onInit(params) {
    this.state.deckId = parseParams(params).deckId
  },

  build() {
    setPageBrightTime({ brightTime: SCREEN_ON_MS })
    const state = this.state
    const store = createDeviceStore()
    const data = loadDeckForStudy(store, state.deckId, Date.now())

    state.store = store
    // O deck de demonstração é só do relógio: nada dele vai para o celular.
    state.local = isDemoDeck(state.deckId)
    state.cards = data.cards
    state.indexById = {}
    data.cards.forEach((card, i) => (state.indexById[card.i] = i))
    state.day = data.day
    // Respostas reaplicadas da outbox ainda não estavam gravadas no deck.
    state.dirty = data.changed ? 1 : 0
    state.revealed = false
    state.finished = false
    state.widgets = []
    state.session = new StudySession(data.queue, {
      clock: Date.now,
      newId: () => uuid(),
      config: data.config,
      deckId: state.deckId,
    })

    state.counter = label({ x: 140, y: 24, w: 200, h: 40, size: 28, color: COLOR.muted })
    onKey({ callback: (key, event) => this.onKeyPress(key, event) })
    this.render()
  },

  onDestroy() {
    this.save()
    offKey()
  },

  render() {
    const state = this.state
    removeAll(state.widgets)
    const card = state.session.current
    if (!card) {
      this.finish()
      return
    }
    setText(state.counter, state.session.position + ' / ' + state.session.total)
    const add = (w) => state.widgets.push(w)

    if (!state.revealed) {
      add(scrollingText({ x: 70, y: 72, w: 340, h: 270, text: card.f, size: 36 }))
      add(button({ x: 90, y: 355, w: 300, h: 80, text: t('study.show'), size: 34, color: COLOR.primary, onClick: () => this.reveal() }))
      return
    }

    add(scrollingText({ x: 70, y: 72, w: 340, h: 188, text: card.b, size: 36, color: COLOR.accent }))
    add(button({ x: 88, y: 270, w: 148, h: 72, text: t('study.again'), size: 28, color: COLOR.again, onClick: () => this.answer('AGAIN') }))
    add(button({ x: 244, y: 270, w: 148, h: 72, text: t('study.hard'), size: 28, color: COLOR.hard, textColor: COLOR.dark, onClick: () => this.answer('HARD') }))
    add(button({ x: 88, y: 350, w: 148, h: 72, text: t('study.good'), size: 28, color: COLOR.good, onClick: () => this.answer('GOOD') }))
    add(button({ x: 244, y: 350, w: 148, h: 72, text: t('study.easy'), size: 28, color: COLOR.easy, onClick: () => this.answer('EASY') }))
  },

  reveal() {
    if (this.state.revealed || this.state.finished) return
    this.state.revealed = true
    this.render()
  },

  answer(rating) {
    const state = this.state
    if (!state.revealed || state.finished || state.session.isFinished) return
    const result = state.session.answer(rating)
    // A resposta vai para a outbox na hora; o deck é gravado em lotes.
    if (!state.local) state.store.addToOutbox(result.review)
    state.store.recordToday(state.deckId, state.day.start, result.review.ps)
    const index = state.indexById[result.card.i]
    if (index !== undefined) state.cards[index] = result.card
    state.dirty++
    if (state.dirty >= SAVE_EVERY) this.save()
    state.revealed = false
    this.render()
  },

  save() {
    const state = this.state
    if (!state.store || !state.dirty) return
    state.store.saveCards(state.deckId, state.cards)
    state.dirty = 0
  },

  finish() {
    const state = this.state
    if (state.finished) return
    state.finished = true
    this.save()
    replace({ url: 'page/summary/index.page', params: state.session.summary() })
  },

  onKeyPress(key, event) {
    const state = this.state
    if (event !== KEY_EVENT_CLICK || state.finished) return false
    if (!state.revealed) {
      if (key === KEY_SELECT) {
        this.reveal()
        return true
      }
      return false
    }
    if (key === KEY_SELECT) {
      this.answer('GOOD')
      return true
    }
    if (key === KEY_UP) {
      this.answer('EASY')
      return true
    }
    if (key === KEY_DOWN) {
      this.answer('AGAIN')
      return true
    }
    return false
  },
})
