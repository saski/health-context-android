package com.example

import com.example.export.ExportRecoveryPolicy
import com.example.export.DailyContextArchiveDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExportRecoveryPolicyTest {
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun `always finalizes yesterday even when the archive is complete`() {
        val completeArchive = (1L..7L).map { today.minusDays(it) }.toSet()

        assertEquals(
            listOf(today.minusDays(1)),
            ExportRecoveryPolicy.datesToExport(today, completeArchive)
        )
    }

    @Test
    fun `recovers missing dates chronologically and leaves yesterday last without duplicates`() {
        val archive = setOf(
            today.minusDays(7),
            today.minusDays(5),
            today.minusDays(4),
            today.minusDays(2)
        )

        assertEquals(
            listOf(
                today.minusDays(6),
                today.minusDays(3),
                today.minusDays(1)
            ),
            ExportRecoveryPolicy.datesToExport(today, archive)
        )
    }

    @Test
    fun `ignores archive entries outside the recovery window`() {
        val archive = setOf(today, today.plusDays(1), today.minusDays(8))

        assertEquals(
            (7L downTo 1L).map { today.minusDays(it) },
            ExportRecoveryPolicy.datesToExport(today, archive)
        )
    }

    @Test
    fun `extracts only dated canonical artifacts from folder entries`() {
        assertEquals(
            setOf(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 23)),
            DailyContextArchiveDates.parse(
                listOf(
                    "health-context-2026-08-22.md",
                    "health-context-latest.md",
                    "health-context-2026-08-23.md",
                    "daily-freshness-probe.md"
                )
            )
        )
    }
}
