# Arquitetura: Flashcards para Amazfit T-Rex 3

> Documento de decisão técnica do projeto **Flashcards para Amazfit T-Rex 3**.
> As seções 1 a 12 são a análise da FASE 1, feita antes de qualquer código. A seção 0
> resume o que foi implementado e onde a implementação se afastou do plano.
>
> Marcações usadas: **[DOC]** vem da documentação oficial da Zepp; **[COMUNIDADE]** foi
> comprovado em projetos públicos, mas não está formalmente documentado; **[VALIDAR]**
> precisa ser confirmado no relógio real.

## 0. Como ficou a implementação

**Implementado conforme o plano:** app Android (Kotlin, Compose, Room); Mini Program
Zepp OS; ponte HTTP em `127.0.0.1` via side service; algoritmo próprio `sm2-v1`
com vetores de teste compartilhados; feed de mudanças `sync_state` com cursor;
outbox de respostas no relógio; recálculo por replay do log de revisões.

**Ajustes feitos durante a implementação:**

| Plano | Implementado | Motivo |
|---|---|---|
| compileSdk 36, bibliotecas mais novas | compileSdk 36.1, com AndroidX travado nas últimas versões compatíveis com o 36 | core-ktx 1.19, lifecycle 2.11, navigation 2.10 e Compose 1.12 exigem compileSdk 37, que não está instalado |
| `app.json` com `designWidth` por dispositivo | `targets.common` com `platforms: [{ "st": "r" }]` | Formato v3 dos exemplos oficiais da ZML; vale para todas as telas redondas |
| Métodos BLE próprios | ZML `request`/`onRequest` (JSON-RPC sobre BLE, com fragmentação e timeout) | Biblioteca oficial da Zepp; `timeout` padrão de 60 s, usado com 30 s |
| Servidor HTTP no módulo `:app` | `SyncServer` e rotas no `:core` (Kotlin puro) | Testáveis na JVM com `testApplication` do Ktor, sem Android |
| Home + Lista de decks separadas | Home **é** a lista de decks | O mock da Home já era a lista; uma segunda tela seria duplicada |
| Página de pull só por quantidade | Quantidade **e** tamanho (~16 mil caracteres) | Cartões longos gerariam mensagens BLE enormes |
| Pull parava na primeira página vazia | Continua enquanto `hasMore` e o cursor andar | Cartões de decks fora do relógio geram páginas "vazias" que ainda avançam o cursor |
| Botões físicos a definir | SELECT = mostrar/BOM, UP = FÁCIL, DOWN = ERREI; BACK nunca interceptado | Mapeamento **[VALIDAR]** no aparelho |

**Segurança da ponte local:** o servidor escuta só em `127.0.0.1` e só enquanto a aba
"Relógio" está ativa (foreground service `dataSync`, desliga após 10 min parado). Toda
requisição exige o código de 6 dígitos, e 10 códigos errados em 5 minutos bloqueiam o
servidor (429). Detalhes em [PROTOCOL.md](PROTOCOL.md).

---

## 1. Conclusões da pesquisa

