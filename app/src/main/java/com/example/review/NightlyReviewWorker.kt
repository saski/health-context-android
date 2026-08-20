package com.example.review

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.RealHealthConnectRepository
import com.example.export.DailyContextExportRepository
import java.time.Clock
import java.time.ZoneId

class NightlyReviewWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = SharedPreferencesNightlyReviewStore(applicationContext)
        if (!store.isEnabled()) return Result.success()

        val writer = DailyContextExportRepository(applicationContext)
        if (!writer.isConfigured()) {
            store.recordStatus("Revisión nocturna detenida: vuelve a elegir la carpeta Health context")
            return Result.failure()
        }

        val healthRepository = RealHealthConnectRepository(applicationContext)
        val granted = healthRepository.getGrantedPermissions()
        if (!healthRepository.isBackgroundReadAvailable() || healthRepository.getBackgroundReadPermission() !in granted) {
            store.recordStatus("Revisión nocturna detenida: falta la lectura de Health Connect en segundo plano")
            return Result.failure()
        }
        if (!canNotify()) {
            store.recordStatus("Revisión nocturna detenida: faltan permisos de notificación")
            return Result.failure()
        }

        val result = NightlyReviewTask(
            healthRepository = healthRepository,
            writer = writer,
            store = store,
            notifier = AndroidNightlyReviewNotifier(applicationContext),
            clock = Clock.systemDefaultZone(),
            zoneId = ZoneId.systemDefault()
        ).run()

        return result.fold(
            onSuccess = { fileName ->
                store.recordStatus("Última revisión nocturna completada: $fileName")
                Result.success()
            },
            onFailure = { error ->
                store.recordStatus("La revisión nocturna falló: ${error.localizedMessage ?: "error desconocido"}")
                Result.retry()
            }
        )
    }

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
