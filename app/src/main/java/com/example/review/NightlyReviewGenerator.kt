package com.example.review

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.MetricAvailability
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Builds one concise, local coaching message from daily evidence and recent personal context. */
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
        val trends = RecentEvolution.from(history)
        val localGeneratedAt = generatedAt.atZone(report.zoneId)
        val currentDay = localGeneratedAt.toLocalDate() == report.date
        val dayInProgress = currentDay && localGeneratedAt.hour < EVENING_HOUR
        val signals = Signals.from(current, baselines)

        return NightlyReview(
            date = report.date,
            generatedAt = generatedAt,
            summary = summary(current, signals, trends, currentDay, dayInProgress, feeling),
            facts = listOf(coachingMessage(current, baselines, signals, trends, feeling)),
            gaps = confidenceNote(current, baselines, currentDay, dayInProgress),
            nextActions = listOf(primaryAction(current, signals, trends, feeling)),
            checkInPrompt = checkInPrompt(current, signals, trends)
        )
    }

    private fun summary(
        current: SnapshotStats,
        signals: Signals,
        trends: RecentEvolution,
        currentDay: Boolean,
        dayInProgress: Boolean,
        feeling: NightlyFeeling?
    ): String {
        val provisional = when {
            dayInProgress -> " La jornada sigue en curso, así que esta lectura todavía es provisional."
            currentDay -> " Esta lectura se completará mañana con los datos que lleguen tarde."
            else -> ""
        }
        val hasWorkout = current.exerciseSessions.isNotEmpty()
        return when {
            feeling == NightlyFeeling.UNWELL ->
                "Has indicado que hoy te encuentras mal; manda cómo te sientes, no el plan.$provisional"
            feeling == NightlyFeeling.LOADED && trends.trainingLoadHigher ->
                "La carga de las últimas semanas está subiendo y hoy sigues notándola.$provisional"
            signals.sleepBelowBaseline && hasWorkout ->
                "Ayer entrenaste con menos sueño de lo habitual, por debajo de tu referencia reciente.$provisional"
            signals.sleepBelowBaseline ->
                "Ayer el descanso quedó por debajo de tu referencia reciente.$provisional"
            signals.shortSleep && hasWorkout ->
                "Ayer hubo entrenamiento, pero el sueño fue corto para acompañar bien la recuperación.$provisional"
            signals.shortSleep ->
                "La señal principal es un sueño corto.$provisional"
            signals.recoveryLessFavourable && hasWorkout ->
                "Ayer sumaste entrenamiento con señales de recuperación menos favorables de lo habitual.$provisional"
            signals.recoveryLessFavourable ->
                "Ayer las señales de recuperación fueron menos favorables que tu referencia reciente.$provisional"
            feeling == NightlyFeeling.LOADED ->
                "Hoy te notas cargado; conviene ajustar el plan a tus sensaciones.$provisional"
            hasWorkout ->
                "Ayer entrenaste y no aparece otra señal sólida que obligue a cambiar el plan de hoy.$provisional"
            dayInProgress ->
                "La jornada sigue en curso y aún no hay evidencia suficiente para valorarla."
            trends.activityLower ->
                "Tu actividad reciente ha bajado, aunque un día tranquilo no necesita compensación.$provisional"
            else ->
                "Ayer no aparece ninguna señal sólida que obligue a cambiar el plan de hoy.$provisional"
        }.trim()
    }

    private fun coachingMessage(
        current: SnapshotStats,
        baselines: PersonalBaselines,
        signals: Signals,
        trends: RecentEvolution,
        feeling: NightlyFeeling?
    ): String = buildList {
        feeling?.let { add("Tu sensación registrada fue ${it.labelEs}; la uso para matizar la recomendación.") }

        current.sleepMinutes?.let { sleep ->
            val baseline = baselines.sleepMinutes
            when {
                signals.sleepBelowBaseline && baseline != null -> {
                    val difference = baseline.value.roundToInt() - sleep
                    add(
                        "Dormiste ${formatMinutes(sleep)}, ${formatMinutes(difference)} menos que tu " +
                            "referencia reciente de ${formatMinutes(baseline.value.roundToInt())}."
                    )
                }
                sleep < SEVEN_HOURS_IN_MINUTES ->
                    add("Dormiste ${formatMinutes(sleep)}; es un sueño corto, aunque un solo día no define tu evolución.")
                baseline != null && abs(sleep - baseline.value) <= 45 ->
                    add("Dormiste ${formatMinutes(sleep)}, cerca de tu referencia reciente.")
                else -> add("El sueño registrado fue de ${formatMinutes(sleep)}.")
            }
        }

        if (signals.recoveryLessFavourable) {
            val restingDelta = baselines.restingHeartRate?.let { current.restingHeartRate?.minus(it.value) }
            val hrvDeltaPercent = baselines.hrvRmssd?.let { baseline ->
                current.hrvRmssd?.let { ((it - baseline.value) / baseline.value * 100.0).roundToInt() }
            }
            val changes = buildList {
                restingDelta?.takeIf { it >= 5.0 }?.let { add("FC en reposo ${it.roundToInt()} ppm más alta") }
                hrvDeltaPercent?.takeIf { it <= -15 }?.let { add("variabilidad cardíaca ${abs(it)} % menor") }
            }.joinToString(" y ")
            add("La $changes apunta a menor recuperación frente a tu propia referencia.")
        }

        if (current.exerciseSessions.isNotEmpty()) {
            val count = current.exerciseSessions.size
            val sessionLabel = if (count == 1) "1 sesión" else "$count sesiones"
            val duration = current.trainingMinutes?.let { " y ${formatMinutes(it)} en total" }.orEmpty()
            val types = current.exerciseSessions.map { it.label }.distinct().take(2).joinToString(" y ")
            add("Entrenamiento registrado: $sessionLabel$duration${types.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}.")
        } else if (current.steps != null && baselines.steps != null) {
            val ratio = current.steps / baselines.steps.value
            add(
                when {
                    ratio < 0.7 -> "Tu movimiento quedó claramente por debajo de tu referencia reciente."
                    ratio > 1.3 -> "Tu movimiento quedó claramente por encima de tu referencia reciente."
                    else -> "Tu movimiento se mantuvo cerca de tu referencia reciente."
                }
            )
        }

        if (current.nutritionObserved) {
            val nutritionDetails = buildList {
                current.nutritionEnergy?.let { add("${formatWhole(it)} kcal") }
                current.nutritionProtein?.let { add("${formatWhole(it)} g de proteína") }
            }
            add(
                if (nutritionDetails.isEmpty()) {
                    "Hay alimentación registrada, pero no suficiente detalle para valorar el conjunto del día."
                } else {
                    "La alimentación quedó registrada (${nutritionDetails.joinToString(" y ")}); la tomo como contexto, no como un objetivo personal."
                }
            )
        }

        trends.primaryFact?.let(::add)
    }.ifEmpty {
        listOf("Los datos disponibles no permiten una lectura responsable más allá de confirmar que no hay una señal clara.")
    }.joinToString(" ")

    private fun confidenceNote(
        current: SnapshotStats,
        baselines: PersonalBaselines,
        currentDay: Boolean,
        dayInProgress: Boolean
    ): List<String> {
        val limits = buildList {
            if (currentDay) {
                add(
                    if (dayInProgress) {
                        "Es una lectura provisional del día en curso."
                    } else {
                        "Los registros tardíos se incorporarán en la revisión de mañana."
                    }
                )
            }
            if (!baselines.hasAnyReadyBaseline()) {
                add("Hacen falta al menos 3 días comparables para hablar de evolución personal.")
            }
            if (current.sleepMinutes == null) add("No hay sueño utilizable.")
            if (current.exerciseSessions.isEmpty() && current.hasIsolatedExerciseMetrics) {
                add("Hay métricas de movimiento, pero no una sesión de entrenamiento confirmada.")
            }
            if (!current.nutritionObserved) {
                add("No hay un registro utilizable de alimentación; no se interpreta como ingesta cero.")
            }
        }
        return limits.takeIf { it.isNotEmpty() }
            ?.let { listOf(it.joinToString(" ")) }
            .orEmpty()
    }

    private fun primaryAction(
        current: SnapshotStats,
        signals: Signals,
        trends: RecentEvolution,
        feeling: NightlyFeeling?
    ): String = when {
        feeling == NightlyFeeling.UNWELL ->
            "Hoy prioriza descanso y reevalúa cómo te encuentras; si el malestar es importante o persiste, consulta a un profesional."
        feeling == NightlyFeeling.LOADED && trends.trainingLoadHigher ->
            "Cambia hoy la sesión intensa por yoga, movilidad, un paseo suave o descanso, y vuelve a valorar mañana."
        feeling == NightlyFeeling.LOADED ->
            "Hoy elige yoga, movilidad, un paseo suave o descanso y comprueba si la sensación de carga mejora."
        (signals.sleepBelowBaseline || signals.shortSleep || signals.recoveryLessFavourable) &&
            current.exerciseSessions.isNotEmpty() ->
            "Hoy prioriza recuperación: baja la intensidad o elige yoga, movilidad o paseo suave, come con regularidad y protege una ventana de sueño más amplia."
        signals.sleepBelowBaseline || signals.shortSleep ->
            "Hoy mantén un movimiento suave y protege una ventana de sueño más amplia esta noche."
        signals.recoveryLessFavourable ->
            "Hoy ajusta la intensidad a tus sensaciones y favorece recuperación con comidas regulares, hidratación y descanso."
        trends.activityLower ->
            "Si hoy te encuentras bien, recupera movimiento sin compensar: un paseo o una sesión suave es suficiente."
        current.exerciseSessions.isNotEmpty() ->
            "Mantén el plan previsto y facilita la recuperación con comidas regulares, hidratación y una buena oportunidad de sueño."
        else ->
            "Mantén el plan previsto; hoy no hay una señal suficientemente sólida para cambiarlo."
    }

    private fun checkInPrompt(
        current: SnapshotStats,
        signals: Signals,
        trends: RecentEvolution
    ): String = when {
        current.exerciseSessions.isNotEmpty() || trends.trainingLoadHigher -> "¿Cómo notas hoy la recuperación?"
        signals.shortSleep || signals.sleepBelowBaseline -> "¿Con qué energía te has levantado?"
        else -> "¿Cómo te encuentras hoy?"
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
                val restingElevated = current.restingHeartRate?.let { value ->
                    baselines.restingHeartRate?.let { value >= it.value + 5.0 }
                } ?: false
                val hrvLower = current.hrvRmssd?.let { value ->
                    baselines.hrvRmssd?.let { value <= it.value * 0.85 }
                } ?: false
                return Signals(
                    shortSleep = current.sleepMinutes?.let { it < SEVEN_HOURS_IN_MINUTES } == true,
                    sleepBelowBaseline = sleepBelowBaseline,
                    recoveryLessFavourable = restingElevated || hrvLower
                )
            }
        }
    }

    private data class Baseline(val value: Double)

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
                return Baseline(median(values))
            }
        }
    }

    private data class RecentEvolution(
        val primaryFact: String?,
        val trainingLoadHigher: Boolean,
        val activityLower: Boolean
    ) {
        companion object {
            fun from(history: List<DayAvailabilityReport>): RecentEvolution {
                val recent = history.take(RECENT_WINDOW_DAYS).map(SnapshotStats::from)
                val earlier = history.drop(RECENT_WINDOW_DAYS).take(21).map(SnapshotStats::from)

                val recentTraining = recent.sumOf { it.trainingMinutes ?: 0 }
                val earlierWeeklyTraining = if (earlier.isEmpty()) {
                    0.0
                } else {
                    earlier.sumOf { it.trainingMinutes ?: 0 } * 7.0 / earlier.size
                }
                val trainingLoadHigher = recent.size >= MINIMUM_BASELINE_DAYS &&
                    earlier.size >= MINIMUM_EARLIER_DAYS && recentTraining >= 60 &&
                    recentTraining >= earlierWeeklyTraining * 1.3

                val stepComparison = comparableMedian(
                    recent.mapNotNull { it.steps },
                    earlier.mapNotNull { it.steps }
                )
                val activityLower = stepComparison?.let { (recentValue, earlierValue) ->
                    earlierValue > 0 && recentValue <= earlierValue * 0.8
                } == true

                val facts = buildList {
                    if (trainingLoadHigher) {
                        add("Últimos 7 días: la carga de entrenamiento ha subido frente a las tres semanas anteriores.")
                    }
                    comparableMedian(
                        recent.mapNotNull { it.sleepMinutes?.toDouble() },
                        earlier.mapNotNull { it.sleepMinutes?.toDouble() }
                    )?.let { (recentValue, earlierValue) ->
                        val difference = (recentValue - earlierValue).roundToInt()
                        if (abs(difference) >= 30) {
                            val direction = if (difference > 0) "más" else "menos"
                            add("Últimos 7 días: el sueño mediano es ${formatMinutes(abs(difference))} $direction que en los 21 anteriores.")
                        }
                    }
                    stepComparison?.let { (recentValue, earlierValue) ->
                        if (earlierValue > 0) {
                            val percent = ((recentValue - earlierValue) / earlierValue * 100).roundToInt()
                            if (abs(percent) >= 20) {
                                val direction = if (percent > 0) "por encima" else "por debajo"
                                add("Últimos 7 días: la actividad mediana está ${abs(percent)} % $direction de los 21 anteriores.")
                            }
                        }
                    }
                    val nutritionDays = recent.count { it.nutritionObserved }
                    if (recent.size >= 5 && nutritionDays in 1..4) {
                        add("La alimentación está registrada en $nutritionDays de los últimos ${recent.size} días; la tendencia nutricional aún es incompleta.")
                    }
                }
                return RecentEvolution(facts.firstOrNull(), trainingLoadHigher, activityLower)
            }

            private fun comparableMedian(recent: List<Double>, earlier: List<Double>): Pair<Double, Double>? {
                if (recent.size < MINIMUM_BASELINE_DAYS || earlier.size < MINIMUM_EARLIER_DAYS) return null
                return median(recent) to median(earlier)
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
        val hasIsolatedExerciseMetrics: Boolean,
        val nutritionObserved: Boolean,
        val nutritionEnergy: Double?,
        val nutritionProtein: Double?
    ) {
        companion object {
            fun from(report: DayAvailabilityReport): SnapshotStats {
                val exerciseMetrics = report.domain(HealthDomain.EXERCISE)
                    ?.metrics
                    .orEmpty()
                    .filter { it.status == HealthAvailabilityStatus.AVAILABLE && !it.observation.isNullOrBlank() }
                val nutrition = report.domain(HealthDomain.NUTRITION)
                return SnapshotStats(
                    steps = report.availableMetric(HealthDomain.STEPS, "steps")?.number(),
                    sleepMinutes = report.domain(HealthDomain.SLEEP)
                        ?.metrics
                        .orEmpty()
                        .filter { it.status == HealthAvailabilityStatus.AVAILABLE && it.key.startsWith("sleep_session_") }
                        .mapNotNull { it.observation?.durationMinutes() }
                        .takeIf { it.isNotEmpty() }
                        ?.sum(),
                    restingHeartRate = report.availableMetric(
                        HealthDomain.RESTING_HEART_RATE,
                        "resting_heart_rate"
                    )?.number(),
                    hrvRmssd = report.availableMetric(HealthDomain.RESTING_HEART_RATE, "hrv_rmssd")?.number(),
                    exerciseSessions = exerciseMetrics.filter { it.key.startsWith("exercise_session_") },
                    trainingMinutes = report.availableMetric(
                        HealthDomain.EXERCISE,
                        "exercise_duration_total"
                    )?.observation?.durationMinutes()
                        ?: exerciseMetrics
                            .filter { it.key.startsWith("exercise_session_") }
                            .mapNotNull { it.observation?.durationMinutes() }
                            .takeIf { it.isNotEmpty() }
                            ?.sum(),
                    hasIsolatedExerciseMetrics = exerciseMetrics.any { !it.key.startsWith("exercise_session_") },
                    nutritionObserved = nutrition?.hasObservedData() == true,
                    nutritionEnergy = report.availableMetric(
                        HealthDomain.NUTRITION,
                        "nutrition_energy_total"
                    )?.number(),
                    nutritionProtein = report.availableMetric(
                        HealthDomain.NUTRITION,
                        "nutrition_protein_total"
                    )?.number()
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

    private fun String.durationMinutes(): Int? {
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

    private fun formatWhole(value: Double): String =
        if (value % 1.0 == 0.0) value.roundToInt().toString() else String.format("%.1f", value)

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
