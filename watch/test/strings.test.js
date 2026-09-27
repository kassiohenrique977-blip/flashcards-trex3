import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { ENGLISH, makeText, englishText } from '../lib/strings.js'
import { PORTUGUES } from '../scripts/strings-pt.mjs'
import { arquivosPo, PASTA_I18N } from '../scripts/generate-po.mjs'

const BARRA = String.fromCharCode(92)

test('inglês e português têm exatamente as mesmas chaves', () => {
  assert.deepEqual(Object.keys(PORTUGUES).sort(), Object.keys(ENGLISH).sort())
})

test('nenhum texto está vazio', () => {
  for (const chave of Object.keys(ENGLISH)) {
    assert.ok(ENGLISH[chave].length > 0, chave)
    assert.ok(PORTUGUES[chave].length > 0, chave)
  }
})

test('o inglês é a base: chave conhecida vira texto, chave desconhecida vira ela mesma', () => {
  assert.equal(englishText('study.show'), 'SHOW')
  assert.equal(englishText('nao.existe'), 'nao.existe')
})

test('a tradução do sistema ganha do inglês', () => {
  const t = makeText((chave) => (chave === 'study.show' ? 'MOSTRAR' : ''))
  assert.equal(t('study.show'), 'MOSTRAR')
  assert.equal(t('study.good'), ENGLISH['study.good'])
})

test('sem tradução (vazio, a própria chave ou erro) o texto cai no inglês', () => {
  assert.equal(makeText(() => '')('summary.ok'), 'OK')
  assert.equal(makeText((chave) => chave)('home.start'), 'START')
  assert.equal(
    makeText(() => {
      throw new Error('sem i18n neste aparelho')
    })('home.start'),
    'START',
  )
})

test('barra-n vindo do .po ainda quebra a linha na tela', () => {
  const t = makeText(() => 'Primeira.' + BARRA + 'nSegunda.')
  assert.equal(t('home.empty'), 'Primeira.\nSegunda.')
})

test('os .po em page/i18n estão em dia com as fontes', () => {
  const arquivos = arquivosPo()
  for (const nome of Object.keys(arquivos)) {
    const emDisco = readFileSync(join(PASTA_I18N, nome), 'utf8')
    assert.equal(emDisco, arquivos[nome], nome + ': rode "npm run i18n"')
  }
})

test('o .po escapa as quebras de linha e traz todas as chaves', () => {
  const arquivos = arquivosPo()
  for (const nome of Object.keys(arquivos)) {
    const conteudo = arquivos[nome]
    assert.ok(conteudo.startsWith('msgid ""\nmsgstr ""\n'), nome)
    for (const chave of Object.keys(ENGLISH)) {
      assert.ok(conteudo.includes('msgid "' + chave + '"\nmsgstr "'), nome + ' sem ' + chave)
    }
    // Uma entrada ocupa uma linha: o texto com quebra de linha vai escapado.
    assert.ok(conteudo.includes(BARRA + 'nSync with your phone.') || nome !== 'en-US.po')
    for (const linha of conteudo.split('\n')) {
      assert.ok(linha === '' || linha.startsWith('msgid ') || linha.startsWith('msgstr ') || linha.startsWith('"'), linha)
    }
  }
})

test('português de Portugal troca o vocabulário', () => {
  const ptPt = arquivosPo()['pt-PT.po']
  assert.ok(ptPt.includes('telemóvel'), 'pt-PT deveria falar telemóvel')
  assert.ok(!ptPt.includes('celular'), 'pt-PT não deveria falar celular')
  assert.ok(ptPt.includes('câmara'))
})
