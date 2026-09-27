package com.flashcards.app

import android.app.Application
import com.flashcards.app.di.AppContainer

class FlashcardsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
