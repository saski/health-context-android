package com.example.export

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyExportScheduler(private val context: Context) {
    fun enable(now: ZonedDateTime = ZonedDateTime.now()) {
        val delay = DailyExportSchedule.delayUntilNextRun(now)
        val periodic = PeriodicWorkRequestBuilder<DailyHealthExportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        val catchUp = OneTimeWorkRequestBuilder<DailyHealthExportWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CATCH_UP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            catchUp
        )
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(CATCH_UP_WORK_NAME)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "daily_health_context_export"
        const val CATCH_UP_WORK_NAME = "daily_health_context_export_catch_up"
    }
}