| Pergunta | Resposta |
|---|---|
| Sistema operacional do T-Rex 3 | **Zepp OS**, não Android nem Wear OS. A tabela oficial de dispositivos lista: *Amazfit T-Rex 3 — Latest API_LEVEL 4.0 — Zepp OS 4.5 — deviceSource 8716544\* (China), 8716545, 8716547 — Round — 480×480 — 4 botões físicos* **[DOC]**. A Amazfit anunciou atualização para Zepp OS 5 (dez/2025), mas o API_LEVEL publicado para desenvolvedores continua **4.0**. |
| Roda APK / app Android nativo? | **Não.** Não existe instalação de APK nem de código Kotlin no relógio. |
| Suporta apps de terceiros? | **Sim**, como **Mini Programs do Zepp OS** (loja do Zepp App ou instalação em modo desenvolvedor) **[DOC]**. |
| Linguagem / SDK | JavaScript (módulos `@zos/*`), empacotado pelo **Zeus CLI** (`@zeppos/zeus-cli`). Biblioteca oficial auxiliar **ZML** (`@zeppos/zml`) para comunicação; exige API_LEVEL ≥ 3.0 (v0.0.28 exige ≥ 3.6), então é compatível com o T-Rex 3 (4.0) **[DOC]**. |
| Como instalar no relógio | Zepp App → Perfil → Configurações → Sobre → tocar 7× no ícone do Zepp (ativa Developer Mode). No PC: `zeus login` e depois `zeus preview` gera um QR code; escaneie com "Scan" no Developer Mode **[DOC]**. |
| Comunicação relógio ↔ celular | O relógio só conversa por BLE com o **Side Service**, um código JS que roda **dentro do Zepp App** no celular. O Side Service pode fazer `fetch` HTTP. **Não há API oficial para um app Android de terceiros falar diretamente com o relógio** **[DOC]**. |
| Armazenamento no relógio | `LocalStorage` de `@zos/storage` (API 3.0+, **recomendado** pela doc de persistência; aceita arquivo próprio via `new LocalStorage(storagePath)`). Alternativa: `@zos/fs` (`openSync`, `readFileSync`, `writeFileSync`…) no diretório `/data`, isolado por app. Os dados são apagados ao desinstalar **[DOC]**. |
| Botões físicos | `onKey` de `@zos/interaction`: `KEY_UP`, `KEY_DOWN`, `KEY_SELECT`, `KEY_BACK`, `KEY_HOME`, `KEY_SHORTCUT`; eventos `CLICK`, `LONG_PRESS`, `DOUBLE_CLICK`… Só um handler por vez; retornar `true` bloqueia a ação padrão **[DOC]**. O manual do T-Rex 3 cita UP, DOWN, SELECT e BACK; o mapeamento exato das constantes no aparelho é **[VALIDAR]**. |
| Gestos | `onGesture`: `GESTURE_UP/DOWN/LEFT/RIGHT` **[DOC]**. |
| Tela ligada durante o estudo | `setPageBrightTime({ brightTime })` e `pauseDropWristScreenOff` de `@zos/display` **[DOC]**. |
| UI | `createWidget` de `@zos/ui`: `TEXT` (`text_style.WRAP` para quebra de linha), `BUTTON`, `FILL_RECT`, `VIEW_CONTAINER` (rolagem com `scroll_enable`). `px()` de `@zos/utils` escala a partir de `designWidth` = 480 **[DOC]**. |
| Transferência de arquivos | `TransferFile` (API 3.0+): Outbox/Inbox nos dois lados (`@zos/ble/TransferFile` no relógio, `transferFile` no Side Service) **[DOC]**. Fica como plano B para decks grandes. |
| Teclado | Não será usado no relógio. O código de pareamento é digitado no **Settings App** do mini program, que roda no celular dentro do Zepp App (`TextInput` + `settingsStorage`) **[DOC]**. |

### A ponte com o app Android

A Zepp não fornece Intent, AIDL nem SDK Android para o relógio. O caminho viável,
já usado por projetos em produção, é:

> **O app Android sobe um servidor HTTP em `127.0.0.1` e o Side Service faz `fetch("http://localhost:PORTA/...")`.**

Evidências:
- O Watchdrip (app do relógio + app Android) usa `SERVER_URL = "http://localhost:29863/"` no `app-side` **[COMUNIDADE]**.
- Na discussão #276 da própria organização `zepp-health`, o exemplo envia dados do Side Service com `fetch({ url: "http://localhost:4080/sleep", method: "POST" })` **[COMUNIDADE]**.

A documentação oficial do `fetch` só mostra exemplos HTTPS e não fala de localhost.
Por isso a ponte fica isolada numa camada própria (`SyncTransport`), para poder
ser trocada sem mexer no resto.

---

## 2. Arquitetura geral

