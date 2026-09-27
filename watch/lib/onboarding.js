// Roteiro da primeira abertura do app no relógio: explica o funcionamento, mostra o
// QR code do app do celular e termina com uma pergunta de confirmação.
// `kind` diz como a página desenha o passo: texto, QR code ou pergunta.
// Os textos são chaves de tradução (lib/strings.js e page/i18n/*.po).

export const TUTORIAL_STEPS = [
  { kind: 'text', title: 'tour.welcome.title', text: 'tour.welcome.text' },
  { kind: 'text', title: 'tour.how.title', text: 'tour.how.text' },
  { kind: 'text', title: 'tour.study.title', text: 'tour.study.text' },
  { kind: 'text', title: 'tour.back.title', text: 'tour.back.text' },
  { kind: 'qr', title: 'tour.qr.title', text: 'tour.qr.text' },
  { kind: 'confirm', title: 'tour.confirm.title', text: 'tour.confirm.text' },
]

export function isLastStep(index) {
  return index >= TUTORIAL_STEPS.length - 1
}

export function nextStep(index) {
  return Math.min(index + 1, TUTORIAL_STEPS.length - 1)
}

/** Índice da tela do QR code, para onde volta quem responde "ainda não". */
export const QR_STEP = TUTORIAL_STEPS.findIndex((step) => step.kind === 'qr')
