package com.flashcards.core.study

import com.flashcards.core.model.Card
import com.flashcards.core.model.Rating
import com.flashcards.core.model.Review
import com.flashcards.core.model.ReviewSource
import com.flashcards.core.srs.Scheduler
import com.flashcards.core.srs.reviewIntervalDays
import com.flashcards.core.util.Clock
import com.flashcards.core.util.IdGenerator

data class AnswerResult(
    /** Revisão a ser gravada. */
    val review: Review,
    /** Cartão com o novo estado de agendamento. */
    val card: Card,
    /** O cartão continua em etapas curtas e volta mais adiante nesta sessão. */
    val requeued: Boolean,
)

data class SessionSummary(
    val cardsStudied: Int,
    val answers: Int,
    /** Respostas BOM e FÁCIL. */
    val correct: Int,
    val hard: Int,
    val again: Int,
    val durationMs: Long,
)

/**
 * Controla uma sessão de estudo: qual cartão mostrar, o que acontece a cada resposta
 * e o resumo final. Não grava nada: quem usa persiste o [AnswerResult].
 *
 * Um cartão que continua em etapas curtas depois da resposta (por exemplo, ERREI) volta
 * para a fila depois de [requeueGap] outros cartões, ou no fim se sobrarem menos.
 */
class StudySession(
    cards: List<Card>,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val source: ReviewSource,
    private val deviceId: String? = null,
    private val requeueGap: Int = DEFAULT_REQUEUE_GAP,
) {
    val sessionId: String = ids.newId()

    private val queue = ArrayDeque(cards.distinctBy { it.id })

    /** Cartões distintos planejados para a sessão. */
    val total: Int = queue.size

    private val studied = LinkedHashSet<String>()
    private val startedAt = clock.now()
    private var shownAt = startedAt
    private var finishedAt: Long? = if (queue.isEmpty()) startedAt else null
    private var answers = 0
    private var correct = 0
    private var hard = 0
    private var again = 0

    val current: Card?
        get() = queue.firstOrNull()

    val isFinished: Boolean
        get() = queue.isEmpty()

    /** Número do cartão atual, para mostrar "12 / 30". Um cartão que volta mantém seu número. */
    val position: Int
        get() {
            val card = current ?: return total
            val number = studied.size + if (card.id in studied) 0 else 1
            return number.coerceAtMost(total)
        }

    fun answer(rating: Rating): AnswerResult {
        val card = queue.removeFirstOrNull() ?: throw IllegalStateException("A sessão já terminou.")
        val now = clock.now()
        val before = card.scheduling
        val after = scheduler.schedule(before, rating, now)
        val updated = card.copy(scheduling = after)

        val review = Review(
            id = ids.newId(),
            cardId = card.id,
            deckId = card.deckId,
            rating = rating,
            reviewedAt = now,
            previousInterval = before.reviewIntervalDays,
            newInterval = after.reviewIntervalDays,
            previousState = before.state,
            newState = after.state,
            durationMs = (now - shownAt).coerceAtLeast(0),
            source = source,
            deviceId = deviceId,
            sessionId = sessionId,
            algorithm = scheduler.id,
        )

        val requeued = after.state.inSteps
        if (requeued) queue.add(minOf(requeueGap, queue.size), updated)

        studied += card.id
        answers++
        when (rating) {
            Rating.AGAIN -> again++
            Rating.HARD -> hard++
            Rating.GOOD, Rating.EASY -> correct++
        }
        shownAt = now
        if (queue.isEmpty()) finishedAt = now
        return AnswerResult(review, updated, requeued)
    }

    fun summary(): SessionSummary = SessionSummary(
        cardsStudied = studied.size,
        answers = answers,
        correct = correct,
        hard = hard,
        again = again,
        durationMs = (finishedAt ?: clock.now()) - startedAt,
    )

    companion object {
        const val DEFAULT_REQUEUE_GAP = 3
    }
}
