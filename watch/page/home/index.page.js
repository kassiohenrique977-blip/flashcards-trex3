import { push } from '@zos/router'
import {
  onKey,
  offKey,
  onGesture,
  offGesture,
  KEY_UP,
  KEY_DOWN,
  KEY_SELECT,
  KEY_EVENT_CLICK,
  GESTURE_UP,
  GESTURE_DOWN,
} from '@zos/interaction'
import { createDeviceStore } from '../../lib/device-store.js'
import { loadDeckForStudy } from '../../lib/study.js'
import { cardCountLabel } from '../../lib/format.js'
import { COLOR, label, button, setText, removeAll } from '../ui.js'

// Tela inicial: "FLASHCARDS" · "Baralho: Inglês" · "12 cartões" · [COMEÇAR].
// UP/DOWN ou deslizar para cima/baixo troca de baralho; SELECT começa.

Page({
  state: {
    store: null,
    decks: [],
    index: 0,
    due: 0,
    ready: false,
    status: null,
    widgets: [],
  },

  build() {
    label({ x: 90, y: 40, w: 300, h: 50, text: 'FLASHCARDS', size: 30, color: COLOR.accent })
    this.state.status = label({ x: 60, y: 200, w: 360, h: 60, text: 'Preparando...', size: 32, color: COLOR.muted })
    onKey({ callback: (key, event) => this.onKeyPress(key, event) })
    onGesture({ callback: (event) => this.onSwipe(event) })
    // Deixa o "Preparando..." aparecer antes de ler os arquivos.
    setTimeout(() => this.refresh(), 20)
  },

  onResume() {
    if (this.state.ready) this.refresh()
  },

  onDestroy() {
    offKey()
    offGesture()
  },

  refresh() {
    const store = createDeviceStore()
    const decks = store.decks()
    const selected = store.selectedDeckId()
    const index = decks.findIndex((deck) => deck.id === selected)
    this.state.store = store
    this.state.decks = decks
    this.state.index = index >= 0 ? index : 0
    this.state.ready = true
    this.render()
  },

  render() {
    const state = this.state
    removeAll(state.widgets)
    setText(state.status, '')
    const add = (w) => state.widgets.push(w)

    if (state.decks.length === 0) {
      state.due = 0
      add(label({ x: 60, y: 120, w: 360, h: 150, text: 'Nenhum baralho.\nSincronize com o celular.', size: 32, wrap: true }))
      add(button({ x: 90, y: 300, w: 300, h: 80, text: 'SINCRONIZAR', size: 30, color: COLOR.primary, onClick: () => this.openSync() }))
      return
    }

    const deck = state.decks[state.index]
    const study = loadDeckForStudy(state.store, deck.id, Date.now())
    state.due = study.queue.length

    add(label({ x: 50, y: 100, w: 380, h: 80, text: 'Baralho: ' + deck.name, size: 34, wrap: true }))
    add(label({ x: 60, y: 185, w: 360, h: 60, text: cardCountLabel(state.due), size: 40, color: COLOR.accent }))
    if (state.due > 0) {
      add(button({ x: 90, y: 260, w: 300, h: 90, text: 'COMEÇAR', size: 36, color: COLOR.good, onClick: () => this.start() }))
    } else {
      add(label({ x: 60, y: 270, w: 360, h: 70, text: 'Tudo em dia!', size: 32, color: COLOR.good }))
    }
    if (state.decks.length > 1) {
      add(label({ x: 110, y: 352, w: 260, h: 36, text: state.index + 1 + ' / ' + state.decks.length + ' · deslize', size: 24, color: COLOR.muted }))
    }
    add(button({ x: 130, y: 392, w: 220, h: 56, text: 'SINCRONIZAR', size: 26, color: COLOR.neutral, onClick: () => this.openSync() }))
  },

  move(delta) {
    const total = this.state.decks.length
    if (total < 2) return
    this.state.index = (this.state.index + delta + total) % total
    this.state.store.selectDeck(this.state.decks[this.state.index].id)
    this.render()
  },

  start() {
    const deck = this.state.decks[this.state.index]
    if (!deck || this.state.due === 0) return
    push({ url: 'page/study/index.page', params: { deckId: deck.id } })
  },

  openSync() {
    push({ url: 'page/sync/index.page' })
  },

  onKeyPress(key, event) {
    if (event !== KEY_EVENT_CLICK || !this.state.ready) return false
    if (key === KEY_SELECT && this.state.due > 0) {
      this.start()
      return true
    }
    if (this.state.decks.length < 2) return false
    if (key === KEY_UP) {
      this.move(-1)
      return true
    }
    if (key === KEY_DOWN) {
      this.move(1)
      return true
    }
    return false
  },

  onSwipe(event) {
    if (this.state.decks.length < 2) return false
    if (event === GESTURE_UP) {
      this.move(1)
      return true
    }
    if (event === GESTURE_DOWN) {
      this.move(-1)
      return true
    }
    return false
  },
})
