package com.example.export

import com.example.data.repository.HealthConnectRepository
import com.example.review.NightlyReviewGenerator
import com.example.review.NightlyReviewStore
import com.example.review.loadRecentReports
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class PreviousDayExportTask(
    private val healthRepository: HealthConnectRepository,
    private val writer: DailyContextWriter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val reviewStore: NightlyReviewStore? = null
) {
    suspend fun run(
        date: LocalDate = LocalDate.now(clock.withZone(zoneId)).minusDays(1)
    ): Result<String> = runCatching {
        val report = healthRepository.loadDayAvailability(date, zoneId)
        val generatedAt = clock.instant()
        val recentReports = healthRepository.loadRecentReports(date, zoneId)
        val review = NightlyReviewGenerator.generate(
            report,
            generatedAt,
            recentReports,
            reviewStore?.feeling(date)
        )
        val fileName = writer.export(report, generatedAt, review, SnapshotStage.FINAL).getOrThrow()
        reviewStore?.save(review)
        fileName
    }
}
