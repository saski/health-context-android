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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthAvailabilityViewModel(
    private val repository: HealthConnectRepository,
    private val exportRepository: DailyContextExportRepository,
    private val exportScheduler: DailyExportScheduler,
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
                backgroundReadAvailable = repository.isBackgroundReadAvailable()
            )
        }
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
                        todayReport = todayReport,
                        yesterdayReport = yesterdayReport,
                        backgroundReadAvailable = repository.isBackgroundReadAvailable(),
                        backgroundReadPermissionGranted = repository.getBackgroundReadPermission() in granted,
                        automaticExportEnabled = exportRepository.isAutomaticExportEnabled(),
                        automaticExportStatus = exportRepository.automaticExportStatus(),
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
        _uiState.update {
            it.copy(
                automaticExportEnabled = false,
                automaticExportStatus = "Exportación automática pausada",
                errorMessage = null
            )
        }
    }

    fun exportSelectedDay() {
        val report = if (_uiState.value.selectedTab == SelectedDayTab.TODAY) _uiState.value.todayReport else _uiState.value.yesterdayReport
        if (report == null) {
            _uiState.update { it.copy(errorMessage = "Actualiza Health Connect antes de exportar") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExporting = true, exportMessage = null, errorMessage = null) }
            exportRepository.export(report, Instant.now()).onSuccess { fileName ->
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
            zoneId: ZoneId = ZoneId.systemDefault()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthAvailabilityViewModel(repository, exportRepository, exportScheduler, zoneId) as T
            }
        }
    }
}
