package com.flashcards.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        ReviewEntity::class,
        SyncStateEntity::class,
        WatchDeviceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FlashcardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    abstract fun reviewDao(): ReviewDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun watchDeviceDao(): WatchDeviceDao

    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "flashcards.db"

        fun build(context: Context): FlashcardsDatabase =
            Room.databaseBuilder(context, FlashcardsDatabase::class.java, NAME).build()
    }
}
