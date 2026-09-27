// Settings App: roda no celular, dentro do Zepp App (Perfil > relógio > Flashcards).
// É onde o usuário digita o código de pareamento, para não precisar de teclado no relógio.
// Os valores ficam no settingsStorage, que o side service lê.

AppSettingsPage({
  build() {
    return Section({}, [
      View({ style: { padding: '12px 16px' } }, [
        Text({}, ['Flashcards · pareamento com o celular']),
      ]),
      View({ style: { padding: '0 16px 12px' } }, [
        Text({}, ['1. No app Flashcards do celular, abra a aba "Relógio".']),
      ]),
      View({ style: { padding: '0 16px 12px' } }, [
        Text({}, ['2. Digite abaixo o código de 6 dígitos mostrado lá.']),
      ]),
      TextInput({
        label: 'Código de pareamento',
        settingsKey: 'pairToken',
        placeholder: '123456',
      }),
      TextInput({
        label: 'Endereço do app no celular (avançado)',
        settingsKey: 'serverUrl',
        placeholder: 'http://127.0.0.1:8765',
      }),
    ])
  },
})
