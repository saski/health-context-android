package com.example.export

import java.time.Duration
import java.time.ZonedDateTime

object DailyExportSchedule {
    const val TARGET_HOUR = 9

    fun delayUntilNextRun(now: ZonedDateTime): Duration {
        val todayTarget = now.toLocalDate().atTime(TARGET_HOUR, 0).atZone(now.zone)
        val nextTarget = if (todayTarget.isAfter(now)) {
            todayTarget
        } else {
            now.toLocalDate().plusDays(1).atTime(TARGET_HOUR, 0).atZone(now.zone)
        }
        return Duration.between(now, nextTarget)
    }
}
