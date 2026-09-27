// Onde as pessoas baixam o app do celular. Depois de mudar a URL, rode `npm run qr`
// para gerar de novo o QR code que aparece no relógio.
export const APP_DOWNLOAD_URL = 'https://github.com/kassiohenrique977-blip/flashcards-trex3/releases/latest'

/** Versão curta, para caber na tela do relógio. */
export const APP_DOWNLOAD_LABEL = APP_DOWNLOAD_URL.replace(/^https?:\/\//, '')
