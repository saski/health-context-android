package com.example.review

import com.example.data.model.DayAvailabilityReport
import com.example.data.repository.HealthConnectRepository
import java.time.LocalDate
import java.time.ZoneId

internal suspend fun HealthConnectRepository.loadRecentReports(
    beforeDate: LocalDate,
    zoneId: ZoneId,
    days: Int = 7
): List<DayAvailabilityReport> = (1..days).mapNotNull { daysAgo ->
    runCatching { loadDayAvailability(beforeDate.minusDays(daysAgo.toLong()), zoneId) }.getOrNull()
}
