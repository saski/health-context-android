package com.example.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.RealHealthConnectRepository
import com.example.review.AndroidNightlyReviewNotifier
import com.example.review.NightlyReviewTask
import com.example.review.SharedPreferencesNightlyReviewStore
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class DailyHealthExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val writer = DailyContextExportRepository(applicationContext)
        if (!writer.isAutomaticExportEnabled()) return Result.success()
        if (!writer.isConfigured()) {
            writer.recordAutomaticExportStatus("Automatización detenida: vuelve a elegir la carpeta Health context")
            return Result.failure()
        }

        val healthRepository = RealHealthConnectRepository(applicationContext)
        val backgroundPermission = healthRepository.getBackgroundReadPermission()
        val granted = healthRepository.getGrantedPermissions()
        if (!healthRepository.isBackgroundReadAvailable() || backgroundPermission !in granted) {
            writer.recordAutomaticExportStatus("Automatización detenida: falta el permiso de lectura en segundo plano")
            return Result.failure()
        }

        val clock = Clock.systemDefaultZone()
        val zoneId = ZoneId.systemDefault()
        val reviewStore = SharedPreferencesNightlyReviewStore(applicationContext)
        val exportTask = PreviousDayExportTask(
            healthRepository = healthRepository,
            writer = writer,
            reviewStore = reviewStore,
            clock = clock,
            zoneId = zoneId
        )
        val export = runCatching {
            val today = LocalDate.now(clock.withZone(zoneId))
            val archiveDates = writer.existingArchiveDates().getOrThrow()
            val dates = ExportRecoveryPolicy.datesToExport(today, archiveDates)
            dates.dropLast(1).forEach { date -> exportTask.run(date).getOrThrow() }
            val fileName = if (reviewStore.isEnabled()) {
                val reviewedFile = NightlyReviewTask(
                    healthRepository = healthRepository,
                    writer = writer,
                    store = reviewStore,
                    notifier = AndroidNightlyReviewNotifier(applicationContext),
                    clock = clock,
                    zoneId = zoneId
                ).run().getOrThrow()
                reviewStore.recordStatus("Revisión de ayer enviada alrededor de las 09:00")
                reviewedFile
            } else {
                exportTask.run(dates.last()).getOrThrow()
            }
            fileName
        }

        return export.fold(
            onSuccess = { fileName ->
                writer.recordAutomaticExportStatus("Última exportación automática completada: $fileName")
                Result.success()
            },
            onFailure = { error ->
                if (reviewStore.isEnabled()) {
                    reviewStore.recordStatus(
                        "La revisión diaria falló: ${error.localizedMessage ?: "error desconocido"}"
                    )
                }
                writer.recordAutomaticExportStatus(
                    "Última exportación automática falló: ${error.localizedMessage ?: "error desconocido"}"
                )
                Result.retry()
            }
        )
    }
}