```
┌──────────────────────────── CELULAR ANDROID ────────────────────────────┐
│                                                                          │
│  App "Flashcards" (Kotlin)                 Zepp App (oficial, obrigatório)│
│  ┌──────────────────────────┐              ┌───────────────────────────┐ │
│  │ UI Compose (MVVM)        │              │ Side Service (JS, ZML)    │ │
│  │ ViewModels + Flow        │   HTTP/JSON  │  - proxy do protocolo     │ │
│  │ Repositórios             │◄────────────►│  - envia X-Pair-Token     │ │
│  │ Room (fonte da verdade)  │  127.0.0.1   ├───────────────────────────┤ │
│  │ SyncServer (Ktor CIO)    │    :8765     │ Settings App (JS)         │ │
│  │  (foreground service,    │              │  - campo "código de       │ │
│  │   só durante a sync)     │              │    pareamento"            │ │
│  └──────────────────────────┘              └─────────────┬─────────────┘ │
└──────────────────────────────────────────────────────────┼───────────────┘
                                                           │ BLE (ZML request/response)
                                               ┌───────────▼──────────────┐
                                               │ Amazfit T-Rex 3          │
                                               │ Device App (Zepp OS JS)  │
                                               │  - páginas: home, estudo,│
                                               │    resumo, sync          │
                                               │  - SRS em JS (espelho)   │
                                               │  - LocalStorage          │
                                               │  - fila de respostas     │
                                               └──────────────────────────┘
```

**Princípios**
1. **O celular é a fonte da verdade.** O relógio guarda uma cópia do deck e uma fila de respostas.
2. **O relógio calcula a próxima revisão localmente** (para funcionar offline por dias e repetir cartões "ERREI" na mesma sessão). No celular, o estado final de cada cartão é **recalculado a partir do log de revisões**. Isso é determinístico e idempotente.
3. **A sincronização sempre começa no relógio**, com o app aberto na tela "Sincronizar". Nada roda em segundo plano no relógio.
4. **O servidor HTTP do Android só existe durante a janela de sincronização** (foreground service `dataSync`, desliga sozinho após alguns minutos ocioso).

---

## 3. Tecnologias escolhidas

### Android
| Item | Escolha | Motivo |
|---|---|---|
| Linguagem | Kotlin 2.x | Pedido do projeto |
| UI | Jetpack Compose + Material 3 + Navigation Compose | Pedido do projeto |
| Arquitetura | Clean Architecture leve + MVVM | Domínio testável na JVM |
| Banco | Room (KSP) + Coroutines + Flow | Pedido do projeto |
| DI | Manual (`AppContainer`) | Menos dependências e build mais previsível; dá para migrar para Hilt depois |
| Servidor local | Ktor Server CIO + kotlinx.serialization | Moderno, mantido, roda em Android |
| Serviço | Foreground Service tipo `dataSync` | Tipo oficial para sincronização com dispositivo companheiro; sem permissão em tempo de execução |
| Testes | JUnit 4, kotlinx-coroutines-test, Room in-memory | Unitários sem emulador sempre que possível |
| SDK | compileSdk 36, minSdk 26 | SDK 36 já instalado nesta máquina |

**Módulos Gradle**
- `:core`: Kotlin puro (JVM). Modelos, algoritmo SRS, protocolo de sync, parser CSV e contratos de importadores. Testes rápidos, sem Android.
- `:app`: Android. Room, repositórios, servidor de sync e UI.

### Relógio
| Item | Escolha |
|---|---|
| Plataforma | Zepp OS Mini Program, `app.json` configVersion v3 |
| API | `minVersion` 3.6 / `target` 4.0 (confirmar com o template do `zeus create` na FASE 6) |
| Comunicação | `@zeppos/zml` (BaseApp, BasePage, BaseSideService, `request`/`onRequest`) |
| Armazenamento | `LocalStorage`, **um arquivo por deck** (`new LocalStorage('deck_<id>.json')`), para carregar na memória só o deck ativo, mais um arquivo pequeno de metadados e fila |
| Lógica testável | `watch/lib/*.js` em JS puro (sem `@zos`), testado com `node --test` |
| Ferramentas | Zeus CLI 1.9.3 (já instalado), Node 24 |

---

## 4. Estrutura de diretórios (planejada)

