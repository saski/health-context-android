package com.example

import com.example.review.NightlyReviewSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class NightlyReviewScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `schedules the same night before the target time`() {
        val now = ZonedDateTime.of(2026, 8, 20, 21, 45, 0, 0, madrid)

        assertEquals(Duration.ofMinutes(45), NightlyReviewSchedule.delayUntilNextRun(now))
    }

    @Test
    fun `schedules the next night after the target time`() {
        val now = ZonedDateTime.of(2026, 8, 20, 23, 0, 0, 0, madrid)

        assertEquals(Duration.ofHours(23).plusMinutes(30), NightlyReviewSchedule.delayUntilNextRun(now))
    }
}
