package com.flashcards.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DeckSummaryRow(
    @Embedded val deck: DeckEntity,
    val cardCount: Int,
    val dueCount: Int,
    val newCount: Int,
)

@Dao
interface DeckDao {

    @Query(
        """
        SELECT d.*,
            COUNT(c.id) AS cardCount,
            COALESCE(SUM(CASE WHEN c.state != 'NEW' AND c.dueAt <= :now THEN 1 ELSE 0 END), 0) AS dueCount,
            COALESCE(SUM(CASE WHEN c.state = 'NEW' THEN 1 ELSE 0 END), 0) AS newCount
        FROM decks d
        LEFT JOIN cards c ON c.deckId = d.id
        GROUP BY d.id
        ORDER BY d.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(now: Long): Flow<List<DeckSummaryRow>>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observe(id: String): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun get(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<DeckEntity>

    @Insert
    suspend fun insert(deck: DeckEntity)

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC, dueAt DESC")
    fun observeByDeck(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getByDeck(deckId: String): List<CardEntity>

    @Query("SELECT id FROM cards WHERE deckId = :deckId ORDER BY dueAt")
    suspend fun getIdsByDeck(deckId: String): List<String>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CardEntity>

    @Insert
    suspend fun insert(card: CardEntity)

    @Insert
    suspend fun insertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: String): Int
}

@Dao
interface ReviewDao {

    /** Retorna -1 quando o ID já existe: reenviar uma revisão não a duplica. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(review: ReviewEntity): Long

    @Query("SELECT * FROM reviews WHERE cardId = :cardId ORDER BY reviewedAt, id")
    suspend fun getForCard(cardId: String): List<ReviewEntity>

    @Query("SELECT id FROM reviews WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    /** Cartões novos apresentados pela primeira vez desde [since] (para o limite diário). */
    @Query("SELECT COUNT(*) FROM reviews WHERE deckId = :deckId AND previousState = 'NEW' AND reviewedAt >= :since")
    suspend fun countNewIntroducedSince(deckId: String, since: Long): Int

    /** Revisões de cartões já aprendidos desde [since] (para o limite diário). */
    @Query("SELECT COUNT(*) FROM reviews WHERE deckId = :deckId AND previousState = 'REVIEW' AND reviewedAt >= :since")
    suspend fun countReviewsSince(deckId: String, since: Long): Int

    @Query("DELETE FROM reviews WHERE cardId = :cardId")
    suspend fun deleteByCard(cardId: String): Int

    @Query("DELETE FROM reviews WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: String): Int
}

@Dao
interface SyncStateDao {

    @Query("SELECT COALESCE(MAX(version), 0) FROM sync_state")
    suspend fun maxVersion(): Long

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<SyncStateEntity>)

    @Query("SELECT * FROM sync_state WHERE entityType = :type AND entityId = :id")
    suspend fun get(type: String, id: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE version > :since ORDER BY version LIMIT :limit")
    suspend fun changesSince(since: Long, limit: Int): List<SyncStateEntity>

    @Query("DELETE FROM sync_state WHERE entityType = 'CARD' AND parentId = :deckId")
    suspend fun deleteCardStatesOfDeck(deckId: String): Int

    @Query("UPDATE sync_state SET syncStatus = 'SYNCED' WHERE version <= :upTo AND syncStatus = 'PENDING'")
    suspend fun markSyncedUpTo(upTo: Long): Int

    @Query("SELECT COUNT(*) FROM sync_state WHERE syncStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface WatchDeviceDao {

    @Query("SELECT * FROM watch_devices WHERE deviceId = :deviceId")
    suspend fun get(deviceId: String): WatchDeviceEntity?

    @Upsert
    suspend fun upsert(device: WatchDeviceEntity)

    @Query("SELECT * FROM watch_devices ORDER BY lastSyncAt DESC")
    fun observeAll(): Flow<List<WatchDeviceEntity>>
}
