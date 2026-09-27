// Textos do app do relógio. O inglês mora aqui e é a base: se o relógio estiver num
// idioma sem tradução (ou faltar uma chave no .po), aparece o inglês, nunca a chave crua.
// As traduções ficam em page/i18n/<idioma>.po e chegam pelo getText do @zos/i18n.

export const ENGLISH = {
  'home.preparing': 'Loading...',
  'home.deckPrefix': 'Deck: ',
  'home.empty': 'No decks yet.\nSync with your phone.',
  'home.start': 'START',
  'home.allDone': 'All caught up!',
  'home.swipe': 'swipe',
  'home.sync': 'SYNC',
  'home.howTo': 'HOW IT WORKS',

  'card.one': 'card',
  'card.many': 'cards',

  'study.show': 'SHOW',
  'study.again': 'AGAIN',
  'study.hard': 'HARD',
  'study.good': 'GOOD',
  'study.easy': 'EASY',

  'summary.title': 'SESSION DONE',
  'summary.hit.one': 'correct',
  'summary.hit.many': 'correct',
  'summary.hard.one': 'hard',
  'summary.hard.many': 'hard',
  'summary.miss.one': 'miss',
  'summary.miss.many': 'misses',
  'summary.time': 'Time: ',
  'summary.ok': 'OK',

  'sync.title': 'SYNC',
  'sync.connecting': 'Connecting...',
  'sync.sending': 'Sending answers',
  'sync.receiving': 'Receiving cards...',
  'sync.finishing': 'Finishing...',
  'sync.done': 'Synced!',
  'sync.failed': 'Not synced',
  'sync.sent': 'Sent: ',
  'sync.received': 'Received: ',
  'sync.ok': 'OK',
  'sync.retry': 'RETRY',
  'sync.back': 'BACK',
  'answer.one': 'answer',
  'answer.many': 'answers',
  'change.one': 'change',
  'change.many': 'changes',

  'error.unauthorized': 'Wrong pairing code. Check it in the Zepp App: Flashcards > Settings.',
  'error.noServer': 'Open the Flashcards app on your phone, on the Watch tab, and try again.',
  'error.protocol': 'Different versions. Update the phone and watch apps.',
  'error.pushRejected': 'The phone refused the answers. Try again.',
  'error.badResponse': 'Invalid answer from the phone. Try again.',
  'error.generic': 'No connection to the phone. Check Bluetooth and the Zepp App.',

  'tour.welcome.title': 'FLASHCARDS',
  'tour.welcome.text': 'Study your cards on the watch, offline, without carrying your phone.',
  'tour.how.title': 'HOW IT WORKS',
  'tour.how.text': '1. Create or import cards in the phone app.\n2. Tap SYNC.\n3. Study here, offline.',
  'tour.study.title': 'STUDYING',
  'tour.study.text': 'Tap SHOW and answer: AGAIN, HARD, GOOD or EASY. Your answer sets when the card comes back.',
  'tour.back.title': 'BACK TO THE PHONE',
  'tour.back.text': 'Your answers stay here until you sync again. Nothing is lost.',
  'tour.qr.title': 'PHONE APP',
  'tour.qr.text': 'Scan the code with your phone camera to install:',
  'tour.confirm.title': 'ALL SET?',
  'tour.confirm.text': 'Have you installed the Flashcards app on your phone?',
  'tour.next': 'NEXT',
  'tour.yes': 'YES, START',
  'tour.notYet': 'NOT YET',

  'demo.name': 'Demo',
  'demo.q1': 'What is this app for?',
  'demo.a1': 'Studying flashcards on the watch, offline',
  'demo.q2': 'How do cards get to the watch?',
  'demo.a2': 'From the phone app, by tapping SYNC',
  'demo.q3': 'What does AGAIN mean?',
  'demo.a3': 'You forgot the card: it comes back in this session',
  'demo.q4': 'What does EASY mean?',
  'demo.a4': 'It was effortless: the card comes back much later',
  'demo.q5': 'Do you need internet to study?',
  'demo.a5': 'No. Everything works offline on the watch',
  'demo.q6': 'Where do your answers go?',
  'demo.a6': 'They stay on the watch until the next sync',
  'demo.q7': 'What is spaced repetition?',
  'demo.a7': 'Reviewing right before you would forget',
  'demo.q8': 'Which button shows the answer?',
  'demo.a8': 'SHOW, or the select button on the watch',
}

/**
 * Cria a função de texto do app.
 * @param lookup função do sistema (getText). Pode faltar, devolver vazio ou a própria chave.
 */
export function makeText(lookup) {
  return (key) => {
    if (lookup) {
      try {
        const traduzido = lookup(key)
        if (traduzido && traduzido !== key) return quebrasDeLinha(traduzido)
      } catch (e) {
        // sem i18n disponível: cai no inglês
      }
    }
    return ENGLISH[key] !== undefined ? ENGLISH[key] : key
  }
}

// Rede de segurança: se o leitor de .po do sistema entregar a barra e o "n" em vez da
// quebra de linha, o texto ainda aparece em duas linhas na tela.
function quebrasDeLinha(texto) {
  const escape = String.fromCharCode(92) + 'n'
  return texto.indexOf(escape) < 0 ? texto : texto.split(escape).join('\n')
}

/** Texto só em inglês, para testes e para quem não tem o @zos/i18n. */
export const englishText = makeText(null)
