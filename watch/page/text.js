import { getText } from '@zos/i18n'
import { makeText } from '../lib/strings.js'

// Texto das telas do relógio. Primeiro tenta a tradução do sistema (page/i18n/*.po);
// se o idioma do relógio não tiver tradução, cai no inglês de lib/strings.js.
export const t = makeText(getText)
