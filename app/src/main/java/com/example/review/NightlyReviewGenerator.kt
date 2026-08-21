package com.example.review

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import java.time.Instant

object NightlyReviewGenerator {
    private val preferredKeys = mapOf(
        HealthDomain.STEPS to listOf("steps", "active_calories", "distance", "total_calories"),
        HealthDomain.SLEEP to listOf("sleep_session_1"),
        HealthDomain.WEIGHT to listOf("weight", "body_fat", "lean_body_mass"),
        HealthDomain.NUTRITION to listOf(
            "nutrition_energy_total",
            "nutrition_protein_total",
            "nutrition_carbohydrate_total",
            "nutrition_fat_total",
            "hydration"
        ),
        HealthDomain.RESTING_HEART_RATE to listOf(
            "resting_heart_rate",
            "hrv_rmssd",
            "oxygen_saturation",
            "heart_rate"
        )
    )

    fun generate(report: DayAvailabilityReport, generatedAt: Instant): NightlyReview {
        val covered = report.domains.count { it.hasObservedData() }
        val uncovered = report.domains.filter { !it.hasObservedData() }
        val summary = buildSummary(covered, report.domains.size, uncovered)
        val facts = report.domains
            .sortedBy { if (it.domain == HealthDomain.EXERCISE) 0 else 1 }
            .mapNotNull(::factFor)
        val gaps = report.domains.mapNotNull(::gapFor)
        val actions = possibleActions(report).take(2)

        return NightlyReview(
            date = report.date,
            generatedAt = generatedAt,
            summary = summary,
            facts = facts,
            gaps = gaps,
            nextActions = actions
        )
    }

    private fun buildSummary(
        covered: Int,
        total: Int,
        uncovered: List<DomainAvailability>
    ): String {
        if (uncovered.isEmpty()) return "$covered de $total áreas aportan datos; no hay dominios completamente ausentes."
        val labels = uncovered.joinToString(" y ") { it.domain.labelEs.lowercase() }
        val verb = if (uncovered.size == 1) "queda" else "quedan"
        return "$covered de $total áreas aportan datos; $labels $verb sin cobertura."
    }

    private fun factFor(domain: DomainAvailability): String? {
        val available = domain.metrics.filter {
            it.status == HealthAvailabilityStatus.AVAILABLE && !it.observation.isNullOrBlank()
        }
        if (available.isEmpty()) {
            return domain.metricSummary?.takeIf { domain.hasObservedData() }?.let {
                "${domain.domain.labelEs}: ${sentence(it)}"
            }
        }

        val selected = when (domain.domain) {
            HealthDomain.EXERCISE -> available.filter { it.key.startsWith("exercise_session_") }.take(3)
            else -> preferredKeys[domain.domain]
                .orEmpty()
                .mapNotNull { key -> available.firstOrNull { it.key == key } }
                .take(if (domain.domain == HealthDomain.NUTRITION) 5 else 3)
        }.ifEmpty { available.take(3) }

        val observations = selected.joinToString("; ") { metric ->
            buildString {
                append(metric.label)
                append(' ')
                append(metric.observation)
                if (domain.domain == HealthDomain.EXERCISE && metric.coveredThrough != "Total del día") {
                    append(" (${metric.coveredThrough}; source: ${metric.source})")
                } else if (domain.domain == HealthDomain.EXERCISE) {
                    append(" (source: ${metric.source})")
                }
            }
        }
        return "${domain.domain.labelEs}: ${sentence(observations)}"
    }

    private fun gapFor(domain: DomainAvailability): String? {
        if (!domain.hasObservedData()) {
            val reason = if (domain.status == HealthAvailabilityStatus.PERMISSION_NEEDED) {
                "Faltan permisos de lectura"
            } else {
                domain.reason
            }
            return "${domain.domain.labelEs}: ${sentence(reason)}"
        }

        val missing = domain.metrics.filter { it.status != HealthAvailabilityStatus.AVAILABLE }
        if (missing.isEmpty()) return null
        val labels = missing.take(3).joinToString(", ") { it.label }
        val suffix = if (missing.size > 3) " y ${missing.size - 3} más" else ""
        return "${domain.domain.labelEs}: sin datos de $labels$suffix."
    }

    private fun possibleActions(report: DayAvailabilityReport): List<String> = buildList {
        val exercisePresent = report.domain(HealthDomain.EXERCISE)?.hasObservedData() == true
        val sleepMissing = report.domain(HealthDomain.SLEEP)?.hasObservedData() != true
        val vitalsMissing = report.domain(HealthDomain.RESTING_HEART_RATE)?.hasObservedData() != true
        if (exercisePresent && (sleepMissing || vitalsMissing)) {
            add("No hay datos suficientes de recuperación para orientar la intensidad de mañana; decide según tus sensaciones.")
        }

        val permissionDomains = report.domains.filter { domain ->
            domain.status == HealthAvailabilityStatus.PERMISSION_NEEDED ||
                domain.metrics.any { it.status == HealthAvailabilityStatus.PERMISSION_NEEDED }
        }
        if (permissionDomains.isNotEmpty()) {
            add("Revisa los permisos de ${permissionDomains.joinToString(" y ") { it.domain.labelEs.lowercase() }} para completar futuras revisiones.")
        }

        val nutrition = report.domain(HealthDomain.NUTRITION)
        if (nutrition != null && nutrition.status != HealthAvailabilityStatus.AVAILABLE) {
            add("Si quieres evaluar el día completo, termina el registro manual de nutrición cuando falten comidas.")
        }
    }

    private fun DomainAvailability.hasObservedData(): Boolean =
        status == HealthAvailabilityStatus.AVAILABLE || status == HealthAvailabilityStatus.PARTIAL ||
            metrics.any { it.status == HealthAvailabilityStatus.AVAILABLE }

    private fun DayAvailabilityReport.domain(domain: HealthDomain): DomainAvailability? =
        domains.firstOrNull { it.domain == domain }

    private fun sentence(text: String): String = text.trim().removeSuffix(".") + "."
}
