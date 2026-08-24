package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.data.repository.RealHealthConnectRepository
import com.example.export.DailyContextExportRepository
import com.example.export.DailyExportScheduler
import com.example.review.AndroidNightlyReviewNotifier
import com.example.review.NightlyReviewScheduler
import com.example.review.SharedPreferencesNightlyReviewStore
import com.example.ui.HealthAvailabilityScreen
import com.example.ui.HealthAvailabilityViewModel
import com.example.ui.theme.MyApplicationTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: HealthAvailabilityViewModel by viewModels {
        HealthAvailabilityViewModel.provideFactory(
            RealHealthConnectRepository(applicationContext),
            DailyContextExportRepository(applicationContext),
            DailyExportScheduler(applicationContext),
            SharedPreferencesNightlyReviewStore(applicationContext),
            NightlyReviewScheduler(applicationContext),
            AndroidNightlyReviewNotifier(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNightlyReviewIntent(intent)

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var pendingNightlyAction by remember { mutableStateOf(NightlyPermissionAction.ENABLE) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { }
                val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    viewModel.handleBackgroundPermissionResult(
                        viewModel.getBackgroundReadPermission() in granted
                    )
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        when (pendingNightlyAction) {
                            NightlyPermissionAction.ENABLE -> viewModel.enableNightlyReview()
                            NightlyPermissionAction.GENERATE -> viewModel.generateNightlyReviewNow()
                        }
                    }
                    viewModel.handleNotificationPermissionResult(granted)
                }
                val nightlyBackgroundPermissionLauncher = rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    val backgroundGranted = viewModel.getBackgroundReadPermission() in granted
                    viewModel.handleNightlyBackgroundPermissionResult(backgroundGranted)
                    if (backgroundGranted) {
                        if (notificationPermissionGranted()) {
                            viewModel.enableNightlyReview()
                        } else {
                            pendingNightlyAction = NightlyPermissionAction.ENABLE
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
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
                    onManagePermissions = {
                        openHealthConnectSettingsOrStore()
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
                    },
                    onToggleNightlyReview = {
                        if (uiState.nightlyReviewEnabled) {
                            viewModel.disableNightlyReview()
                        } else if (!uiState.backgroundReadPermissionGranted) {
                            nightlyBackgroundPermissionLauncher.launch(setOf(viewModel.getBackgroundReadPermission()))
                        } else if (!notificationPermissionGranted()) {
                            pendingNightlyAction = NightlyPermissionAction.ENABLE
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.enableNightlyReview()
                        }
                    },
                    onGenerateNightlyReviewNow = {
                        if (notificationPermissionGranted()) {
                            viewModel.generateNightlyReviewNow()
                        } else {
                            pendingNightlyAction = NightlyPermissionAction.GENERATE
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onShowNightlyReview = { viewModel.showNightlyReview(it) },
                    onNightlyReviewFeedback = { viewModel.recordNightlyReviewFeedback(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNightlyReviewIntent(intent)
    }

    private fun handleNightlyReviewIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_NIGHTLY_REVIEW) {
            val date = intent.getStringExtra(EXTRA_REVIEW_DATE)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            viewModel.openNightlyReview(date)
        }
    }

    private fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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

    companion object {
        const val ACTION_OPEN_NIGHTLY_REVIEW = "com.example.action.OPEN_NIGHTLY_REVIEW"
        const val EXTRA_REVIEW_DATE = "review_date"
    }

    private enum class NightlyPermissionAction {
        ENABLE,
        GENERATE
    }
}
