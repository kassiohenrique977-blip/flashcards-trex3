package com.flashcards.core.srs

/**
 * Parâmetros do [Sm2Scheduler]. Os valores padrão são os mesmos de
 * `shared/srs-test-vectors.json` e do relógio.
 */
data class SchedulerConfig(
    /** Etapas de aprendizado de um cartão novo, em minutos. */
    val learningStepsMinutes: List<Double> = listOf(1.0, 10.0),
    /** Etapas depois de esquecer um cartão em revisão, em minutos. Vazio = volta direto para revisão. */
    val relearningStepsMinutes: List<Double> = listOf(10.0),
    val graduatingIntervalDays: Int = 1,
    val easyIntervalDays: Int = 4,
    val startingEase: Double = 2.5,
    val minimumEase: Double = 1.3,
    val easyBonus: Double = 1.3,
    val hardMultiplier: Double = 1.2,
    /** Fração do intervalo mantida depois de um esquecimento (0 = recomeça do mínimo). */
    val lapseIntervalMultiplier: Double = 0.0,
    val minimumLapseIntervalDays: Int = 1,
    val maximumIntervalDays: Int = 36_500,
) {
    init {
        require(learningStepsMinutes.isNotEmpty()) { "É preciso ao menos uma etapa de aprendizado." }
        require(learningStepsMinutes.all { it > 0 } && relearningStepsMinutes.all { it > 0 }) {
            "As etapas precisam ser positivas."
        }
        require(graduatingIntervalDays >= 1) { "O intervalo de graduação precisa ser de pelo menos 1 dia." }
        require(easyIntervalDays >= graduatingIntervalDays) {
            "O intervalo fácil não pode ser menor que o de graduação."
        }
        require(minimumEase > 0 && startingEase >= minimumEase) { "Ease inicial abaixo do mínimo." }
        require(easyBonus >= 1 && hardMultiplier >= 1) { "Multiplicadores precisam ser pelo menos 1." }
        require(lapseIntervalMultiplier in 0.0..1.0) { "O multiplicador de esquecimento deve estar entre 0 e 1." }
        require(minimumLapseIntervalDays >= 1) { "O intervalo mínimo após esquecer é de 1 dia." }
        require(maximumIntervalDays >= easyIntervalDays) { "O intervalo máximo é menor que o fácil." }
    }
}
