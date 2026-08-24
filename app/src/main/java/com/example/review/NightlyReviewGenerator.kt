package com.example.review

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.MetricAvailability
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

object NightlyReviewGenerator {
    private const val MINIMUM_BASELINE_DAYS = 3
    private const val RECENT_WINDOW_DAYS = 7
    private const val HISTORY_WINDOW_DAYS = 28
    private const val MINIMUM_EARLIER_DAYS = 7
    private const val EVENING_HOUR = 20
    private const val SEVEN_HOURS_IN_MINUTES = 7 * 60

    fun generate(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        recentReports: List<DayAvailabilityReport> = emptyList(),
        feeling: NightlyFeeling? = null
    ): NightlyReview {
        val history = recentReports
            .filter { it.date.isBefore(report.date) }
            .sortedByDescending { it.date }
            .take(HISTORY_WINDOW_DAYS)
        val current = SnapshotStats.from(report)
        val baselines = PersonalBaselines.from(history.take(RECENT_WINDOW_DAYS))
        val evolution = RecentEvolution.from(history)
        val localGeneratedAt = generatedAt.atZone(report.zoneId)
        val currentDay = localGeneratedAt.toLocalDate() == report.date
        val dayInProgress = currentDay && localGeneratedAt.hour < EVENING_HOUR

        val signals = Signals.from(current, baselines)
        val facts = interpretedFacts(current, baselines, signals, dayInProgress, feeling) + evolution.facts
        val limits = interpretationLimits(report, current, baselines, currentDay, dayInProgress)
        val actions = suggestedActions(current, signals, feeling).take(2)

        return NightlyReview(
            date = report.date,
            generatedAt = generatedAt,
            summary = summary(current, signals, currentDay, dayInProgress, feeling),
            facts = facts,
            gaps = limits,
            nextActions = actions
        )
    }

    private fun summary(
        current: SnapshotStats,
        signals: Signals,
        currentDay: Boolean,
        dayInProgress: Boolean,
        feeling: NightlyFeeling?
    ): String {
        val provisional = when {
            dayInProgress -> " La jornada sigue en curso: actividad y nutrición aún son provisionales."
            currentDay -> " Es una lectura provisional que se recalculará mañana con datos tardíos."
            else -> ""
        }
        return when {
            feeling == NightlyFeeling.UNWELL ->
                "Has registrado que te encuentras mal; los datos sirven como contexto, no como diagnóstico.$provisional"
            signals.sleepBelowBaseline ->
                "La señal principal es un sueño por debajo de tu referencia reciente.$provisional"
            signals.shortSleep ->
                "La señal principal es un sueño corto, sin historial suficiente para afirmar una evolución.$provisional"
            signals.recoveryLessFavourable ->
                "Las señales de recuperación se apartan de tu referencia reciente en una dirección menos favorable.$provisional"
            feeling == NightlyFeeling.LOADED ->
                "Has registrado una sensación de carga; conviene decidir mañana según cómo evolucione.$provisional"
            current.exerciseSessions.isNotEmpty() ->
                "Hay un entrenamiento real registrado; no aparece otra señal suficientemente sólida para cambiar el plan.$provisional"
            dayInProgress ->
                "La jornada sigue en curso y todavía no ofrece una señal suficientemente completa para valorarla."
            else ->
                "No aparece una desviación suficientemente sólida para cambiar el plan a partir de esta revisión."
        }.trim()
    }

