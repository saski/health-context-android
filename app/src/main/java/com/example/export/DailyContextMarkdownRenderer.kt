package com.example.export

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.HealthAvailabilityStatus
import com.example.review.NightlyReview
import com.example.review.NightlyReviewGenerator
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class SnapshotStage(val serializedName: String) {
    PROVISIONAL("provisional"),
    FINAL("final")
}

object DailyContextMarkdownRenderer {
    fun fileName(report: DayAvailabilityReport): String = "health-context-${report.date}.md"

    fun render(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        suppliedReview: NightlyReview? = null,
        stage: SnapshotStage = SnapshotStage.FINAL
    ): String = buildString {
        val review = suppliedReview ?: NightlyReviewGenerator.generate(report, generatedAt)
        appendLine("# Health context — ${report.date}")
        appendLine()
        appendLine("- schema: health-context/v2")
        appendLine("- generated_at: ${DateTimeFormatter.ISO_INSTANT.format(generatedAt)}")
        appendLine("- timezone: ${report.zoneId.id}")
        appendLine("- snapshot_date: ${report.date}")
        appendLine("- snapshot_stage: ${stage.serializedName}")
        appendLine("- overall_status: ${report.overallStatus.name.lowercase()}")
        appendLine("- snapshot: daily Health Connect read; generated in foreground or scheduled background; not a live feed")
        appendLine()
        appendLine("## Morning coach review")
        appendLine("- conclusion: ${review.summary}")
        appendLine()
        appendLine("### Coach interpretation")
        appendLine(review.facts.firstOrNull() ?: "No reliable interpretation is available for this day.")
        appendLine()
        appendLine("### Recommendation for today")
        appendLine(review.nextActions.firstOrNull() ?: "No change is suggested from this snapshot alone.")
        appendLine()
        appendLine("### Confidence note")
        appendLine(review.gaps.firstOrNull() ?: "No additional confidence limitation is present in this snapshot.")
        report.domains.forEach { domain ->
            appendLine()
            appendLine("## ${domain.domain.labelEs}")
            appendLine("- status: ${domain.status.name.lowercase()}")
            appendLine("- source: ${domain.source}")
            appendLine("- coverage: ${domain.coveredThrough}")
            appendLine("- reason: ${domain.reason}")
            domain.metricSummary?.let { appendLine("- observation: $it") }
            if (domain.status != HealthAvailabilityStatus.AVAILABLE) {
                appendLine("- gap: unavailable; no value is inferred as zero")
            }
            domain.metrics.forEach { metric ->
                appendLine()
                appendLine("### ${metric.label}")
                appendLine("- key: ${metric.key}")
                appendLine("- status: ${metric.status.name.lowercase()}")
                appendLine("- source: ${metric.source}")
                appendLine("- coverage: ${metric.coveredThrough}")
                appendLine("- reason: ${metric.reason}")
                metric.observation?.let { appendLine("- observation: $it") }
                if (metric.status != HealthAvailabilityStatus.AVAILABLE) {
                    appendLine("- gap: unavailable; no value is inferred as zero")
                }
            }
        }
    }
}
