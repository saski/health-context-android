package com.example

import com.example.export.DailyExportSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyExportScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `schedules the same morning when target time has not passed`() {
        val now = ZonedDateTime.of(2026, 8, 19, 7, 30, 0, 0, madrid)

        assertEquals(Duration.ofMinutes(90), DailyExportSchedule.delayUntilNextRun(now))
    }

    @Test
    fun `schedules the next morning when target time has passed`() {
        val now = ZonedDateTime.of(2026, 8, 19, 11, 30, 0, 0, madrid)

        assertEquals(Duration.ofHours(21).plusMinutes(30), DailyExportSchedule.delayUntilNextRun(now))
    }
}
