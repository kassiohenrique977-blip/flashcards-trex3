import { createWidget, deleteWidget, getTextLayout, widget, align, text_style, prop } from '@zos/ui'
import { px } from '@zos/utils'

// Coordenadas no espaço de design 480×480 (tela redonda); px() escala para o aparelho.
// Fundo preto puro: na tela AMOLED, pixel preto é pixel desligado.

export const COLOR = {
  text: 0xffffff,
  muted: 0x9a9a9a,
  accent: 0x7fb3ff,
  primary: 0x2f5fa8,
  neutral: 0x333333,
  again: 0xe5484d,
  hard: 0xf5a524,
  good: 0x30a46c,
  easy: 0x3e7bfa,
  dark: 0x000000,
}

export function label({ x, y, w, h, text = '', size = 32, color = COLOR.text, wrap = false }) {
  return createWidget(widget.TEXT, {
    x: px(x),
    y: px(y),
    w: px(w),
    h: px(h),
    text,
    text_size: px(size),
    color,
    align_h: align.CENTER_H,
    align_v: align.CENTER_V,
    text_style: wrap ? text_style.WRAP : text_style.ELLIPSIS,
  })
}

export function button({ x, y, w, h, text, size = 30, color, textColor = COLOR.text, onClick }) {
  return createWidget(widget.BUTTON, {
    x: px(x),
    y: px(y),
    w: px(w),
    h: px(h),
    radius: px(Math.floor(h / 2)),
    normal_color: color,
    press_color: darken(color),
    text,
    text_size: px(size),
    color: textColor,
    click_func: () => onClick(),
  })
}

/**
 * Texto com quebra de linha dentro de uma área com rolagem. Textos curtos ficam
 * centralizados; longos rolam com o dedo.
 */
export function scrollingText({ x, y, w, h, text, size = 36, color = COLOR.text }) {
  const width = px(w)
  const height = px(h)
  const textSize = px(size)
  const layout = getTextLayout(text, { text_size: textSize, text_width: width, wrapped: 1 })
  const textHeight = Math.max(layout.height, textSize) + px(8)
  const container = createWidget(widget.VIEW_CONTAINER, {
    x: px(x),
    y: px(y),
    w: width,
    h: height,
    scroll_enable: textHeight > height ? 1 : 0,
  })
  container.createWidget(widget.TEXT, {
    x: 0,
    y: textHeight < height ? Math.floor((height - textHeight) / 2) : 0,
    w: width,
    h: textHeight,
    text,
    text_size: textSize,
    color,
    align_h: align.CENTER_H,
    align_v: align.TOP,
    text_style: text_style.WRAP,
  })
  return container
}

export function setText(target, text) {
  target.setProperty(prop.TEXT, text)
}

/** Apaga todos os widgets da lista e a esvazia. */
export function removeAll(widgets) {
  while (widgets.length > 0) deleteWidget(widgets.pop())
}

function darken(color) {
  const r = Math.round(((color >> 16) & 0xff) * 0.7)
  const g = Math.round(((color >> 8) & 0xff) * 0.7)
  const b = Math.round((color & 0xff) * 0.7)
  return (r << 16) | (g << 8) | b
}
