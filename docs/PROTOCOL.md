# Protocolo de sincronização celular ↔ relógio (v1)

Exemplos de todas as mensagens: [shared/protocol-examples.json](../shared/protocol-examples.json).
Os testes Kotlin (`ProtocolExamplesTest`) e JS (`watch/test/protocol.test.js`) conferem
esse arquivo. Se um lado mudar o formato, os dois lados quebram.

## Caminho das mensagens

```
Relógio (Device App)                Zepp App (Side Service)             App Android
    │  ZML request {method, params}      │  HTTP/1.1 JSON                     │
    │ ─────────── BLE ─────────────────▶ │ ───────── 127.0.0.1:8765 ────────▶ │ Ktor CIO
    │ ◀────────── resposta ───────────── │ ◀──────── resposta ─────────────── │ SyncServer
```

- **O relógio sempre começa.** A sincronização só roda com a tela "Sincronizar" aberta no relógio.
- **O side service** (`watch/app-side`) traduz cada método para uma rota HTTP e acrescenta o
  cabeçalho `X-Pair-Token` com o código digitado no Settings App. O código nunca vai para o relógio.
- **O servidor Android** escuta só em `127.0.0.1`, e apenas enquanto a sincronização está ligada
  na aba "Relógio" (foreground service).

## Autenticação

- Cabeçalho `X-Pair-Token: <código de 6 dígitos>` em toda requisição.
- Código errado responde `401 {"error":"UNAUTHORIZED"}`.
- Depois de 10 códigos errados em 5 minutos, tudo responde `429 {"error":"LOCKED"}` até a janela
  passar. Isso protege contra outro app do celular tentando adivinhar o código.

## Métodos

| Relógio (ZML) | HTTP | Corpo / parâmetros | Resposta |
|---|---|---|---|
| `sync.hello` | `GET /v1/hello?deviceId=` | — | `{protocol, serverId, serverTime, deviceKnown}` |
| `sync.push` | `POST /v1/reviews` | `{deviceId, reviews: [Review]}` (máx. 500) | `{accepted: [id], duplicates: [id], rejected: [{id, reason}]}` |
| `sync.pull` | `GET /v1/changes?deviceId=&since=&limit=` | `limit` de 1 a 200 | `{changes: [Change], nextSince, hasMore, settings}` |
| `sync.ack` | `POST /v1/ack` | `{deviceId, cursor}` | `{ok: true}` |

Erros sempre vêm como `{"error": "CÓDIGO", "message": "texto"}`:

| Status | Código | Quando |
|---|---|---|
| 400 | `BAD_REQUEST`, `TOO_MANY_REVIEWS` | Parâmetro ou JSON inválido |
| 401 | `UNAUTHORIZED` | Código de pareamento errado |
| 413 | `TOO_LARGE` | Corpo acima de 1 MB |
| 429 | `LOCKED` | Tentativas demais com código errado |
| 500 | `SERVER_ERROR` | Falha ao processar |

O side service transforma falha de conexão (app Android fechado) em `NO_SERVER`.

### Review: resposta dada no relógio

| Chave | Significado |
|---|---|
| `id` | UUID gerado no relógio. É a **chave de idempotência** |
| `c`, `k` | ID do cartão e do deck |
| `r` | Resposta: 1 AGAIN, 2 HARD, 3 GOOD, 4 EASY |
| `t` | Momento da resposta (epoch ms) |
| `ms` | Tempo gasto no cartão |
| `sid` | ID da sessão |
| `ps`, `ns` | Estado antes e depois: 0 NEW, 1 LEARNING, 2 REVIEW, 3 RELEARNING |
| `pi`, `ni` | Intervalo em dias antes e depois (0 nas etapas curtas) |
| `a` | Algoritmo que calculou (`sm2-v1`) |

### Change: mudança enviada ao relógio

- **Deck:** `{t: "d", id, n: nome}`
- **Cartão:** `{t: "c", id, k: deckId, f, b, s, d: dueAt, iv, ef, r, l, ls, lr}`
- **Remoção:** `{t: "d", id, del: true}` ou `{t: "c", id, k, del: true}`

Campos nulos e `del: false` não são enviados.

Um deck com "Enviar para o relógio" desligado é enviado ao relógio como remoção, e os
cartões dele não são enviados.

## Sequência e garantias

1. **`hello`**: confere o código e a versão do protocolo. Versão diferente interrompe tudo
   sem alterar dados.
2. **`push`**: a outbox é enviada em lotes de 50. O celular grava cada resposta pelo `id`.
   Reenviar é seguro, porque o que já existe volta em `duplicates`. O relógio só apaga da
   outbox o que voltou em `accepted`, `duplicates` ou `rejected`.
3. **O celular recalcula** cada cartão afetado, reproduzindo todas as revisões dele (celular e
   relógio) em ordem de `reviewedAt` e desempatando pelo `id`. O resultado não depende da
   ordem de chegada. Cada cartão recalculado ganha uma nova versão no feed.
4. **`pull`**: páginas com `version > since`. O relógio grava a página e só então avança o
   cursor para `nextSince`. Se a conexão cair, a próxima sincronização continua da última
   página gravada.
5. **`ack`**: o celular registra até onde o relógio tem os dados (`watch_devices.lastAckSeq`)
   e marca essas mudanças como `SYNCED`.

**Conflitos:**
- **Conteúdo** (frente, verso, nome do deck): o celular é a fonte da verdade. O relógio nunca
  edita conteúdo.
- **Agendamento:** é derivado do log de revisões, então os dois lados convergem para o mesmo estado.

**Por que enviar antes de receber:** se o relógio recebesse primeiro, sobrescreveria cartões
que ainda têm respostas locais não enviadas.

## Feed de mudanças (`sync_state`)

- Toda alteração em deck ou cartão recebe `version = MAX(version) + 1`, na mesma transação.
- Cada entidade tem uma linha, sempre com a última versão. Alterar o mesmo cartão várias vezes
  gera uma única entrada.
- Remoções viram "lápides" (`deleted = true`).
- Ao apagar um deck, as linhas dos cartões dele são removidas depois de gravada a lápide do deck.
  Como a lápide tem a maior versão, o `MAX(version)` nunca diminui.
