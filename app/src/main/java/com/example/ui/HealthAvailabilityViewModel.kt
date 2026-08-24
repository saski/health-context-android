package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.HealthUiState
import com.example.data.model.SelectedDayTab
import com.example.data.repository.HealthConnectRepository
import com.example.export.DailyContextExportRepository
import com.example.export.DailyExportScheduler
import com.example.export.SnapshotStage
import com.example.review.NightlyReviewFeedback
import com.example.review.NightlyFeeling
import com.example.review.NightlyReviewGenerator
import com.example.review.NightlyReviewNotifier
import com.example.review.NightlyReviewScheduler
import com.example.review.NightlyReviewStore
import com.example.review.NightlyReviewTask
import com.example.review.loadRecentReports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class HealthAvailabilityViewModel(
    private val repository: HealthConnectRepository,
    private val exportRepository: DailyContextExportRepository,
    private val exportScheduler: DailyExportScheduler,
    private val nightlyReviewStore: NightlyReviewStore,
    private val nightlyReviewScheduler: NightlyReviewScheduler,
    private val nightlyReviewNotifier: NightlyReviewNotifier,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        checkSdkStatus()
        _uiState.update {
            it.copy(
                exportFolderConfigured = exportRepository.isConfigured(),
                automaticExportEnabled = exportRepository.isAutomaticExportEnabled(),
                automaticExportStatus = exportRepository.automaticExportStatus(),
                nightlyReviewEnabled = nightlyReviewStore.isEnabled(),
                nightlyReviewStatus = nightlyReviewStore.status(),
                latestNightlyReview = nightlyReviewStore.latest(),
                nightlyReviewFeedback = nightlyReviewStore.latest()?.let { review ->
                    nightlyReviewStore.feedback(review.date)
                },
                nightlyFeeling = nightlyReviewStore.latest()?.let { review ->
                    nightlyReviewStore.feeling(review.date)
                },
                backgroundReadAvailable = repository.isBackgroundReadAvailable()
            )
        }
        if (exportRepository.isAutomaticExportEnabled()) exportScheduler.enable()
        if (nightlyReviewStore.isEnabled()) nightlyReviewScheduler.enable()
    }

    fun checkSdkStatus() {
        val status = repository.getSdkAvailability()
        _uiState.update { it.copy(sdkAvailability = status) }
    }

    fun selectTab(tab: SelectedDayTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun showDataBoundaries(show: Boolean) {
        _uiState.update { it.copy(showDataBoundaries = show) }
    }

    fun getRequiredPermissions(): Set<String> {
        return repository.getRequiredPermissions()
    }

    fun getBackgroundReadPermission(): String = repository.getBackgroundReadPermission()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val sdkStatus = repository.getSdkAvailability()
                val granted = repository.getGrantedPermissions()

                val todayDate = LocalDate.now(zoneId)
                val yesterdayDate = todayDate.minusDays(1)

                val todayReport = repository.loadDayAvailability(todayDate, zoneId)
                val yesterdayReport = repository.loadDayAvailability(yesterdayDate, zoneId)

                _uiState.update {
                    it.copy(
                        sdkAvailability = sdkStatus,
                        grantedPermissions = granted,
                        requiredPermissionsGranted = granted.containsAll(repository.getRequiredPermissions()),
                        todayReport = todayReport,
                        yesterdayReport = yesterdayReport,
                        backgroundReadAvailable = repository.isBackgroundReadAvailable(),
                        backgroundReadPermissionGranted = repository.getBackgroundReadPermission() in granted,
                        automaticExportEnabled = exportRepository.isAutomaticExportEnabled(),
                        automaticExportStatus = exportRepository.automaticExportStatus(),
                        nightlyReviewEnabled = nightlyReviewStore.isEnabled(),
                        nightlyReviewStatus = nightlyReviewStore.status(),
                        latestNightlyReview = nightlyReviewStore.latest(),
                        nightlyReviewFeedback = nightlyReviewStore.latest()?.let { review ->
                            nightlyReviewStore.feedback(review.date)
                        },
                        nightlyFeeling = nightlyReviewStore.latest()?.let { review ->
                            nightlyReviewStore.feeling(review.date)
                        },
                        lastRefreshed = Instant.now(),
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = "Error al leer Health Connect: ${e.localizedMessage ?: "desconocido"}"
                    )
                }
            }
        }
    }

    fun saveExportFolder(uri: Uri, flags: Int) {
        exportRepository.saveFolder(uri, flags).onSuccess {
            _uiState.update { it.copy(exportFolderConfigured = true, exportMessage = "Carpeta Health context configurada") }
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = "No se pudo guardar la carpeta: ${error.localizedMessage}") }
        }
    }

    fun handleBackgroundPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(backgroundReadPermissionGranted = granted) }
        if (granted) {
            enableAutomaticExport()
        } else {
            _uiState.update { it.copy(errorMessage = "La exportación automática necesita lectura de Health Connect en segundo plano") }
        }
    }

    fun enableAutomaticExport() {
        val state = _uiState.value
        when {
            !state.exportFolderConfigured -> {
                _uiState.update { it.copy(errorMessage = "Elige primero la carpeta Health context") }
            }
            !state.backgroundReadAvailable -> {
                _uiState.update { it.copy(errorMessage = "Este dispositivo no ofrece lectura de Health Connect en segundo plano") }
            }
            !state.backgroundReadPermissionGranted -> {
                _uiState.update { it.copy(errorMessage = "Concede el permiso de lectura en segundo plano para activar la automatización") }
            }
            else -> {
                exportRepository.setAutomaticExportEnabled(true)
                exportScheduler.enable()
                _uiState.update {
                    it.copy(
                        automaticExportEnabled = true,
                        automaticExportStatus = "Automática activa: exportará ayer cada mañana",
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun disableAutomaticExport() {
        exportScheduler.disable()
        exportRepository.setAutomaticExportEnabled(false)
        val nightlyWasEnabled = nightlyReviewStore.isEnabled()
        if (nightlyWasEnabled) {
            nightlyReviewScheduler.disable()
            nightlyReviewStore.setEnabled(false)
            nightlyReviewStore.recordStatus("Revisión nocturna pausada junto con la exportación diaria")
        }
        _uiState.update {
            it.copy(
                automaticExportEnabled = false,
                automaticExportStatus = "Exportación automática pausada",
                nightlyReviewEnabled = if (nightlyWasEnabled) false else it.nightlyReviewEnabled,
                nightlyReviewStatus = if (nightlyWasEnabled) "Revisión nocturna pausada junto con la exportación diaria" else it.nightlyReviewStatus,
                errorMessage = null
            )
        }
    }

    fun handleNightlyBackgroundPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(backgroundReadPermissionGranted = granted) }
        if (!granted) {
            _uiState.update { it.copy(errorMessage = "La revisión nocturna necesita lectura de Health Connect en segundo plano") }
        }
    }

    fun handleNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(errorMessage = "Concede notificaciones para recibir la revisión nocturna") }
        }
    }

    fun enableNightlyReview() {
        val state = _uiState.value
        when {
            !state.exportFolderConfigured -> {
                _uiState.update { it.copy(errorMessage = "Elige primero la carpeta Health context") }
            }
            !state.backgroundReadAvailable -> {
                _uiState.update { it.copy(errorMessage = "Este dispositivo no ofrece lectura de Health Connect en segundo plano") }
            }
            !state.backgroundReadPermissionGranted -> {
                _uiState.update { it.copy(errorMessage = "Concede la lectura en segundo plano para activar la revisión nocturna") }
            }
            else -> {
                exportRepository.setAutomaticExportEnabled(true)
                exportScheduler.enable()
                nightlyReviewStore.setEnabled(true)
                nightlyReviewScheduler.enable()
                val status = "Revisión nocturna activa: se preparará aproximadamente a las 22:30"
                nightlyReviewStore.recordStatus(status)
                _uiState.update {
                    it.copy(
                        automaticExportEnabled = true,
                        automaticExportStatus = "Automática activa: recalculará ayer cada mañana",
                        nightlyReviewEnabled = true,
                        nightlyReviewStatus = status,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun disableNightlyReview() {
        nightlyReviewScheduler.disable()
        nightlyReviewStore.setEnabled(false)
        val status = "Revisión nocturna pausada"
        nightlyReviewStore.recordStatus(status)
        _uiState.update {
            it.copy(nightlyReviewEnabled = false, nightlyReviewStatus = status, errorMessage = null)
        }
    }

    fun generateNightlyReviewNow() {
        if (!exportRepository.isConfigured()) {
            _uiState.update { it.copy(errorMessage = "Elige primero la carpeta Health context") }
            return
        }
        if (!_uiState.value.backgroundReadPermissionGranted) {
            _uiState.update { it.copy(errorMessage = "Concede primero la lectura de Health Connect en segundo plano") }
            return
        }
        if (!_uiState.value.backgroundReadAvailable) {
            _uiState.update { it.copy(errorMessage = "Este dispositivo no ofrece lectura de Health Connect en segundo plano") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isGeneratingNightlyReview = true, errorMessage = null) }
            val result = NightlyReviewTask(
                healthRepository = repository,
                writer = exportRepository,
                store = nightlyReviewStore,
                notifier = nightlyReviewNotifier,
                clock = clock,
                zoneId = zoneId
            ).run()
            result.onSuccess { fileName ->
                val status = "Revisión generada y exportada: $fileName"
                nightlyReviewStore.recordStatus(status)
                val review = nightlyReviewStore.latest()
                _uiState.update {
                    it.copy(
                        isGeneratingNightlyReview = false,
                        nightlyReviewStatus = status,
                        latestNightlyReview = review,
                        nightlyReviewFeedback = review?.let { item -> nightlyReviewStore.feedback(item.date) },
                        showNightlyReview = review != null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isGeneratingNightlyReview = false,
                        errorMessage = "No se pudo generar la revisión: ${error.localizedMessage ?: "error desconocido"}"
                    )
                }
            }
        }
    }

    fun showNightlyReview(show: Boolean) {
        _uiState.update { it.copy(showNightlyReview = show) }
    }

    fun openNightlyReview(date: LocalDate?) {
        val review = nightlyReviewStore.latest()
        if (review == null) {
            _uiState.update { it.copy(errorMessage = "Todavía no hay una revisión nocturna guardada") }
            return
        }
        if (date != null && review.date != date) {
            _uiState.update { it.copy(errorMessage = "La revisión de $date ya no está disponible en el teléfono") }
            return
        }
        _uiState.update {
            it.copy(
                latestNightlyReview = review,
                nightlyReviewFeedback = nightlyReviewStore.feedback(review.date),
                nightlyFeeling = nightlyReviewStore.feeling(review.date),
                showNightlyReview = true,
                errorMessage = null
            )
        }
    }

    fun recordNightlyReviewFeedback(feedback: NightlyReviewFeedback) {
        val review = _uiState.value.latestNightlyReview ?: return
        nightlyReviewStore.recordFeedback(review.date, feedback)
        _uiState.update { it.copy(nightlyReviewFeedback = feedback) }
    }

    fun recordNightlyFeeling(feeling: NightlyFeeling) {
        val review = _uiState.value.latestNightlyReview ?: return
        nightlyReviewStore.recordFeeling(review.date, feeling)
        _uiState.update { it.copy(nightlyFeeling = feeling) }
    }

    fun exportSelectedDay() {
        val report = if (_uiState.value.selectedTab == SelectedDayTab.TODAY) _uiState.value.todayReport else _uiState.value.yesterdayReport
        if (report == null) {
            _uiState.update { it.copy(errorMessage = "Actualiza Health Connect antes de exportar") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExporting = true, exportMessage = null, errorMessage = null) }
            val generatedAt = Instant.now()
            val recentReports = repository.loadRecentReports(report.date, zoneId)
            val review = NightlyReviewGenerator.generate(report, generatedAt, recentReports)
            val stage = if (report.date.isBefore(LocalDate.now(clock.withZone(zoneId)))) {
                SnapshotStage.FINAL
            } else {
                SnapshotStage.PROVISIONAL
            }
            exportRepository.export(report, generatedAt, review, stage).onSuccess { fileName ->
                _uiState.update { it.copy(isExporting = false, exportMessage = "Exportado localmente: $fileName") }
            }.onFailure { error ->
                _uiState.update { it.copy(isExporting = false, errorMessage = "No se pudo exportar: ${error.localizedMessage}") }
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: HealthConnectRepository,
            exportRepository: DailyContextExportRepository,
            exportScheduler: DailyExportScheduler,
            nightlyReviewStore: NightlyReviewStore,
            nightlyReviewScheduler: NightlyReviewScheduler,
            nightlyReviewNotifier: NightlyReviewNotifier,
            clock: Clock = Clock.systemDefaultZone(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthAvailabilityViewModel(
                    repository,
                    exportRepository,
                    exportScheduler,
                    nightlyReviewStore,
                    nightlyReviewScheduler,
                    nightlyReviewNotifier,
                    clock,
                    zoneId
                ) as T
            }
        }
    }
}
