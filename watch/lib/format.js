export function countLabel(count, singular, plural) {
  return count === 1 ? '1 ' + singular : count + ' ' + plural
}

/** "12 cartões" / "12 cards", no idioma do relógio. [t] é a função de texto. */
export function cardCountLabel(count, t) {
  return countLabel(count, t('card.one'), t('card.many'))
}

/** "8:32" */
export function formatDuration(ms) {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  const rest = seconds % 60
  return Math.floor(seconds / 60) + ':' + (rest < 10 ? '0' : '') + rest
}

/** Parâmetros de rota chegam como texto JSON (ou objeto, dependendo da versão). */
export function parseParams(params) {
  if (!params) return {}
  if (typeof params === 'object') return params
  try {
    return JSON.parse(params)
  } catch (e) {
    return {}
  }
}