    private fun interpretedFacts(
        current: SnapshotStats,
        baselines: PersonalBaselines,
        signals: Signals,
        dayInProgress: Boolean,
        feeling: NightlyFeeling?
    ): List<String> = buildList {
        feeling?.let { add("Sensación registrada: te sentías ${it.labelEs}; se usa como contexto subjetivo, no como medida clínica.") }
        current.sleepMinutes?.let { sleep ->
            val baseline = baselines.sleepMinutes
            when {
                signals.sleepBelowBaseline && baseline != null -> {
                    val difference = baseline.value.roundToInt() - sleep
                    add(
                        "Sueño: ${formatMinutes(sleep)} son ${formatMinutes(difference)} menos que tu " +
                            "referencia reciente (${formatMinutes(baseline.value.roundToInt())})."
                    )
                }
                sleep < SEVEN_HOURS_IN_MINUTES ->
                    add("Sueño: ${formatMinutes(sleep)} queda por debajo de siete horas; un solo día no define una evolución.")
                baseline != null && abs(sleep - baseline.value) <= 45 ->
                    add("Sueño: ${formatMinutes(sleep)} se mantiene cerca de tu referencia reciente.")
            }
        }

        if (signals.recoveryLessFavourable) {
            val restingDelta = baselines.restingHeartRate?.let { current.restingHeartRate?.minus(it.value) }
            val hrvDeltaPercent = baselines.hrvRmssd?.let { baseline ->
                current.hrvRmssd?.let { ((it - baseline.value) / baseline.value * 100.0).roundToInt() }
            }
            val changes = buildList {
                restingDelta?.takeIf { it >= 5.0 }?.let { add("FC en reposo +${it.roundToInt()} ppm") }
                hrvDeltaPercent?.takeIf { it <= -15 }?.let { add("HRV ${abs(it)} % menor") }
            }.joinToString(" y ")
            add(
                "Recuperación: $changes frente a tu referencia reciente apuntan a menor recuperación; " +
                    "es una señal personal orientativa, sin valor diagnóstico."
            )
        }

        if (current.exerciseSessions.isNotEmpty()) {
            val sessions = current.exerciseSessions.joinToString("; ") { session ->
                buildString {
                    append(session.label)
                    session.observation?.let { append(" · $it") }
                    if (session.coveredThrough != "Total del día") append(" · ${session.coveredThrough}")
                    append(" · origen: ${session.source}")
                }
            }
            val sessionLabel = if (current.exerciseSessions.size == 1) "1 sesión" else "${current.exerciseSessions.size} sesiones"
            val duration = current.trainingMinutes?.let { " · ${formatMinutes(it)} en total" }.orEmpty()
            add("Entrenamiento registrado: $sessionLabel$duration. Detalle: $sessions.")
        }

        if (!dayInProgress && current.steps != null && baselines.steps != null) {
            val ratio = current.steps / baselines.steps.value
            when {
                ratio < 0.7 -> add("Actividad: el movimiento del día queda claramente por debajo de tu referencia reciente.")
                ratio > 1.3 -> add("Actividad: el movimiento del día queda claramente por encima de tu referencia reciente.")
                else -> add("Actividad: el movimiento del día está cerca de tu referencia reciente.")
            }
        }
    }

    private fun interpretationLimits(
        report: DayAvailabilityReport,
        current: SnapshotStats,
        baselines: PersonalBaselines,
        currentDay: Boolean,
        dayInProgress: Boolean
    ): List<String> = buildList {
        if (currentDay) {
            add(
                if (dayInProgress) {
                    "Lectura provisional: no se juzgan actividad ni nutrición antes de que termine el día."
                } else {
                    "Lectura provisional: la exportación de mañana incorporará registros que lleguen tarde."
                }
            )
        }

        if (!baselines.hasAnyReadyBaseline()) {
            add("Evolución: hacen falta al menos 3 días comparables dentro de los 7 anteriores para afirmar cambios.")
        }

        if (current.sleepMinutes == null || (current.restingHeartRate == null && current.hrvRmssd == null)) {
            val missing = buildList {
                if (current.sleepMinutes == null) add("sueño")
                if (current.restingHeartRate == null && current.hrvRmssd == null) add("recuperación")
            }.joinToString(" y ")
            add("Confianza limitada: hoy falta contexto de $missing; ausencia de datos no equivale a un resultado negativo.")
        }

        if (current.exerciseSessions.isEmpty() && current.hasIsolatedExerciseMetrics) {
            add("Entrenamiento: Health Connect aporta métricas sueltas, pero no una sesión; no se cuentan como entrenamiento.")
        }

        val nutrition = report.domain(HealthDomain.NUTRITION)
        if (!dayInProgress && nutrition?.hasObservedData() != true) {
            add("Nutrición no evaluable: no hay registro; esto limita la revisión, pero no se interpreta como ingesta cero.")
        }
    }

    private fun suggestedActions(
        current: SnapshotStats,
        signals: Signals,
        feeling: NightlyFeeling?
    ): List<String> = buildList {
        if (feeling == NightlyFeeling.UNWELL) {
            add("Prioriza descanso y reevalúa mañana; si el malestar es importante o persiste, busca orientación profesional.")
        } else if (feeling == NightlyFeeling.LOADED) {
            add("Valora una sesión fácil o descanso mañana y comprueba si la sensación de carga mejora.")
        }
        if (signals.shortSleep || signals.sleepBelowBaseline) {
            add("Protege esta noche una ventana de sueño más amplia y comprueba mañana si la señal se corrige.")
        }

        when {
            signals.recoveryLessFavourable && current.exerciseSessions.isNotEmpty() ->
                add("Haz que la próxima sesión sea fácil o descansa si tus sensaciones coinciden con estas señales.")
            current.exerciseSessions.isNotEmpty() &&
                (current.sleepMinutes == null || (current.restingHeartRate == null && current.hrvRmssd == null)) ->
                add("Decide la intensidad de la próxima sesión según tus sensaciones; hoy faltan datos para orientarla.")
        }

        if (isEmpty()) {
            add("Mantén el plan previsto; hoy no aparece una señal suficientemente sólida para cambiarlo.")
        }
    }

