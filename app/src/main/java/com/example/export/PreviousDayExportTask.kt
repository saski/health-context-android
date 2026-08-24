package com.example.export

import com.example.data.repository.HealthConnectRepository
import com.example.review.NightlyReviewGenerator
import com.example.review.loadRecentReports
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class PreviousDayExportTask(
    private val healthRepository: HealthConnectRepository,
    private val writer: DailyContextWriter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    suspend fun run(): Result<String> = runCatching {
        val date = LocalDate.now(clock.withZone(zoneId)).minusDays(1)
        val report = healthRepository.loadDayAvailability(date, zoneId)
        val generatedAt = clock.instant()
        val recentReports = healthRepository.loadRecentReports(date, zoneId)
        val review = NightlyReviewGenerator.generate(report, generatedAt, recentReports)
        writer.export(report, generatedAt, review).getOrThrow()
    }
}
