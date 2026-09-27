import { DEFAULT_CONFIG } from './srs.js'

// Deck de demonstração: nasce dentro do relógio na primeira abertura, para que o app
// funcione (e possa ser avaliado na loja) antes de existir qualquer sincronização.
// Ele é só do relógio: as respostas dele não vão para a outbox e o celular nunca o vê.
// Os textos vêm das chaves demo.* (lib/strings.js e page/i18n/*.po).

export const DEMO_DECK_ID = 'demo-local'
export const DEMO_CARD_COUNT = 8

export function isDemoDeck(deckId) {
  return deckId === DEMO_DECK_ID
}

const cardId = (n) => DEMO_DECK_ID + '-' + n

/** Número do cartão dentro do deck de demonstração, ou 0 se o id não for dele. */
function cardNumber(id) {
  const n = Number(String(id).slice(DEMO_DECK_ID.length + 1))
  return n >= 1 && n <= DEMO_CARD_COUNT ? n : 0
}

/**
 * O deck no formato de uma página de mudanças do protocolo, para entrar pelo mesmo
 * caminho dos decks que vêm do celular (store.applyChanges).
 */
export function demoChanges(t, now) {
  const changes = [{ t: 'd', id: DEMO_DECK_ID, n: t('demo.name') }]
  for (let n = 1; n <= DEMO_CARD_COUNT; n++) {
    changes.push({
      t: 'c',
      id: cardId(n),
      k: DEMO_DECK_ID,
      f: t('demo.q' + n),
      b: t('demo.a' + n),
      s: 0,
      d: now,
      iv: 0,
      ef: DEFAULT_CONFIG.startingEase,
      r: 0,
      l: 0,
      ls: 0,
      lr: 0,
    })
  }
  return changes
}

/**
 * Garante o deck de demonstração: cria na primeira abertura e, se o idioma do relógio
 * mudou depois disso, troca os textos sem perder o agendamento dos cartões.
 * @returns true se gravou alguma coisa.
 */
export function ensureDemoDeck(store, t, now) {
  if (!store.isDemoSeeded()) {
    store.applyChanges(demoChanges(t, now))
    store.setDemoSeeded(true)
    return true
  }
  const deck = store.decks().find((d) => d.id === DEMO_DECK_ID)
  // Nome igual: o idioma não mudou, não há motivo para abrir o arquivo do deck.
  if (!deck || deck.name === t('demo.name')) return false
  return retranslate(store, t)
}

function retranslate(store, t) {
  store.applyChanges([{ t: 'd', id: DEMO_DECK_ID, n: t('demo.name') }])
  const cards = store.loadCards(DEMO_DECK_ID)
  let changed = false
  const updated = cards.map((card) => {
    const n = cardNumber(card.i)
    if (n === 0) return card
    const f = t('demo.q' + n)
    const b = t('demo.a' + n)
    if (card.f === f && card.b === b) return card
    changed = true
    return Object.assign({}, card, { f, b })
  })
  if (changed) store.saveCards(DEMO_DECK_ID, updated)
  return true
}
