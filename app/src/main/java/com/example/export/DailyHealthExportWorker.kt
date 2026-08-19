package com.example.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.RealHealthConnectRepository
import java.time.Clock
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

        val export = PreviousDayExportTask(
            healthRepository = healthRepository,
            writer = writer,
            clock = Clock.systemDefaultZone(),
            zoneId = ZoneId.systemDefault()
        ).run()

        return export.fold(
            onSuccess = { fileName ->
                writer.recordAutomaticExportStatus("Última exportación automática completada: $fileName")
                Result.success()
            },
            onFailure = { error ->
                writer.recordAutomaticExportStatus(
                    "Última exportación automática falló: ${error.localizedMessage ?: "error desconocido"}"
                )
                Result.retry()
            }
        )
    }
}
