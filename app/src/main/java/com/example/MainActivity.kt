package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.RealHealthConnectRepository
import com.example.export.DailyContextExportRepository
import com.example.export.DailyExportScheduler
import com.example.ui.HealthAvailabilityScreen
import com.example.ui.HealthAvailabilityViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HealthAvailabilityViewModel by viewModels {
        HealthAvailabilityViewModel.provideFactory(
            RealHealthConnectRepository(applicationContext),
            DailyContextExportRepository(applicationContext),
            DailyExportScheduler(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) {
                    viewModel.refresh()
                }
                val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    viewModel.handleBackgroundPermissionResult(
                        viewModel.getBackgroundReadPermission() in granted
                    )
                }
                val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    result.data?.data?.let { uri -> viewModel.saveExportFolder(uri, result.data?.flags ?: 0) }
                }

                HealthAvailabilityScreen(
                    uiState = uiState,
                    onRefresh = { viewModel.refresh() },
                    onSelectTab = { viewModel.selectTab(it) },
                    onRequestPermissions = {
                        val required = viewModel.getRequiredPermissions()
                        permissionLauncher.launch(required)
                    },
                    onOpenPlayStoreOrSettings = {
                        openHealthConnectSettingsOrStore()
                    },
                    onShowDataBoundaries = { viewModel.showDataBoundaries(it) },
                    onChooseExportFolder = {
                        folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        ))
                    },
                    onExport = { viewModel.exportSelectedDay() },
                    onToggleAutomaticExport = {
                        if (uiState.automaticExportEnabled) {
                            viewModel.disableAutomaticExport()
                        } else if (uiState.backgroundReadPermissionGranted) {
                            viewModel.enableAutomaticExport()
                        } else {
                            backgroundPermissionLauncher.launch(setOf(viewModel.getBackgroundReadPermission()))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openHealthConnectSettingsOrStore() {
        try {
            // Intentar abrir la pantalla de ajustes de Health Connect
            val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            startActivity(settingsIntent)
        } catch (e: Exception) {
            try {
                // Si no está disponible como configuración del sistema, abrir Play Store
                val playStoreIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                )
                startActivity(playStoreIntent)
            } catch (e2: Exception) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                )
                startActivity(browserIntent)
            }
        }
    }
}
