package com.flashcards.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.flashcards.app.FlashcardsApplication
import com.flashcards.app.di.AppContainer

/** Acesso ao [AppContainer] a partir de uma tela. Não usar em @Preview. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as FlashcardsApplication).container