```
FLASHCARDS APPS/
├── README.md
├── docs/
│   ├── ARCHITECTURE.md          ← este documento
│   └── PROTOCOL.md              (FASE 7/8)
├── shared/
│   └── srs-test-vectors.json    casos de teste usados por Kotlin E JS
├── sample-data/
│   ├── ingles.csv
│   └── programacao.csv
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   ├── core/src/main/kotlin/com/flashcards/core/
│   │   ├── model/       Deck, Card, Review, Rating, CardState, SyncState
│   │   ├── srs/         Scheduler (interface), Sm2Scheduler, SchedulerConfig
│   │   ├── sync/        DTOs do protocolo, SyncMerger, ReviewReplayer
│   │   └── importer/    CardImporter (interface), CsvImporter
│   ├── core/src/test/...
│   └── app/src/main/java/com/flashcards/app/
│       ├── data/        Room (entities, DAOs, database), repositórios
│       ├── sync/        SyncServer (Ktor), SyncForegroundService, SyncService
│       ├── di/          AppContainer
│       └── ui/          home, decks, deck, card, importer, study, stats, settings, sync
└── watch/
    ├── app.json  app.js  package.json
    ├── page/            home, study, summary, sync
    ├── app-side/index.js
    ├── setting/index.js
    ├── lib/             srs.js, session.js, store.js, protocol.js (JS puro)
    ├── assets/
    └── test/            testes node --test
```

---

## 5. Modelo de dados

### Android (Room)

**DeckEntity**: `id` (UUID), `name`, `description`, `createdAt`, `updatedAt`, `version`, `deletedAt?`, `syncToWatch` (bool)

**CardEntity**: `id` (UUID), `deckId`, `front`, `back`, `tags`, `createdAt`, `updatedAt`, `version`, `deletedAt?`, mais o estado SRS: `state` (NEW/LEARNING/REVIEW/RELEARNING), `dueAt`, `interval` (dias), `easeFactor`, `repetitions`, `lapses`, `learningStep`, `lastReviewedAt?`

**ReviewEntity**: `id` (UUID gerado **no dispositivo que respondeu**, chave de idempotência), `cardId`, `deckId`, `rating` (AGAIN/HARD/GOOD/EASY), `reviewedAt`, `previousInterval`, `newInterval`, `previousState`, `newState`, `durationMs`, `source` (PHONE/WATCH), `deviceId`, `sessionId`, `algorithm`

**SyncStateEntity**: `entityId`, `entityType` (DECK/CARD/REVIEW), `version`, `updatedAt`, `syncStatus` (PENDING/SYNCED)

**WatchDeviceEntity**: `deviceId`, `lastPulledSeq` (cursor confirmado), `lastSyncAt`

**Versionamento**: contador global monotônico `changeSeq` no celular. Toda alteração em deck ou cartão recebe `version = ++changeSeq`, e o pull incremental é simplesmente `WHERE version > :since`. Exclusões são *tombstones* (`deletedAt`), para que o relógio também saiba o que remover.

### Relógio (LocalStorage)
- `meta.json`: `deviceId`, `pairToken`, `pullCursor`, lista de decks `{id, name, count}`, configurações da sessão
- `deck_<id>.json`: cartões em formato compacto `{i, f, b, st, due, iv, ef, r, l, ls, v}`
- `outbox.json`: respostas ainda não confirmadas pelo celular `[{id, c, rt, at, ms, sid}]`

---

## 6. Protocolo de comunicação (resumo; especificação completa na FASE 7)

**Camada 1 (Relógio ↔ Side Service):** ZML `request({ method, params })` com JSON.
O Side Service é um proxy fino: repassa para o celular e devolve a resposta.

**Camada 2 (Side Service ↔ App Android):** HTTP/1.1 JSON em `http://127.0.0.1:8765`,
cabeçalho `X-Pair-Token`.

| Método | Rota | Função |
|---|---|---|
| GET | `/v1/hello` | versão do protocolo, `serverId`, hora do celular |
| POST | `/v1/reviews` | `{deviceId, reviews:[…]}` → `{accepted:[ids], duplicates:[ids]}` |
| GET | `/v1/changes?deviceId=&since=&limit=` | `{decks, cards, deletes, settings, nextSince, hasMore}` |
| POST | `/v1/ack` | `{deviceId, seq}` grava o cursor confirmado |

