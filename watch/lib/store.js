import { cardFromChange } from './codec.js'
import { DEFAULT_CONFIG } from './srs.js'
import { uuid } from './ids.js'

// Tudo o que o relógio guarda. Arquivos (cada um é um LocalStorage próprio, carregado
// na memória só quando usado):
//   fc_meta.json      id do relógio, lista de decks, configurações, cursor, contadores do dia
//   fc_outbox.json    respostas ainda não confirmadas pelo celular
//   fc_deck_<id>.json cartões de um deck

const META = 'fc_meta.json'
const OUTBOX = 'fc_outbox.json'
const deckFile = (deckId) => 'fc_deck_' + deckId + '.json'

export const DEFAULT_SETTINGS = Object.freeze({
  newCardsPerDay: 20,
  maxReviewsPerDay: 200,
  dayCutoffHour: 4,
  scheduler: DEFAULT_CONFIG,
})

/**
 * @param open (nome) => { getItem(chave, padrão), setItem(chave, valor), removeItem(chave), clear() }
 *   No relógio é `new LocalStorage(nome)`; nos testes, um mapa em memória.
 */
export function createStore(open, options = {}) {
  const random = options.random || Math.random
  const files = {}
  const file = (name) => files[name] || (files[name] = open(name))
  const meta = () => file(META)
  const outboxFile = () => file(OUTBOX)

  function decks() {
    return meta().getItem('decks', [])
  }

  function saveDecks(list) {
    const sorted = list.slice().sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
    meta().setItem('decks', sorted)
  }

  function loadCards(deckId) {
    return file(deckFile(deckId)).getItem('cards', [])
  }

  function writeCards(deckId, cards, deckList) {
    file(deckFile(deckId)).setItem('cards', cards)
    const deck = deckList.find((d) => d.id === deckId)
    if (deck) deck.count = cards.length
  }

  function outbox() {
    return outboxFile().getItem('reviews', [])
  }

  return {
    /** ID estável do relógio, criado na primeira vez. */
    deviceId() {
      let id = meta().getItem('deviceId', null)
      if (!id) {
        id = 'watch-' + uuid(random)
        meta().setItem('deviceId', id)
      }
      return id
    },

    decks,

    /** O tutorial de primeira abertura já foi concluído. */
    isOnboarded() {
      return meta().getItem('onboarded', false) === true
    },

    setOnboarded(value) {
      meta().setItem('onboarded', value === true)
    },

    selectedDeckId() {
      return meta().getItem('selectedDeck', null)
    },

    selectDeck(deckId) {
      meta().setItem('selectedDeck', deckId)
    },

    settings() {
      return meta().getItem('settings', DEFAULT_SETTINGS)
    },

    cursor() {
      return meta().getItem('cursor', 0)
    },

    setCursor(value) {
      meta().setItem('cursor', value)
    },

    lastSyncAt() {
      return meta().getItem('lastSyncAt', 0)
    },

    setLastSyncAt(value) {
      meta().setItem('lastSyncAt', value)
    },

    loadCards,

    saveCards(deckId, cards) {
      const list = decks()
      writeCards(deckId, cards, list)
      meta().setItem('decks', list)
    },

    outbox,

    addToOutbox(review) {
      const reviews = outbox()
      reviews.push(review)
      outboxFile().setItem('reviews', reviews)
    },

    /** Remove as respostas que o celular confirmou. */
    removeFromOutbox(ids) {
      const confirmed = {}
      ids.forEach((id) => (confirmed[id] = true))
      outboxFile().setItem('reviews', outbox().filter((r) => !confirmed[r.id]))
    },

    /** Contadores do dia de estudo que começa em [dayStart], usados nos limites diários. */
    todayCounts(deckId, dayStart) {
      const today = meta().getItem('today', null)
      const entry = today && today.start === dayStart && today.decks[deckId]
      return { newIntroduced: entry ? entry.n : 0, reviewsDone: entry ? entry.r : 0 }
    },

    /** Conta uma resposta no dia. [previousState] é o código do estado antes da resposta. */
    recordToday(deckId, dayStart, previousState) {
      let today = meta().getItem('today', null)
      if (!today || today.start !== dayStart) today = { start: dayStart, decks: {} }
      const entry = today.decks[deckId] || { n: 0, r: 0 }
      if (previousState === 0) entry.n++
      if (previousState === 2) entry.r++
      today.decks[deckId] = entry
      meta().setItem('today', today)
    },

    /**
     * Aplica uma página de mudanças vindas do celular. Cada deck alterado é lido
     * e gravado uma única vez por página.
     */
    applyChanges(changes, settings) {
      if (settings) meta().setItem('settings', normalizeSettings(settings))
      const list = decks()
      const touched = new Map()

      for (const change of changes) {
        if (change.t === 'd') {
          const index = list.findIndex((d) => d.id === change.id)
          if (change.del) {
            if (index >= 0) list.splice(index, 1)
            touched.delete(change.id)
            file(deckFile(change.id)).clear()
          } else if (index >= 0) {
            list[index] = Object.assign({}, list[index], { name: change.n })
          } else {
            list.push({ id: change.id, name: change.n, count: 0 })
          }
        } else if (change.t === 'c') {
          // Deck removido ou desligado do relógio: o cartão não interessa.
          if (!list.some((d) => d.id === change.k)) continue
          let cards = touched.get(change.k)
          if (!cards) {
            cards = new Map(loadCards(change.k).map((c) => [c.i, c]))
            touched.set(change.k, cards)
          }
          if (change.del) cards.delete(change.id)
          else cards.set(change.id, cardFromChange(change))
        }
      }

      touched.forEach((cards, deckId) => writeCards(deckId, Array.from(cards.values()), list))
      saveDecks(list)
    },
  }
}

function normalizeSettings(settings) {
  return {
    newCardsPerDay: numberOr(settings.newCardsPerDay, DEFAULT_SETTINGS.newCardsPerDay),
    maxReviewsPerDay: numberOr(settings.maxReviewsPerDay, DEFAULT_SETTINGS.maxReviewsPerDay),
    dayCutoffHour: numberOr(settings.dayCutoffHour, DEFAULT_SETTINGS.dayCutoffHour),
    scheduler: Object.assign({}, DEFAULT_CONFIG, settings.scheduler || {}),
  }
}

function numberOr(value, fallback) {
  return typeof value === 'number' && isFinite(value) ? value : fallback
}
