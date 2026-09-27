// Gera assets/common.r/icon.png (248×248, RGBA) sem dependências: dois cartões
// empilhados sobre um círculo azul-marinho, as mesmas formas do ícone Android.
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'

const SIZE = 248
const pixels = Buffer.alloc(SIZE * SIZE * 4) // transparente

const NAVY = [0x1e, 0x3a, 0x5f, 255]
const SKY = [0x7f, 0xb3, 0xff, 255]
const WHITE = [255, 255, 255, 255]

function paint(test, color) {
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      if (!test(x + 0.5, y + 0.5)) continue
      const offset = (y * SIZE + x) * 4
      pixels[offset] = color[0]
      pixels[offset + 1] = color[1]
      pixels[offset + 2] = color[2]
      pixels[offset + 3] = color[3]
    }
  }
}

const circle = (cx, cy, r) => (x, y) => (x - cx) ** 2 + (y - cy) ** 2 <= r * r

function roundedRect(x0, y0, x1, y1, r) {
  return (x, y) => {
    if (x < x0 || x > x1 || y < y0 || y > y1) return false
    const cx = Math.min(Math.max(x, x0 + r), x1 - r)
    const cy = Math.min(Math.max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
  }
}

paint(circle(124, 124, 124), NAVY)
paint(roundedRect(104, 58, 180, 166, 10), SKY)
paint(roundedRect(70, 82, 146, 190, 10), WHITE)
paint(roundedRect(84, 120, 132, 130, 3), NAVY)
paint(roundedRect(84, 146, 118, 156, 3), NAVY)

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

function crc32(buffer) {
  let c = 0xffffffff
  for (const byte of buffer) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

const header = Buffer.alloc(13)
header.writeUInt32BE(SIZE, 0)
header.writeUInt32BE(SIZE, 4)
header[8] = 8 // bits por canal
header[9] = 6 // RGBA
const rows = Buffer.alloc(SIZE * (SIZE * 4 + 1))
for (let y = 0; y < SIZE; y++) {
  rows[y * (SIZE * 4 + 1)] = 0 // filtro "None"
  pixels.copy(rows, y * (SIZE * 4 + 1) + 1, y * SIZE * 4, (y + 1) * SIZE * 4)
}

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', header),
  chunk('IDAT', deflateSync(rows)),
  chunk('IEND', Buffer.alloc(0)),
])

const dir = new URL('../assets/common.r/', import.meta.url)
mkdirSync(dir, { recursive: true })
writeFileSync(new URL('icon.png', dir), png)
console.log('icon.png gerado (' + png.length + ' bytes)')