**Sequência de uma sincronização**
1. `hello` (verifica token e versão do protocolo).
2. **Push**: o relógio envia a outbox em lotes. Cada resposta tem UUID; o celular ignora IDs repetidos. Um item só sai da outbox depois de voltar em `accepted` ou `duplicates`.
3. O celular recalcula os cartões afetados a partir do log de revisões.
4. **Pull**: o relógio pede mudanças com `since = pullCursor`, página por página (cerca de 30 cartões por página, para manter as mensagens BLE pequenas). Cada página é aplicada e só então o cursor avança.
5. **Ack**: o relógio confirma o cursor final.

**Propriedades exigidas**
- *Incremental*: só cartões com `version > cursor`, em formato compacto, sem tags nem datas de criação.
- *Idempotente*: UUID por resposta e cursor por página. Repetir qualquer passo não duplica nada.
- *Resiliente*: se a conexão cai, a outbox e o cursor persistem, e a próxima sync continua de onde parou.
- *Conflitos*: o conteúdo do cartão é sempre do celular (o relógio nunca edita texto). O estado SRS é recalculado reproduzindo todas as revisões do cartão (celular + relógio) em ordem de `reviewedAt`, então o resultado não depende da ordem de chegada.

---

## 7. Algoritmo de repetição espaçada

Implementação própria inspirada no SM-2 (algoritmo de domínio público descrito por
P. Wozniak), com etapas de aprendizado no estilo das apps modernas. **Nenhum código
do Anki ou do AnkiDroid será copiado.**

```kotlin
interface Scheduler {
    val id: String                       // "sm2-v1": gravado em cada Review
    fun schedule(card: SchedulingState, rating: Rating, now: Instant): SchedulingResult
}
```

**Configuração** (`SchedulerConfig`): etapas de aprendizado `[1 min, 10 min]`,
etapas de reaprendizado `[10 min]`, intervalo de graduação 1 dia, intervalo fácil
4 dias, ease inicial 2,5, ease mínimo 1,3, bônus fácil 1,3, multiplicador difícil
1,2, intervalo máximo 36.500 dias. Sem aleatoriedade (*fuzz*) por padrão, para ser
determinístico e testável.

| Estado | AGAIN | HARD | GOOD | EASY |
|---|---|---|---|---|
| NEW / LEARNING | volta à etapa 0 | repete a etapa atual | próxima etapa; na última, gradua para REVIEW (1 dia) | gradua direto (4 dias) |
| REVIEW | `lapses+1`, ease −0,20, vai para RELEARNING | intervalo ×1,2, ease −0,15 | intervalo × ease | intervalo × ease × 1,3, ease +0,15 |
| RELEARNING | volta à etapa 0 | repete a etapa | volta a REVIEW com intervalo reduzido | volta a REVIEW com intervalo reduzido +1 dia |

Regras de ordem: em REVIEW, garante-se sempre HARD < GOOD < EASY, e todo intervalo
novo é pelo menos o anterior + 1 dia (exceto AGAIN).

**Paridade Kotlin ↔ JS**: `shared/srs-test-vectors.json` contém entradas e saídas
esperadas. Os testes de Kotlin e de JS leem o mesmo arquivo, e qualquer divergência
quebra os dois.

**Troca futura (FSRS etc.)**: basta outra implementação de `Scheduler`. Como o
estado é recalculado pelo log de revisões, dá até para reprocessar o histórico
inteiro com o algoritmo novo.

---

## 8. Limitações específicas do T-Rex 3 / Zepp OS e alternativas

