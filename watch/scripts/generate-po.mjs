// Gera os arquivos de tradução do app do relógio (page/i18n/*.po) a partir de
// lib/strings.js (inglês, a base do app) e scripts/strings-pt.mjs (português).
//
//   npm run i18n
//
// O inglês também vira .po para que o relógio use o mesmo caminho em todos os idiomas.
// O teste test/strings.test.js confere se os .po estão em dia com as fontes.

import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { ENGLISH } from '../lib/strings.js'
import { PORTUGUES } from './strings-pt.mjs'

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..')
export const PASTA_I18N = join(RAIZ, 'page', 'i18n')

// Português de Portugal: as diferenças que aparecem nestes textos são de vocabulário.
const PT_PT = [
  [/celular/g, 'telemóvel'],
  [/Celular/g, 'Telemóvel'],
  [/CELULAR/g, 'TELEMÓVEL'],
  [/câmera/g, 'câmara'],
]

function paraPtPt(texto) {
  return PT_PT.reduce((atual, [de, para]) => atual.replace(de, para), texto)
}

function traduzirTudo(mapa, transformar) {
  const saida = {}
  for (const chave of Object.keys(mapa)) saida[chave] = transformar(mapa[chave])
  return saida
}

const BARRA = String.fromCharCode(92)

/** Escapa o texto para dentro das aspas de uma entrada .po. */
function escapar(texto) {
  return texto
    .split(BARRA)
    .join(BARRA + BARRA)
    .split('"')
    .join(BARRA + '"')
    .split('\n')
    .join(BARRA + 'n')
}

/** Um arquivo .po (gettext) com uma entrada por chave, na ordem do inglês. */
export function po(idioma, mapa) {
  const faltando = Object.keys(ENGLISH).filter((chave) => mapa[chave] === undefined)
  if (faltando.length > 0) throw new Error(idioma + ': falta traduzir ' + faltando.join(', '))
  const sobrando = Object.keys(mapa).filter((chave) => ENGLISH[chave] === undefined)
  if (sobrando.length > 0) throw new Error(idioma + ': chave que não existe em inglês: ' + sobrando.join(', '))

  const quebra = BARRA + 'n'
  const linhas = [
    'msgid ""',
    'msgstr ""',
    '"Language: ' + idioma + quebra + '"',
    '"Content-Type: text/plain; charset=UTF-8' + quebra + '"',
    '',
  ]
  for (const chave of Object.keys(ENGLISH)) {
    linhas.push('msgid "' + escapar(chave) + '"')
    linhas.push('msgstr "' + escapar(mapa[chave]) + '"')
    linhas.push('')
  }
  return linhas.join('\n')
}

/** { 'en-US.po': conteúdo, ... } */
export function arquivosPo() {
  return {
    'en-US.po': po('en-US', ENGLISH),
    'pt-BR.po': po('pt-BR', PORTUGUES),
    'pt-PT.po': po('pt-PT', traduzirTudo(PORTUGUES, paraPtPt)),
  }
}

function escrever() {
  mkdirSync(PASTA_I18N, { recursive: true })
  const arquivos = arquivosPo()
  for (const nome of Object.keys(arquivos)) {
    writeFileSync(join(PASTA_I18N, nome), arquivos[nome], 'utf8')
    console.log('page/i18n/' + nome + ': ' + Object.keys(ENGLISH).length + ' textos')
  }
}

const chamadoDireto = (process.argv[1] || '').split(BARRA).join('/').endsWith('scripts/generate-po.mjs')
if (chamadoDireto) escrever()
