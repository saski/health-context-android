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
    fun `schedules the same morning before nine`() {
        val now = ZonedDateTime.of(2026, 8, 20, 8, 15, 0, 0, madrid)

        assertEquals(Duration.ofMinutes(45), NightlyReviewSchedule.delayUntilNextRun(now))
    }

    @Test
    fun `schedules the next morning after nine`() {
        val now = ZonedDateTime.of(2026, 8, 20, 9, 30, 0, 0, madrid)

        assertEquals(Duration.ofHours(23).plusMinutes(30), NightlyReviewSchedule.delayUntilNextRun(now))
    }
}