| # | Limitação | Impacto | Solução adotada |
|---|---|---|---|
| 1 | Não há canal oficial entre um app Android de terceiros e o relógio | O **Zepp App precisa estar instalado, pareado e aberto** (pode ser em segundo plano). Gadgetbridge não executa Side Service JS (issue #3266) | Ponte HTTP localhost via Side Service |
| 2 | `fetch` para `http://localhost` funciona na prática, mas não está documentado | A Zepp pode mudar isso no futuro | Camada `SyncTransport` isolada. Plano B: exportar um deck pequeno em JSON pelo `settingsStorage` do Settings App |
| 3 | O ciclo de vida do Side Service (quando inicia/termina) não está documentado em detalhe | Sync em segundo plano não é confiável | A sync só acontece com o app do relógio aberto na tela "Sincronizar". Tempo limite e retentativa em cada passo |
| 4 | O servidor em `127.0.0.1` é acessível por qualquer app do celular | Risco de leitura dos decks por outro app | Escuta só em loopback, token de pareamento (exibido no Android e digitado **uma vez** no Settings App, no celular), e servidor ligado apenas durante a janela de sync |
| 5 | Limites de armazenamento, memória e tamanho de mensagem BLE não são documentados | Decks grandes podem estourar a memória | Um arquivo LocalStorage por deck, formato compacto, páginas pequenas, limite configurável de cartões por deck no relógio (padrão 500), monitoramento com `getPerformance()` (API 4.0). TransferFile como plano B |
| 6 | Sem teclado prático no relógio | Não dá para criar cartões no relógio | Criação e edição só no Android (como pedido) |
| 7 | Fonte do sistema pode não ter emojis (😞😐😊) | Os botões podem mostrar quadrados | Botões com **texto + cor** (ERREI vermelho / DIFÍCIL laranja / BOM verde / FÁCIL azul). Emoji fica opcional se a fonte suportar **[VALIDAR]** |
| 8 | O mock usa 3 botões, mas o requisito pede 4 notas | Espaço na tela redonda | Grade 2×2 com botões grandes (~150 px de altura cada, em 480×480) |
| 9 | `onKey` aceita um único handler, e BACK tem ação padrão do sistema | Interceptar BACK pode prender o usuário | Botões físicos: **SELECT** = mostrar resposta / BOM; **UP** = FÁCIL; **DOWN** = ERREI; DIFÍCIL por toque. BACK nunca é interceptado. Mapeamento **[VALIDAR]** no aparelho |
| 10 | O simulador tem suporte limitado ao Side Service (discussão #478) e pode não emular o T-Rex 3 | Teste de ponta a ponta só no aparelho real | Lógica em `watch/lib` testada no Node, e roteiro manual de teste no relógio |
| 11 | Instalar exige `zeus login` com conta Zepp | Eu não posso fazer login nem digitar senhas | Você executa `zeus login` e escaneia o QR. Não está documentado se a instalação de desenvolvedor dura para sempre **[VALIDAR]** |
| 12 | Tela AMOLED | Consumo de bateria | Fundo preto puro, sem animações, `setPageBrightTime` só na tela de estudo, zero processos em segundo plano |

---

## 9. Experiência no relógio (480×480 redondo)

1. **Home**: "FLASHCARDS" · "Baralho: Inglês" · "12 cartões" · [COMEÇAR]. UP/DOWN ou swipe troca de deck; um botão pequeno leva para "Sincronizar".
2. **Frente**: "12 / 30" no topo · texto grande (36–40 px, branco sobre preto) numa área com rolagem · [MOSTRAR] grande embaixo (ou SELECT).
3. **Verso**: "12 / 30" · resposta com rolagem · grade 2×2 [ERREI][DIFÍCIL][BOM][FÁCIL].
4. **Resumo**: "SESSÃO CONCLUÍDA" · "12 cartões" · "9 acertos" (BOM+FÁCIL) · "3 difíceis" · "Tempo: 8:32".
5. **Sincronizar**: "Preparando..." → "Enviando 12 respostas" → "Recebendo 30 cartões" → "Pronto". Mensagem de erro clara quando o Zepp App ou o app Flashcards não responde.

A fila da sessão tem os cartões vencidos mais os novos até o limite diário. Um cartão
respondido com ERREI volta na mesma sessão depois de pelo menos 3 outros cartões.

---

## 10. Critério de sucesso: como cada passo será atendido

| Passo | Componente |
|---|---|
| 1–3. Abrir app, criar deck, criar/importar cartões | Android: Compose + Room + CsvImporter |
| 4. Sincronizar com o T-Rex 3 | Android SyncServer ↔ Side Service ↔ Device App (pull) |
| 5–7. Estudar offline no relógio | Device App + LocalStorage + SRS em JS |
| 8–9. Voltar e sincronizar respostas | Outbox → `/v1/reviews` (idempotente) → recálculo pelo log |
| 10. Ver progresso no Android | Tela de Estatísticas lendo `ReviewEntity` via Flow |

---

## 11. Ambiente desta máquina (verificado)

| Ferramenta | Situação |
|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`: platform `android-36.1`, build-tools `36.0.0`, platform-tools (adb) |
| JDK | OpenJDK 21.0.10 (JBR do Android Studio, `C:\Program Files\Android\Android Studio\jbr`); **fora do PATH**, então os builds vão usar `JAVA_HOME` apontando para ele |
| Gradle | Não instalado, sem cache de wrapper. O wrapper vai baixar a distribuição no primeiro build |
| Node / npm | v24.19.0 / 11.17.0 |
| Zeus CLI | `@zeppos/zeus-cli@1.9.3` instalado |
| Git | 2.55 (a pasta do projeto ainda não é repositório) |

---

## 12. Pontos para validar no relógio real

- [ ] `fetch('http://localhost:8765/v1/hello')` no Side Service chega ao app Android
- [ ] Mapeamento de `KEY_UP` / `KEY_DOWN` / `KEY_SELECT` nos 4 botões do T-Rex 3
- [ ] Renderização de acentos (ã, ç, é) e de emojis na fonte do sistema
- [ ] Tamanho máximo confortável de página no ZML (começar com 30 cartões)
- [ ] O app instalado via `zeus preview` continua no relógio depois de reiniciar

---

## Fontes

- Lista oficial de dispositivos e API_LEVEL: https://docs.zepp.com/docs/reference/related-resources/device-list/
- Arquitetura do Mini Program: https://docs.zepp.com/docs/guides/architecture/arc/
- LocalStorage: https://docs.zepp.com/docs/reference/device-app-api/newAPI/storage/localStorage/
- Persistência de dados: https://docs.zepp.com/docs/guides/best-practice/persistence-storage/
- @zos/fs openSync: https://docs.zepp.com/docs/reference/device-app-api/newAPI/fs/openSync/
- BLE (device): https://docs.zepp.com/docs/reference/device-app-api/newAPI/ble/
- Messaging (side service): https://docs.zepp.com/docs/reference/side-service-api/messaging/
- Fetch (side service): https://docs.zepp.com/docs/reference/side-service-api/fetch/
- Settings API: https://docs.zepp.com/docs/reference/side-service-api/settings/
- TextInput (Settings App): https://docs.zepp.com/docs/reference/app-settings-api/ui/textinput/
- TransferFile (side service): https://docs.zepp.com/docs/reference/side-service-api/transfer-file/
- TransferFile (device): https://docs.zepp.com/docs/reference/device-app-api/newAPI/transfer-file/TransferFile/
- onKey: https://docs.zepp.com/docs/reference/device-app-api/newAPI/interaction/onKey/
- onGesture: https://docs.zepp.com/docs/reference/device-app-api/newAPI/interaction/onGesture/
- setPageBrightTime: https://docs.zepp.com/docs/reference/device-app-api/newAPI/display/setPageBrightTime/
- TEXT: https://docs.zepp.com/docs/reference/device-app-api/newAPI/ui/widget/TEXT/
- VIEW_CONTAINER: https://docs.zepp.com/docs/reference/device-app-api/newAPI/ui/widget/VIEW_CONTAINER/
- Adaptação de tela: https://docs.zepp.com/docs/guides/best-practice/multi-screen-adaption/
- Novidades 3.0: https://docs.zepp.com/docs/guides/version-info/new-features-30/
- Novidades 4.0: https://docs.zepp.com/docs/guides/version-info/new-features-40/
- Zeus CLI: https://docs.zepp.com/docs/guides/tools/cli/
- Developer Mode: https://docs.zepp.com/docs/guides/tools/zepp-app/
- ZML: https://github.com/zepp-health/zml
- Watchdrip (localhost no side service): https://github.com/bigdigital/zeppos_watchdrip_app
- Discussão #276 (fetch localhost): https://github.com/orgs/zepp-health/discussions/276
- Discussão #478 (simulador e side service): https://github.com/orgs/zepp-health/discussions/478
- Gadgetbridge #3266: https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/3266
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Atualização do T-Rex 3 (dez/2025): https://us.amazfit.com/blogs/product-update/t-rex-3-december-2025-software-updates
