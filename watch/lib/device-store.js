import { LocalStorage } from '@zos/storage'
import { createStore } from './store.js'

/** Store real do relógio: cada arquivo é um LocalStorage (armazenamento recomendado pelo Zepp OS). */
export function createDeviceStore() {
  return createStore((name) => new LocalStorage(name))
}
