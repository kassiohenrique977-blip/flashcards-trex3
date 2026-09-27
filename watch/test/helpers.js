// Backend de armazenamento em memória com a mesma interface do LocalStorage do
// Zepp OS. Os valores passam por JSON, como no arquivo real.

export function memoryBackend() {
  const files = new Map()
  const clone = (value) => (value === undefined ? undefined : JSON.parse(JSON.stringify(value)))
  const open = (name) => {
    if (!files.has(name)) files.set(name, new Map())
    const data = files.get(name)
    return {
      getItem: (key, fallback) => (data.has(key) ? clone(data.get(key)) : fallback),
      setItem: (key, value) => data.set(key, clone(value)),
      removeItem: (key) => data.delete(key),
      clear: () => data.clear(),
    }
  }
  return { open, files }
}

export function sequence(prefix = 'id') {
  let next = 0
  return () => prefix + '-' + ++next
}

/** Cartão no formato compacto do relógio. */
export function card(id, state = 0, dueAt = 0, extra = {}) {
  return Object.assign(
    { i: id, f: 'frente ' + id, b: 'verso ' + id, s: state, d: dueAt, iv: state === 2 ? 5 : 0, ef: 2.5, r: 2, l: 0, ls: 0, lr: 0 },
    extra,
  )
}
