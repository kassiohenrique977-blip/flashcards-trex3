/** UUID v4. As revisões recebem IDs no relógio e o celular os usa para não contar nada duas vezes. */
export function uuid(random = Math.random) {
  let out = ''
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) {
      out += '-'
      continue
    }
    if (i === 14) {
      out += '4'
      continue
    }
    let n = Math.floor(random() * 16)
    if (i === 19) n = (n & 0x3) | 0x8
    out += n.toString(16)
  }
  return out
}
