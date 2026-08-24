package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.DayAvailabilityReport
import com.example.review.NightlyReview
import java.time.Instant

interface DailyContextWriter {
    fun export(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        review: NightlyReview? = null,
        stage: SnapshotStage = SnapshotStage.FINAL
    ): Result<String>
}

class DailyContextExportRepository(private val context: Context) : DailyContextWriter {
    private val preferences = context.getSharedPreferences("daily_context_export", Context.MODE_PRIVATE)
    private val folderKey = "document_tree_uri"
    private val automaticExportKey = "automatic_export_enabled"
    private val automaticStatusKey = "automatic_export_status"

    fun isConfigured(): Boolean = configuredUri() != null

    fun saveFolder(uri: Uri, grantedFlags: Int): Result<Unit> = runCatching {
        val needed = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = grantedFlags and needed
        check(granted == needed) { "Android no concedió acceso de lectura y escritura a la carpeta" }
        context.contentResolver.takePersistableUriPermission(uri, granted)
        preferences.edit().putString(folderKey, uri.toString()).apply()
    }

    fun isAutomaticExportEnabled(): Boolean = preferences.getBoolean(automaticExportKey, false)

    fun setAutomaticExportEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(automaticExportKey, enabled).apply()
    }

    fun automaticExportStatus(): String? = preferences.getString(automaticStatusKey, null)

    fun recordAutomaticExportStatus(status: String) {
        preferences.edit().putString(automaticStatusKey, status).apply()
    }

    fun existingArchiveDates(): Result<Set<java.time.LocalDate>> = runCatching {
        val treeUri = configuredUri() ?: error("Elige primero la carpeta Health context")
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("La carpeta elegida ya no está disponible")
        DailyContextArchiveDates.parse(tree.listFiles().mapNotNull { it.name })
    }

    override fun export(
        report: DayAvailabilityReport,
        generatedAt: Instant,
        review: NightlyReview?,
        stage: SnapshotStage
    ): Result<String> = runCatching {
        val treeUri = configuredUri() ?: error("Elige primero la carpeta Health context")
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("La carpeta elegida ya no está disponible")
        val artifacts = DailyContextArtifacts.create(report, generatedAt, review, stage)
        artifacts.forEach { artifact ->
            val file = tree.findFile(artifact.fileName)
                ?: tree.createFile("text/markdown", artifact.fileName)
                ?: error("No se pudo crear ${artifact.fileName}")
            context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use {
                it.write(artifact.content)
            } ?: error("No se pudo escribir ${artifact.fileName}")
        }
        artifacts.first().fileName
    }

    private fun configuredUri(): Uri? = preferences.getString(folderKey, null)?.let(Uri::parse)
}
