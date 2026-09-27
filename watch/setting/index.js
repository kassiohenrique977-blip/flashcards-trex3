// Settings App: roda no celular, dentro do Zepp App (Perfil > relógio > Flashcards).
// É onde o usuário digita o código de pareamento, para não precisar de teclado no relógio.
// Os valores ficam no settingsStorage, que o side service lê.
//
// A documentação do Zepp OS só descreve i18n para o app do relógio (page/i18n/*.po);
// para o Settings App não existe API documentada, então cada linha aparece em inglês
// (idioma padrão do app) e em português, em vez de inventar uma tradução que não existe.

const EN_PT = (en, pt) => en + ' / ' + pt

AppSettingsPage({
  build() {
    return Section({}, [
      View({ style: { padding: '12px 16px' } }, [
        Text({}, ['Flashcards · pairing with your phone']),
      ]),
      View({ style: { padding: '0 16px 12px' } }, [
        Text({}, ['1. In the Flashcards phone app, open the "Watch" tab.']),
      ]),
      View({ style: { padding: '0 16px 12px' } }, [
        Text({}, ['2. Type the 6-digit code shown there in the field below.']),
      ]),
      View({ style: { padding: '0 16px 12px' } }, [
        Text({}, ['1. No app Flashcards do celular, abra a aba "Relógio".']),
      ]),
      View({ style: { padding: '0 16px 16px' } }, [
        Text({}, ['2. Digite abaixo o código de 6 dígitos mostrado lá.']),
      ]),
      TextInput({
        label: EN_PT('Pairing code', 'Código de pareamento'),
        settingsKey: 'pairToken',
        placeholder: '123456',
      }),
      TextInput({
        label: EN_PT('Phone app address (advanced)', 'Endereço do app no celular (avançado)'),
        settingsKey: 'serverUrl',
        placeholder: 'http://127.0.0.1:8765',
      }),
    ])
  },
})
