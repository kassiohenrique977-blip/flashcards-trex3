import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createPhoneClient, cleanSetting, parseResponse, DEFAULT_SERVER_URL } from '../app-side/phone-client.js'

function client(settings = {}, respond = () => ({ status: 200, body: { ok: true } })) {
  const calls = []
  const handlers = createPhoneClient({
    fetch: async (options) => {
      calls.push(options)
      return respond(options)
    },
    getSetting: (key) => settings[key],
  })
  return { handlers, calls }
}

test('usa 127.0.0.1:8765 e o token do Settings App', async () => {
  const { handlers, calls } = client({ pairToken: '"123456"' })
  await handlers['sync.hello']({ deviceId: 'watch 1' })
  assert.equal(calls[0].url, DEFAULT_SERVER_URL + '/v1/hello?deviceId=watch%201')
  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[0].headers['X-Pair-Token'], '123456')
  assert.equal(calls[0].body, undefined)
})

test('endereço configurado sem barra final', async () => {
  const { handlers, calls } = client({ serverUrl: 'http://localhost:9000/' })
  await handlers['sync.pull']({ deviceId: 'w', since: 7, limit: 30 })
  assert.equal(calls[0].url, 'http://localhost:9000/v1/changes?deviceId=w&since=7&limit=30')
})

test('push e ack mandam JSON no corpo', async () => {
  const { handlers, calls } = client()
  await handlers['sync.push']({ deviceId: 'w', reviews: [{ id: 'r1' }] })
  await handlers['sync.ack']({ deviceId: 'w', cursor: 12 })
  assert.deepEqual(JSON.parse(calls[0].body), { deviceId: 'w', reviews: [{ id: 'r1' }] })
  assert.equal(calls[0].method, 'POST')
  assert.deepEqual(JSON.parse(calls[1].body), { deviceId: 'w', cursor: 12 })
})

test('corpo em texto JSON é convertido', async () => {
  const { handlers } = client({}, () => ({ status: 200, body: '{"protocol":1}' }))
  assert.deepEqual(await handlers['sync.hello']({ deviceId: 'w' }), { protocol: 1 })
})

test('401 vira UNAUTHORIZED', async () => {
  const { handlers } = client({}, () => ({ status: 401, body: { error: 'UNAUTHORIZED', message: 'token' } }))
  await assert.rejects(handlers['sync.hello']({}), (e) => e.code === 'UNAUTHORIZED')
})

test('app do celular fechado vira NO_SERVER', async () => {
  const handlers = createPhoneClient({
    fetch: async () => {
      throw new Error('connect ECONNREFUSED')
    },
    getSetting: () => undefined,
  })
  await assert.rejects(handlers['sync.hello']({}), (e) => e.code === 'NO_SERVER')
})

test('resposta que não é JSON vira BAD_RESPONSE', () => {
  assert.throws(() => parseResponse({ status: 200, body: '<html>' }), (e) => e.code === 'BAD_RESPONSE')
})

test('cleanSetting tira aspas e espaços', () => {
  assert.equal(cleanSetting(' "abc" '), 'abc')
  assert.equal(cleanSetting('abc'), 'abc')
  assert.equal(cleanSetting(undefined), '')
  assert.equal(cleanSetting(123456), '123456')
})
