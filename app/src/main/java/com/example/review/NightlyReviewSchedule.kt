package com.example.review

import java.time.Duration
import java.time.ZonedDateTime

object NightlyReviewSchedule {
    const val TARGET_HOUR = 22
    const val TARGET_MINUTE = 30

    fun delayUntilNextRun(now: ZonedDateTime): Duration {
        val todayTarget = now.toLocalDate()
            .atTime(TARGET_HOUR, TARGET_MINUTE)
            .atZone(now.zone)
        val nextTarget = if (todayTarget.isAfter(now)) {
            todayTarget
        } else {
            now.toLocalDate().plusDays(1)
                .atTime(TARGET_HOUR, TARGET_MINUTE)
                .atZone(now.zone)
        }
        return Duration.between(now, nextTarget)
    }
}
