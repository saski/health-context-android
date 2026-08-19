package com.example.export

import com.example.data.repository.HealthConnectRepository
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
        writer.export(report, clock.instant()).getOrThrow()
    }
}
