// Cliente HTTP do app Android, usado pelo side service (roda no Zepp App do celular).
// O app Android escuta só em 127.0.0.1 enquanto a tela "Relógio" está aberta.
// O token de pareamento fica no Settings App (no celular) e nunca vai para o relógio.

export const DEFAULT_SERVER_URL = 'http://127.0.0.1:8765'
export const SETTINGS_KEYS = { token: 'pairToken', serverUrl: 'serverUrl' }
const DEFAULT_TIMEOUT_MS = 15000

/** O Settings App pode guardar o texto entre aspas (JSON); aqui sempre sai texto limpo. */
export function cleanSetting(value) {
  if (value === undefined || value === null) return ''
  let text = String(value).trim()
  if (text.length >= 2 && text[0] === '"' && text[text.length - 1] === '"') {
    try {
      text = String(JSON.parse(text))
    } catch (e) {
      // não era JSON: usa como está
    }
  }
  return text.trim()
}

/**
 * @param fetch função fetch do side service (ZML this.fetch)
 * @param getSetting (chave) => valor salvo pelo Settings App
 * @returns handlers { 'sync.hello', 'sync.push', 'sync.pull', 'sync.ack' }, cada um (params) => Promise
 */
export function createPhoneClient({ fetch, getSetting, timeout }) {
  const baseUrl = () => (cleanSetting(getSetting(SETTINGS_KEYS.serverUrl)) || DEFAULT_SERVER_URL).replace(/\/+$/, '')

  function call(method, path, body) {
    const options = {
      url: baseUrl() + path,
      method,
      headers: {
        'Content-Type': 'application/json',
        'X-Pair-Token': cleanSetting(getSetting(SETTINGS_KEYS.token)),
      },
      timeout: timeout || DEFAULT_TIMEOUT_MS,
    }
    if (body !== undefined) options.body = JSON.stringify(body)
    return Promise.resolve()
      .then(() => fetch(options))
      .then(parseResponse, (error) => {
        throw { code: 'NO_SERVER', message: String((error && error.message) || error) }
      })
  }

  const deviceQuery = (params) => 'deviceId=' + encodeURIComponent(params.deviceId || '')

  return {
    'sync.hello': (params) => call('GET', '/v1/hello?' + deviceQuery(params)),
    'sync.push': (params) => call('POST', '/v1/reviews', { deviceId: params.deviceId, reviews: params.reviews || [] }),
    'sync.pull': (params) =>
      call(
        'GET',
        '/v1/changes?' + deviceQuery(params) + '&since=' + Number(params.since || 0) + '&limit=' + Number(params.limit || 30),
      ),
    'sync.ack': (params) => call('POST', '/v1/ack', { deviceId: params.deviceId, cursor: Number(params.cursor || 0) }),
  }
}

/** Em alguns modelos o corpo chega como texto JSON; em outros, já como objeto. */
export function parseResponse(response) {
  let data = response.body
  if (typeof data === 'string') {
    try {
      data = data ? JSON.parse(data) : null
    } catch (e) {
      throw { code: 'BAD_RESPONSE', message: 'Resposta inválida do celular.' }
    }
  }
  const status = response.status
  if (status >= 200 && status < 300) return data
  const code = status === 401 ? 'UNAUTHORIZED' : (data && data.error) || 'HTTP_' + status
  throw { code, message: (data && data.message) || 'HTTP ' + status }
}