    private data class Signals(
        val shortSleep: Boolean,
        val sleepBelowBaseline: Boolean,
        val recoveryLessFavourable: Boolean
    ) {
        companion object {
            fun from(current: SnapshotStats, baselines: PersonalBaselines): Signals {
                val sleepBelowBaseline = current.sleepMinutes?.let { sleep ->
                    baselines.sleepMinutes?.let { sleep <= it.value - 45.0 }
                } ?: false
                val restingElevated = current.restingHeartRate?.let { currentValue ->
                    baselines.restingHeartRate?.let { currentValue >= it.value + 5.0 }
                } ?: false
                val hrvLower = current.hrvRmssd?.let { currentValue ->
                    baselines.hrvRmssd?.let { currentValue <= it.value * 0.85 }
                } ?: false
                return Signals(
                    shortSleep = current.sleepMinutes?.let { it < SEVEN_HOURS_IN_MINUTES } == true,
                    sleepBelowBaseline = sleepBelowBaseline,
                    recoveryLessFavourable = restingElevated || hrvLower
                )
            }
        }
    }

    private data class Baseline(val value: Double, val observations: Int)

    private data class PersonalBaselines(
        val sleepMinutes: Baseline?,
        val steps: Baseline?,
        val restingHeartRate: Baseline?,
        val hrvRmssd: Baseline?
    ) {
        fun hasAnyReadyBaseline(): Boolean = listOf(sleepMinutes, steps, restingHeartRate, hrvRmssd).any { it != null }

        companion object {
            fun from(reports: List<DayAvailabilityReport>): PersonalBaselines {
                val stats = reports.map(SnapshotStats::from)
                return PersonalBaselines(
                    sleepMinutes = baseline(stats.mapNotNull { it.sleepMinutes?.toDouble() }),
                    steps = baseline(stats.mapNotNull { it.steps }),
                    restingHeartRate = baseline(stats.mapNotNull { it.restingHeartRate }),
                    hrvRmssd = baseline(stats.mapNotNull { it.hrvRmssd })
                )
            }

            private fun baseline(values: List<Double>): Baseline? {
                if (values.size < MINIMUM_BASELINE_DAYS) return null
                val sorted = values.sorted()
                val middle = sorted.size / 2
                val median = if (sorted.size % 2 == 0) {
                    (sorted[middle - 1] + sorted[middle]) / 2.0
                } else {
                    sorted[middle]
                }
                return Baseline(median, values.size)
            }
        }
    }

    private data class RecentEvolution(val facts: List<String>) {
        companion object {
            fun from(history: List<DayAvailabilityReport>): RecentEvolution {
                val recent = history.take(RECENT_WINDOW_DAYS).map(SnapshotStats::from)
                val earlier = history.drop(RECENT_WINDOW_DAYS).take(21).map(SnapshotStats::from)
                return RecentEvolution(buildList {
                    comparableMedian(recent.mapNotNull { it.sleepMinutes?.toDouble() }, earlier.mapNotNull { it.sleepMinutes?.toDouble() })
                        ?.let { (recentValue, earlierValue) ->
                            val difference = (recentValue - earlierValue).roundToInt()
                            if (abs(difference) >= 30) {
                                val direction = if (difference > 0) "más" else "menos"
                                add("Últimos 7 días: el sueño mediano es ${formatMinutes(abs(difference))} $direction que en los 21 días anteriores.")
                            }
                        }
                    comparableMedian(recent.mapNotNull { it.steps }, earlier.mapNotNull { it.steps })
                        ?.let { (recentValue, earlierValue) ->
                            if (earlierValue > 0) {
                                val percent = ((recentValue - earlierValue) / earlierValue * 100).roundToInt()
                                if (abs(percent) >= 20) {
                                    val direction = if (percent > 0) "por encima" else "por debajo"
                                    add("Últimos 7 días: la actividad mediana está ${abs(percent)} % $direction de los 21 días anteriores.")
                                }
                            }
                        }
                    comparableMedian(
                        recent.mapNotNull { it.restingHeartRate },
                        earlier.mapNotNull { it.restingHeartRate }
                    )?.let { (recentValue, earlierValue) ->
                        val difference = recentValue - earlierValue
                        if (abs(difference) >= 3) {
                            val direction = if (difference > 0) "más alta" else "más baja"
                            add("Últimos 7 días: la FC en reposo mediana está ${abs(difference).roundToInt()} ppm $direction que en los 21 días anteriores.")
                        }
                    }
                    comparableMedian(recent.mapNotNull { it.hrvRmssd }, earlier.mapNotNull { it.hrvRmssd })
                        ?.let { (recentValue, earlierValue) ->
                            if (earlierValue > 0) {
                                val percent = ((recentValue - earlierValue) / earlierValue * 100).roundToInt()
                                if (abs(percent) >= 10) {
                                    val direction = if (percent > 0) "mayor" else "menor"
                                    add("Últimos 7 días: la HRV mediana es ${abs(percent)} % $direction que en los 21 días anteriores.")
                                }
                            }
                        }
                    comparableMedian(
                        recent.mapNotNull { it.trainingMinutes?.toDouble() },
                        earlier.mapNotNull { it.trainingMinutes?.toDouble() }
                    )?.let { (recentValue, earlierValue) ->
                        val difference = (recentValue - earlierValue).roundToInt()
                        if (abs(difference) >= 10) {
                            val direction = if (difference > 0) "más" else "menos"
                            add(
                                "Últimos 7 días: entre los entrenamientos registrados, la duración mediana es " +
                                    "${formatMinutes(abs(difference))} $direction que en los 21 días anteriores."
                            )
                        }
                    }
                })
            }

            private fun comparableMedian(
                recent: List<Double>,
                earlier: List<Double>
            ): Pair<Double, Double>? {
                if (recent.size < MINIMUM_BASELINE_DAYS || earlier.size < MINIMUM_EARLIER_DAYS) return null
                return median(recent) to median(earlier)
            }

            private fun median(values: List<Double>): Double {
                val sorted = values.sorted()
                val middle = sorted.size / 2
                return if (sorted.size % 2 == 0) {
                    (sorted[middle - 1] + sorted[middle]) / 2.0
                } else {
                    sorted[middle]
                }
            }
        }
    }

