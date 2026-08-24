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
    private const val EVENING_HOUR = 20
    private const val SEVEN_HOURS_IN_MINUTES = 7 * 60

    fun generate(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        recentReports: List<DayAvailabilityReport> = emptyList()
    ): NightlyReview {
        val history = recentReports
            .filter { it.date.isBefore(report.date) }
            .sortedByDescending { it.date }
            .take(RECENT_WINDOW_DAYS)
        val current = SnapshotStats.from(report)
        val baselines = PersonalBaselines.from(history)
        val localGeneratedAt = generatedAt.atZone(report.zoneId)
        val currentDay = localGeneratedAt.toLocalDate() == report.date
        val dayInProgress = currentDay && localGeneratedAt.hour < EVENING_HOUR

        val signals = Signals.from(current, baselines)
        val facts = interpretedFacts(current, baselines, signals, dayInProgress)
        val limits = interpretationLimits(report, current, baselines, currentDay, dayInProgress)
        val actions = suggestedActions(current, signals).take(2)

        return NightlyReview(
            date = report.date,
            generatedAt = generatedAt,
            summary = summary(current, signals, currentDay, dayInProgress),
            facts = facts,
            gaps = limits,
            nextActions = actions
        )
    }

    private fun summary(
        current: SnapshotStats,
        signals: Signals,
        currentDay: Boolean,
        dayInProgress: Boolean
    ): String {
        val provisional = when {
            dayInProgress -> " La jornada sigue en curso: actividad y nutrición aún son provisionales."
            currentDay -> " Es una lectura provisional que se recalculará mañana con datos tardíos."
            else -> ""
        }
        return when {
            signals.sleepBelowBaseline ->
                "La señal principal es un sueño por debajo de tu referencia reciente.$provisional"
            signals.shortSleep ->
                "La señal principal es un sueño corto, sin historial suficiente para afirmar una evolución.$provisional"
            signals.recoveryLessFavourable ->
                "Las señales de recuperación se apartan de tu referencia reciente en una dirección menos favorable.$provisional"
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
        dayInProgress: Boolean
    ): List<String> = buildList {
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
            add("Entrenamiento registrado: $sessions.")
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

    private fun suggestedActions(current: SnapshotStats, signals: Signals): List<String> = buildList {
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

    private data class SnapshotStats(
        val steps: Double?,
        val sleepMinutes: Int?,
        val restingHeartRate: Double?,
        val hrvRmssd: Double?,
        val exerciseSessions: List<MetricAvailability>,
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
