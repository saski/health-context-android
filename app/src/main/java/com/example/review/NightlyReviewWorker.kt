package com.example.review

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Compatibility worker that retires the former 22:30 schedule after an upgrade. */
class NightlyReviewWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        NightlyReviewScheduler(applicationContext).disable()
        return Result.success()
    }
}