    private data class SnapshotStats(
        val steps: Double?,
        val sleepMinutes: Int?,
        val restingHeartRate: Double?,
        val hrvRmssd: Double?,
        val exerciseSessions: List<MetricAvailability>,
        val trainingMinutes: Int?,
        val hasIsolatedExerciseMetrics: Boolean
    ) {
        companion object {
            fun from(report: DayAvailabilityReport): SnapshotStats {
                val exerciseMetrics = report.domain(HealthDomain.EXERCISE)
                    ?.metrics
                    .orEmpty()
                    .filter { it.status == HealthAvailabilityStatus.AVAILABLE && !it.observation.isNullOrBlank() }
                return SnapshotStats(
                    steps = report.availableMetric(HealthDomain.STEPS, "steps")?.number(),
                    sleepMinutes = report.domain(HealthDomain.SLEEP)
                        ?.metrics
                        .orEmpty()
                        .filter { it.status == HealthAvailabilityStatus.AVAILABLE && it.key.startsWith("sleep_session_") }
                        .mapNotNull { it.observation?.sleepMinutes() }
                        .takeIf { it.isNotEmpty() }
                        ?.sum(),
                    restingHeartRate = report.availableMetric(
                        HealthDomain.RESTING_HEART_RATE,
                        "resting_heart_rate"
                    )?.number(),
                    hrvRmssd = report.availableMetric(HealthDomain.RESTING_HEART_RATE, "hrv_rmssd")?.number(),
                    exerciseSessions = exerciseMetrics.filter { it.key.startsWith("exercise_session_") },
                    trainingMinutes = exerciseMetrics
                        .filter { it.key.startsWith("exercise_session_") }
                        .mapNotNull { it.observation?.sleepMinutes() }
                        .takeIf { it.isNotEmpty() }
                        ?.sum(),
                    hasIsolatedExerciseMetrics = exerciseMetrics.any { !it.key.startsWith("exercise_session_") }
                )
            }
        }
    }

    private fun DayAvailabilityReport.domain(domain: HealthDomain): DomainAvailability? =
        domains.firstOrNull { it.domain == domain }

    private fun DayAvailabilityReport.availableMetric(domain: HealthDomain, key: String): MetricAvailability? =
        domain(domain)?.metrics?.firstOrNull {
            it.key == key && it.status == HealthAvailabilityStatus.AVAILABLE && !it.observation.isNullOrBlank()
        }

    private fun DomainAvailability.hasObservedData(): Boolean =
        status == HealthAvailabilityStatus.AVAILABLE || status == HealthAvailabilityStatus.PARTIAL ||
            metrics.any { it.status == HealthAvailabilityStatus.AVAILABLE }

    private fun MetricAvailability.number(): Double? {
        val token = observation
            ?.let { Regex("""-?\d+(?:[.,]\d+)?""").find(it)?.value }
            ?: return null
        val normalized = if (key == "steps" && Regex("""\d+[.,]\d{3}""").matches(token)) {
            token.replace(".", "").replace(",", "")
        } else {
            token.replace(',', '.')
        }
        return normalized.toDoubleOrNull()
    }

    private fun String.sleepMinutes(): Int? {
        val match = Regex("""(?:(\d+)\s*h)?\s*(\d+)\s*m""").find(this) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val safeMinutes = abs(totalMinutes)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours h $minutes min"
            hours > 0 -> "$hours h"
            else -> "$minutes min"
        }
    }
}
