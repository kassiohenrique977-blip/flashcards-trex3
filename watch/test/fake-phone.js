// Celular de mentira que fala o protocolo v1 (docs/PROTOCOL.md), para testar o
// cliente de sincronização sem Bluetooth nem app Android.

export function createFakePhone() {
  const phone = {
    version: 0,
    log: [], // { version, change } — só a última versão de cada entidade
    reviews: new Map(),
    requests: [],
    ackedCursor: null,
    failWhen: null, // (method, params, callIndex) => boolean
    settings: { newCardsPerDay: 20, maxReviewsPerDay: 200, dayCutoffHour: 4 },
  }

  function record(change) {
    phone.log = phone.log.filter((entry) => !(entry.change.t === change.t && entry.change.id === change.id))
    phone.log.push({ version: ++phone.version, change })
  }

  function latestCard(id) {
    const entry = phone.log.find((e) => e.change.t === 'c' && e.change.id === id && !e.change.del)
    return entry ? entry.change : null
  }

  phone.addDeck = (id, name) => record({ t: 'd', id, n: name })
  phone.deleteDeck = (id) => record({ t: 'd', id, del: true })
  phone.addCard = (deckId, id, front, back) =>
    record({ t: 'c', id, k: deckId, f: front, b: back, s: 0, d: phone.version, iv: 0, ef: 2.5, r: 0, l: 0, ls: 0, lr: 0 })
  phone.deleteCard = (deckId, id) => record({ t: 'c', id, k: deckId, del: true })

  const handlers = {
    'sync.hello': () => ({ protocol: 1, serverId: 'fake-phone', serverTime: 0 }),
    'sync.push': ({ reviews }) => {
      const accepted = []
      const duplicates = []
      const rejected = []
      for (const review of reviews) {
        if (phone.reviews.has(review.id)) {
          duplicates.push(review.id)
        } else if (!latestCard(review.c)) {
          rejected.push({ id: review.id, reason: 'UNKNOWN_CARD' })
        } else {
          phone.reviews.set(review.id, review)
          accepted.push(review.id)
          // O celular recalcula o cartão a partir do log e o devolve atualizado.
          const card = latestCard(review.c)
          record(Object.assign({}, card, { s: review.ns, r: card.r + 1, lr: review.t }))
        }
      }
      return { accepted, duplicates, rejected }
    },
    'sync.pull': ({ since, limit }) => {
      const pending = phone.log.filter((e) => e.version > since).sort((a, b) => a.version - b.version)
      const page = pending.slice(0, limit)
      return {
        changes: page.map((e) => e.change),
        nextSince: page.length ? page[page.length - 1].version : since,
        hasMore: pending.length > page.length,
        settings: phone.settings,
      }
    },
    'sync.ack': ({ cursor }) => {
      phone.ackedCursor = cursor
      return { ok: true }
    },
  }

  phone.transport = {
    request: async (method, params) => {
      const callIndex = phone.requests.length
      phone.requests.push({ method, params: JSON.parse(JSON.stringify(params)) })
      if (phone.failWhen && phone.failWhen(method, params, callIndex)) throw { code: 'NO_SERVER', message: 'offline' }
      return JSON.parse(JSON.stringify(handlers[method](params)))
    },
  }
  return phone
}
