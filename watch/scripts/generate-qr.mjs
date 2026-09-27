// Gera assets/common.r/qr-app.png com o endereço de download do app do celular.
// Rode de novo sempre que mudar APP_DOWNLOAD_URL em lib/config.js.
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { APP_DOWNLOAD_URL } from '../lib/config.js'

let toFile
try {
  const qrcode = await import('qrcode')
  toFile = qrcode.default.toFile
} catch (error) {
  console.error('Falta a dependência de build do QR code. Rode: npm install')
  process.exit(1)
}

const dir = new URL('../assets/common.r/', import.meta.url)
mkdirSync(dir, { recursive: true })
const file = fileURLToPath(new URL('qr-app.png', dir))

// 220 px: o mesmo tamanho em que a tela desenha, para não haver reescala e borrão.
await toFile(file, APP_DOWNLOAD_URL, {
  width: 220,
  margin: 2,
  errorCorrectionLevel: 'M',
  color: { dark: '#000000ff', light: '#ffffffff' },
})
console.log('qr-app.png gerado para ' + APP_DOWNLOAD_URL)
