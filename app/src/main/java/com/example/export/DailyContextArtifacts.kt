package com.example.export

import com.example.data.model.DayAvailabilityReport
import com.example.review.NightlyReview
import java.time.Instant

data class DailyContextArtifact(
    val fileName: String,
    val content: String
)

object DailyContextArtifacts {
    const val LATEST_FILE_NAME = "health-context-latest.md"

    fun create(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        review: NightlyReview? = null,
        stage: SnapshotStage = SnapshotStage.FINAL
    ): List<DailyContextArtifact> {
        val content = DailyContextMarkdownRenderer.render(report, generatedAt, review, stage)
        return listOf(
            DailyContextArtifact(DailyContextMarkdownRenderer.fileName(report), content),
            DailyContextArtifact(LATEST_FILE_NAME, content)
        )
    }
}
