// Roteiro da primeira abertura do app no relógio: explica o funcionamento, mostra o
// QR code do app do celular e termina com uma pergunta de confirmação.
// `kind` diz como a página desenha o passo: texto, QR code ou pergunta.

export const TUTORIAL_STEPS = [
  {
    kind: 'text',
    title: 'FLASHCARDS',
    text: 'Estude seus cartões no relógio, sem internet e sem levar o celular.',
  },
  {
    kind: 'text',
    title: 'COMO FUNCIONA',
    text: '1. Crie ou importe cartões no app do celular.\n2. Toque em SINCRONIZAR.\n3. Estude aqui, offline.',
  },
  {
    kind: 'text',
    title: 'ESTUDANDO',
    text: 'Toque em MOSTRAR e responda: ERREI, DIFÍCIL, BOM ou FÁCIL. Cada resposta define quando o cartão volta.',
  },
  {
    kind: 'text',
    title: 'DE VOLTA AO CELULAR',
    text: 'Suas respostas ficam guardadas aqui até você sincronizar de novo. Nada se perde.',
  },
  {
    kind: 'qr',
    title: 'APP DO CELULAR',
    text: 'Leia o código com a câmera do celular para instalar:',
  },
  {
    kind: 'confirm',
    title: 'TUDO PRONTO?',
    text: 'Você já instalou o app Flashcards no celular?',
  },
]

export function isLastStep(index) {
  return index >= TUTORIAL_STEPS.length - 1
}

export function nextStep(index) {
  return Math.min(index + 1, TUTORIAL_STEPS.length - 1)
}

/** Índice da tela do QR code, para onde volta quem responde "ainda não". */
export const QR_STEP = TUTORIAL_STEPS.findIndex((step) => step.kind === 'qr')
