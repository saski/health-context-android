package com.example.review

import android.content.Context
import androidx.work.WorkManager
import java.time.ZonedDateTime

class NightlyReviewScheduler(private val context: Context) {
    @Suppress("UNUSED_PARAMETER")
    fun enable(now: ZonedDateTime = ZonedDateTime.now()) {
        // The 09:00 DailyHealthExportWorker owns both final export and notification.
        // Cancel the old 22:30 work when upgrading an existing installation.
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "nightly_health_review"
    }
}
