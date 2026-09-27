import { STATES } from './srs.js'

// No relógio os cartões ficam num formato compacto (chaves curtas), para caber
// mais cartões na memória e reduzir o tamanho das mensagens BLE:
//   { i: id, f: frente, b: verso, s: código do estado, d: dueAt, iv: intervalo (dias),
//     ef: ease, r: repetições, l: esquecimentos, ls: etapa, lr: última revisão (0 = nunca) }

/** Estado de agendamento no formato do srs.js. */
export function schedulingOf(card) {
  return {
    state: STATES[card.s],
    dueAt: card.d,
    intervalDays: card.iv,
    easeFactor: card.ef,
    repetitions: card.r,
    lapses: card.l,
    learningStep: card.ls,
    lastReviewedAt: card.lr || null,
  }
}

export function withScheduling(card, s) {
  return Object.assign({}, card, {
    s: STATES.indexOf(s.state),
    d: s.dueAt,
    iv: s.intervalDays,
    ef: s.easeFactor,
    r: s.repetitions,
    l: s.lapses,
    ls: s.learningStep,
    lr: s.lastReviewedAt || 0,
  })
}

/** Cartão recebido do celular (mudança do tipo "c") para o formato guardado. */
export function cardFromChange(change) {
  return {
    i: change.id,
    f: change.f,
    b: change.b,
    s: change.s,
    d: change.d,
    iv: change.iv,
    ef: change.ef,
    r: change.r,
    l: change.l,
    ls: change.ls,
    lr: change.lr || 0,
  }
}
