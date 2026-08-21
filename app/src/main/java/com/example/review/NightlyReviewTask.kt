package com.example.review

import com.example.data.repository.HealthConnectRepository
import com.example.export.DailyContextWriter
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class NightlyReviewTask(
    private val healthRepository: HealthConnectRepository,
    private val writer: DailyContextWriter,
    private val store: NightlyReviewStore,
    private val notifier: NightlyReviewNotifier,
    private val clock: Clock,
    private val zoneId: ZoneId
) {
    suspend fun run(): Result<String> = runCatching {
        val generatedAt = clock.instant()
        val date = LocalDate.now(clock.withZone(zoneId))
        val report = healthRepository.loadDayAvailability(date, zoneId)
        val review = NightlyReviewGenerator.generate(report, generatedAt)
        val fileName = writer.export(report, generatedAt).getOrThrow()
        store.save(review)
        notifier.notify(review)
        fileName
    }
}
