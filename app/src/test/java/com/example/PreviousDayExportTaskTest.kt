package com.example

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.SdkAvailability
import com.example.data.repository.HealthConnectRepository
import com.example.export.DailyContextWriter
import com.example.export.PreviousDayExportTask
import com.example.review.NightlyReview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PreviousDayExportTaskTest {
    @Test
    fun `exports the previous local calendar day`() = runTest {
        val zone = ZoneId.of("Europe/Madrid")
        val clock = Clock.fixed(Instant.parse("2026-08-19T09:30:00Z"), zone)
        val health = CapturingHealthRepository()
        val writer = CapturingWriter()

        val result = PreviousDayExportTask(health, writer, clock, zone).run()

        assertTrue(result.isSuccess)
        assertEquals(LocalDate.of(2026, 8, 18), health.requestedDates.first())
        assertEquals(8, health.requestedDates.size)
        assertEquals(LocalDate.of(2026, 8, 18), writer.report?.date)
    }

    private class CapturingHealthRepository : HealthConnectRepository {
        val requestedDates = mutableListOf<LocalDate>()

        override fun getSdkAvailability() = SdkAvailability.AVAILABLE
        override suspend fun getGrantedPermissions() = getRequiredPermissions() + getBackgroundReadPermission()
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getBackgroundReadPermission() = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        override fun isBackgroundReadAvailable() = true

        override suspend fun loadDayAvailability(date: LocalDate, zoneId: ZoneId): DayAvailabilityReport {
            requestedDates += date
            return DayAvailabilityReport(
                date = date,
                zoneId = zoneId,
                domains = emptyList(),
                overallStatus = HealthAvailabilityStatus.PARTIAL,
                unavailableDomains = emptyList()
            )
        }
    }

    private class CapturingWriter : DailyContextWriter {
        var report: DayAvailabilityReport? = null

        override fun export(
            report: DayAvailabilityReport,
            generatedAt: Instant,
            review: NightlyReview?
        ): Result<String> {
            this.report = report
            return Result.success("health-context-${report.date}.md")
        }
    }
}
