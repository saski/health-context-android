package com.example

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.repository.HealthConnectRepository
import com.example.export.DailyContextWriter
import com.example.export.SnapshotStage
import com.example.review.NightlyReview
import com.example.review.NightlyReviewNotifier
import com.example.review.NightlyReviewStore
import com.example.review.NightlyReviewTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NightlyReviewTaskTest {
    @Test
    fun `reads today persists the artifact and only then notifies`() = runTest {
        val zone = ZoneId.of("Europe/Madrid")
        val clock = Clock.fixed(Instant.parse("2026-08-20T20:30:00Z"), zone)
        val events = mutableListOf<String>()
        val health = CapturingHealthRepository()
        val writer = CapturingWriter(events)
        val store = CapturingStore(events)
        val notifier = CapturingNotifier(events)

        val result = NightlyReviewTask(health, writer, store, notifier, clock, zone).run()

        assertTrue(result.isSuccess)
        assertEquals(LocalDate.of(2026, 8, 20), health.requestedDates.first())
        assertEquals(29, health.requestedDates.size)
        assertEquals(listOf("export", "save", "notify"), events)
        assertEquals("health-context-2026-08-20.md", result.getOrNull())
    }

    @Test
    fun `does not notify when the canonical export fails`() = runTest {
        val zone = ZoneId.of("Europe/Madrid")
        val events = mutableListOf<String>()
        val result = NightlyReviewTask(
            healthRepository = CapturingHealthRepository(),
            writer = FailingWriter(events),
            store = CapturingStore(events),
            notifier = CapturingNotifier(events),
            clock = Clock.fixed(Instant.parse("2026-08-20T20:30:00Z"), zone),
            zoneId = zone
        ).run()

        assertTrue(result.isFailure)
        assertEquals(listOf("export"), events)
    }

    private class CapturingHealthRepository : HealthConnectRepository {
        val requestedDates = mutableListOf<LocalDate>()
        override fun getSdkAvailability() = com.example.data.model.SdkAvailability.AVAILABLE
        override suspend fun getGrantedPermissions() = emptySet<String>()
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getBackgroundReadPermission() = "background"
        override fun isBackgroundReadAvailable() = true
        override suspend fun loadDayAvailability(date: LocalDate, zoneId: ZoneId): DayAvailabilityReport {
            requestedDates += date
            return DayAvailabilityReport(
                date = date,
                zoneId = zoneId,
                domains = listOf(
                    DomainAvailability(
                        HealthDomain.STEPS,
                        HealthAvailabilityStatus.AVAILABLE,
                        "Health Connect",
                        "Total del día",
                        "Agregación",
                        "4.000 pasos"
                    )
                ),
                overallStatus = HealthAvailabilityStatus.AVAILABLE,
                unavailableDomains = emptyList()
            )
        }
    }

    private class CapturingWriter(private val events: MutableList<String>) : DailyContextWriter {
        override fun export(
            report: DayAvailabilityReport,
            generatedAt: Instant,
            review: NightlyReview?,
            stage: SnapshotStage
        ): Result<String> {
            events += "export"
            assertEquals(SnapshotStage.PROVISIONAL, stage)
            return Result.success("health-context-${report.date}.md")
        }
    }

    private class FailingWriter(private val events: MutableList<String>) : DailyContextWriter {
        override fun export(
            report: DayAvailabilityReport,
            generatedAt: Instant,
            review: NightlyReview?,
            stage: SnapshotStage
        ): Result<String> {
            events += "export"
            return Result.failure(IllegalStateException("folder unavailable"))
        }
    }

    private class CapturingStore(private val events: MutableList<String>) : NightlyReviewStore {
        override fun isEnabled() = true
        override fun setEnabled(enabled: Boolean) = Unit
        override fun latest(): NightlyReview? = null
        override fun save(review: NightlyReview) {
            events += "save"
        }
        override fun status(): String? = null
        override fun recordStatus(status: String) = Unit
        override fun feedback(date: LocalDate) = null
        override fun recordFeedback(date: LocalDate, feedback: com.example.review.NightlyReviewFeedback) = Unit
        override fun feeling(date: LocalDate) = null
        override fun recordFeeling(date: LocalDate, feeling: com.example.review.NightlyFeeling) = Unit
    }

    private class CapturingNotifier(private val events: MutableList<String>) : NightlyReviewNotifier {
        override fun notify(review: NightlyReview) {
            events += "notify"
        }
    }
}
