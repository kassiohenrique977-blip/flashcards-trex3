package com.flashcards.core.srs

import com.flashcards.core.model.CardState
import com.flashcards.core.model.Rating
import com.flashcards.core.model.SchedulingState
import com.flashcards.core.srs.TimeUnits.DAY_MS
import com.flashcards.core.srs.TimeUnits.MINUTE_MS
import kotlin.math.max
import kotlin.math.min

/**
 * Implementação própria inspirada no SM-2 (Wozniak), com etapas de aprendizado em minutos.
 *
 * - NEW/LEARNING: percorre [SchedulerConfig.learningStepsMinutes]; BOM na última etapa gradua
 *   para REVIEW com o intervalo de graduação; FÁCIL gradua direto com o intervalo fácil.
 * - REVIEW: DIFÍCIL = max(iv+1, iv×1,2); BOM = max(difícil+1, (iv + atraso/2)×ease);
 *   FÁCIL = max(bom+1, (iv + atraso)×ease×bônus). ERREI é um esquecimento: vai para RELEARNING.
 * - RELEARNING: percorre as etapas de reaprendizado e volta para REVIEW.
 *
 * A ease é arredondada a duas casas depois de cada ajuste, e as contas seguem a mesma ordem
 * da versão JS do relógio, para que as duas produzam resultados idênticos.
 */
class Sm2Scheduler(private val config: SchedulerConfig = SchedulerConfig()) : Scheduler {

    override val id: String = ID

    override fun schedule(current: SchedulingState, rating: Rating, now: Long): SchedulingState {
        val next = when (current.state) {
            CardState.NEW -> learning(
                current.copy(easeFactor = config.startingEase, learningStep = 0, intervalDays = 0),
                rating,
                now,
            )
            CardState.LEARNING -> learning(current, rating, now)
            CardState.REVIEW -> review(current, rating, now)
            CardState.RELEARNING -> relearning(current, rating, now)
        }
        return next.copy(lastReviewedAt = now)
    }

    private fun learning(s: SchedulingState, rating: Rating, now: Long): SchedulingState {
        val steps = config.learningStepsMinutes
        val step = s.learningStep.coerceIn(0, steps.lastIndex)
        return when (rating) {
            Rating.AGAIN -> s.copy(
                state = CardState.LEARNING,
                learningStep = 0,
                intervalDays = 0,
                dueAt = now + minutes(steps[0]),
            )
            Rating.HARD -> s.copy(
                state = CardState.LEARNING,
                learningStep = step,
                intervalDays = 0,
                dueAt = now + minutes(hardDelay(steps, step)),
            )
            Rating.GOOD -> if (step + 1 < steps.size) {
                s.copy(
                    state = CardState.LEARNING,
                    learningStep = step + 1,
                    intervalDays = 0,
                    dueAt = now + minutes(steps[step + 1]),
                )
            } else {
                graduate(s, config.graduatingIntervalDays, now)
            }
            Rating.EASY -> graduate(s, config.easyIntervalDays, now)
        }
    }

    private fun review(s: SchedulingState, rating: Rating, now: Long): SchedulingState {
        val interval = max(1, s.intervalDays)
        val ease = s.easeFactor
        val daysLate = max(0L, (now - s.dueAt) / DAY_MS).toInt()

        val hardRaw = max(interval + 1, round(interval * config.hardMultiplier))
        val goodRaw = max(hardRaw + 1, round((interval + daysLate / 2.0) * ease))
        val easyRaw = max(goodRaw + 1, round((interval + daysLate) * ease * config.easyBonus))

        return when (rating) {
            Rating.AGAIN -> lapse(s, interval, now)
            Rating.HARD -> reviewed(s, clampInterval(hardRaw), adjustEase(ease - HARD_EASE_PENALTY), now)
            Rating.GOOD -> reviewed(s, clampInterval(goodRaw), ease, now)
            Rating.EASY -> reviewed(s, clampInterval(easyRaw), adjustEase(ease + EASY_EASE_BONUS), now)
        }
    }

    private fun lapse(s: SchedulingState, interval: Int, now: Long): SchedulingState {
        val lapseInterval = clampInterval(
            max(config.minimumLapseIntervalDays, round(interval * config.lapseIntervalMultiplier)),
        )
        val lapsed = s.copy(
            easeFactor = adjustEase(s.easeFactor - AGAIN_EASE_PENALTY),
            lapses = s.lapses + 1,
            repetitions = 0,
            intervalDays = lapseInterval,
            learningStep = 0,
        )
        val steps = config.relearningStepsMinutes
        return if (steps.isEmpty()) {
            lapsed.copy(state = CardState.REVIEW, dueAt = now + days(lapseInterval))
        } else {
            lapsed.copy(state = CardState.RELEARNING, dueAt = now + minutes(steps[0]))
        }
    }

    private fun relearning(s: SchedulingState, rating: Rating, now: Long): SchedulingState {
        val steps = config.relearningStepsMinutes
        val interval = clampInterval(max(1, s.intervalDays))
        // Sem etapas (a configuração mudou no meio do caminho): volta direto para revisão.
        if (steps.isEmpty()) return backToReview(s, interval, now)

        val step = s.learningStep.coerceIn(0, steps.lastIndex)
        return when (rating) {
            Rating.AGAIN -> s.copy(learningStep = 0, dueAt = now + minutes(steps[0]))
            Rating.HARD -> s.copy(learningStep = step, dueAt = now + minutes(hardDelay(steps, step)))
            Rating.GOOD -> if (step + 1 < steps.size) {
                s.copy(learningStep = step + 1, dueAt = now + minutes(steps[step + 1]))
            } else {
                backToReview(s, interval, now)
            }
            Rating.EASY -> backToReview(s, clampInterval(interval + 1), now)
        }
    }

    private fun graduate(s: SchedulingState, intervalDays: Int, now: Long) = s.copy(
        state = CardState.REVIEW,
        learningStep = 0,
        intervalDays = intervalDays,
        repetitions = s.repetitions + 1,
        dueAt = now + days(intervalDays),
    )

    private fun backToReview(s: SchedulingState, intervalDays: Int, now: Long) = s.copy(
        state = CardState.REVIEW,
        learningStep = 0,
        intervalDays = intervalDays,
        repetitions = s.repetitions + 1,
        dueAt = now + days(intervalDays),
    )

    private fun reviewed(s: SchedulingState, intervalDays: Int, ease: Double, now: Long) = s.copy(
        state = CardState.REVIEW,
        learningStep = 0,
        intervalDays = intervalDays,
        easeFactor = ease,
        repetitions = s.repetitions + 1,
        dueAt = now + days(intervalDays),
    )

    /** DIFÍCIL na primeira etapa usa a média das duas primeiras; nas demais, repete a etapa. */
    private fun hardDelay(steps: List<Double>, step: Int): Double =
        if (step == 0 && steps.size > 1) (steps[0] + steps[1]) / 2.0 else steps[step]

    private fun adjustEase(value: Double): Double = max(config.minimumEase, Math.round(value * 100) / 100.0)

    private fun clampInterval(days: Int): Int = min(days, config.maximumIntervalDays)

    private fun minutes(value: Double): Long = Math.round(value * MINUTE_MS)

    private fun days(value: Int): Long = value * DAY_MS

    private fun round(value: Double): Int = Math.round(value).toInt()

    companion object {
        const val ID = "sm2-v1"
        private const val AGAIN_EASE_PENALTY = 0.20
        private const val HARD_EASE_PENALTY = 0.15
        private const val EASY_EASE_BONUS = 0.15
    }
}
