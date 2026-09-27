package com.flashcards.app.di

import android.content.Context
import android.util.Log
import com.flashcards.app.data.db.FlashcardsDatabase
import com.flashcards.app.data.files.DocumentReader
import com.flashcards.app.data.files.IncomingFiles
import com.flashcards.app.data.repository.RoomCardRepository
import com.flashcards.app.data.repository.RoomDeckRepository
import com.flashcards.app.data.repository.RoomStatsRepository
import com.flashcards.app.data.repository.RoomStudyRepository
import com.flashcards.core.repository.StatsRepository
import com.flashcards.core.stats.StatisticsLoader
import com.flashcards.app.data.settings.PreferencesSettingsRepository
import com.flashcards.app.data.sync.RoomSyncBackend
import com.flashcards.app.data.sync.RoomWatchRepository
import com.flashcards.app.sync.AndroidSyncControl
import com.flashcards.app.sync.PairingStore
import com.flashcards.app.sync.SyncControl
import com.flashcards.app.sync.WatchSyncMonitor
import com.flashcards.core.repository.CardRepository
import com.flashcards.core.repository.DeckRepository
import com.flashcards.core.repository.SettingsRepository
import com.flashcards.core.repository.StudyRepository
import com.flashcards.core.repository.WatchRepository
import com.flashcards.core.srs.Scheduler
import com.flashcards.core.srs.SchedulerConfig
import com.flashcards.core.srs.Sm2Scheduler
import com.flashcards.core.sync.PairingAuth
import com.flashcards.core.sync.SyncBackend
import com.flashcards.core.sync.SyncServer
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator
import com.flashcards.core.util.SystemClock
import com.flashcards.core.util.UuidGenerator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.ZoneId

/**
 * Injeção de dependências manual. Cada dependência é criada uma vez, no escopo
 * do processo, e só quando for usada pela primeira vez.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val clock: Clock = SystemClock

    val idGenerator: IdGenerator = UuidGenerator

    /** Parâmetros do algoritmo; os mesmos vão para o relógio em cada sincronização. */
    val schedulerConfig = SchedulerConfig()

    /** Trocar aqui para usar outro algoritmo de repetição espaçada. */
    val scheduler: Scheduler = Sm2Scheduler(schedulerConfig)

    /**
     * Escopo do processo para gravações que não podem ser canceladas quando uma tela
     * fecha (por exemplo, a última resposta de uma sessão).
     */
    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Falha em tarefa em segundo plano", error)
        },
    )

    val database: FlashcardsDatabase by lazy { FlashcardsDatabase.build(appContext) }

    val deckRepository: DeckRepository by lazy { RoomDeckRepository(database, clock, idGenerator) }

    val cardRepository: CardRepository by lazy { RoomCardRepository(database, clock, idGenerator) }

    val studyRepository: StudyRepository by lazy { RoomStudyRepository(database, clock) }

    val settingsRepository: SettingsRepository by lazy { PreferencesSettingsRepository(appContext) }

    val documentReader: DocumentReader by lazy { DocumentReader(appContext) }

    val incomingFiles = IncomingFiles()

    val statsRepository: StatsRepository by lazy { RoomStatsRepository(database) }

    val statisticsLoader: StatisticsLoader by lazy { StatisticsLoader(statsRepository, deckRepository, studyRepository) }

    // --- Sincronização com o relógio ---

    val pairingStore: PairingStore by lazy { PairingStore(appContext) }

    val syncMonitor: WatchSyncMonitor by lazy { WatchSyncMonitor(clock) }

    val watchRepository: WatchRepository by lazy { RoomWatchRepository(database) }

    val syncControl: SyncControl by lazy { AndroidSyncControl(appContext) }

    val syncBackend: SyncBackend by lazy {
        RoomSyncBackend(database, scheduler, schedulerConfig, settingsRepository, { pairingStore.serverId }, clock)
    }

    val syncServer: SyncServer by lazy {
        SyncServer(
            backend = syncBackend,
            auth = PairingAuth({ pairingStore.code.value }, clock),
            events = syncMonitor::onEvent,
        )
    }

    /** Fuso atual do aparelho; lido a cada uso porque o usuário pode viajar. */
    fun zone(): ZoneId = ZoneId.systemDefault()

    private companion object {
        const val TAG = "Flashcards"
    }
}
