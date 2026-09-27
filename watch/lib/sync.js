// Cliente do protocolo de sincronização (docs/PROTOCOL.md), rodando no relógio.
// Ordem: hello → envia a outbox em lotes → recebe mudanças página por página → ack.
// Tudo é idempotente: a outbox só perde uma resposta depois que o celular a confirma,
// e o cursor avança só depois que a página foi gravada. Uma queda no meio continua de
// onde parou na próxima sincronização.

export const PROTOCOL_VERSION = 1
export const PUSH_BATCH = 50
export const PULL_PAGE = 30

export class SyncError extends Error {
  constructor(code, message) {
    super(message || code)
    this.code = code
  }
}

/**
 * @param transport { request(method, params) => Promise } (no relógio: ZML this.request)
 * @param onProgress ({ step: 'connect'|'push'|'pull'|'finish', done?, total? }) => void
 * @returns { pushed, received }
 */
export async function runSync({ transport, store, onProgress, now, pushBatch, pullPage }) {
  const progress = onProgress || (() => {})
  const clock = now || (() => Date.now())
  const deviceId = store.deviceId()

  progress({ step: 'connect' })
  const hello = await transport.request('sync.hello', { deviceId, protocol: PROTOCOL_VERSION })
  if (!hello || hello.protocol !== PROTOCOL_VERSION) {
    throw new SyncError('PROTOCOL', 'Versão de protocolo diferente: ' + (hello && hello.protocol))
  }

  const pushed = await pushOutbox(transport, store, deviceId, pushBatch || PUSH_BATCH, progress)
  const received = await pullChanges(transport, store, deviceId, pullPage || PULL_PAGE, progress)

  progress({ step: 'finish' })
  await transport.request('sync.ack', { deviceId, cursor: store.cursor() })
  store.setLastSyncAt(clock())
  return { pushed, received }
}

async function pushOutbox(transport, store, deviceId, batchSize, progress) {
  let pending = store.outbox()
  const total = pending.length
  let pushed = 0
  while (pending.length > 0) {
    progress({ step: 'push', done: pushed, total })
    const batch = pending.slice(0, batchSize)
    const result = await transport.request('sync.push', { deviceId, reviews: batch })
    const confirmed = confirmedIds(batch, result)
    // Nada confirmado: repetir o mesmo lote não resolveria. Melhor parar e avisar.
    if (confirmed.length === 0) throw new SyncError('PUSH_REJECTED', 'O celular não aceitou as respostas.')
    store.removeFromOutbox(confirmed)
    pushed += confirmed.length
    pending = store.outbox()
  }
  return pushed
}

async function pullChanges(transport, store, deviceId, pageSize, progress) {
  let cursor = store.cursor()
  let received = 0
  for (;;) {
    progress({ step: 'pull', done: received })
    const page = await transport.request('sync.pull', { deviceId, since: cursor, limit: pageSize })
    if (!page || !Array.isArray(page.changes) || typeof page.nextSince !== 'number' || page.nextSince < cursor) {
      throw new SyncError('BAD_RESPONSE', 'Resposta inválida do celular.')
    }
    store.applyChanges(page.changes, page.settings)
    received += page.changes.length
    // Uma página pode vir sem mudanças visíveis (ex.: cartões de um deck que não vai
    // para o relógio) e ainda assim ter mais depois. O que importa é o cursor andar.
    const advanced = page.nextSince > cursor
    cursor = page.nextSince
    store.setCursor(cursor)
    if (!page.hasMore) return received
    if (!advanced) throw new SyncError('BAD_RESPONSE', 'O celular não avançou o cursor.')
  }
}

/** IDs do lote que o celular aceitou, já tinha ou recusou de vez (cartão apagado). */
export function confirmedIds(batch, result) {
  const done = {}
  const mark = (id) => (done[id] = true)
  ;(result && result.accepted ? result.accepted : []).forEach(mark)
  ;(result && result.duplicates ? result.duplicates : []).forEach(mark)
  ;(result && result.rejected ? result.rejected : []).forEach((r) => mark(r.id))
  return batch.filter((r) => done[r.id]).map((r) => r.id)
}

/** Mensagem curta para a tela do relógio. */
export function syncErrorMessage(error) {
  switch (error && error.code) {
    case 'UNAUTHORIZED':
      return 'Código de pareamento inválido. Confira no Zepp App: Flashcards > Configurações.'
    case 'NO_SERVER':
      return 'Abra o app Flashcards no celular, na aba Relógio, e tente de novo.'
    case 'PROTOCOL':
      return 'Versões diferentes. Atualize o app do celular e o do relógio.'
    case 'PUSH_REJECTED':
      return 'O celular recusou as respostas. Tente de novo.'
    case 'BAD_RESPONSE':
      return 'Resposta inválida do celular. Tente de novo.'
    default:
      return 'Sem conexão com o celular. Verifique o Bluetooth e o Zepp App.'
  }
}
