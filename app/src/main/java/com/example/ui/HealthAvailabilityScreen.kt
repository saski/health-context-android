package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HealthUiState
import com.example.data.model.SdkAvailability
import com.example.data.model.SelectedDayTab
import com.example.data.repository.FakeHealthConnectRepository
import com.example.review.NightlyReview
import com.example.review.NightlyReviewFeedback
import com.example.ui.components.DataBoundariesDialog
import com.example.ui.components.DataBoundariesFooterBox
import com.example.ui.components.HealthDomainCard
import com.example.ui.components.OverallStatusCard
import com.example.ui.components.SdkStatusBanner
import com.example.ui.theme.CleanBackground
import com.example.ui.theme.CleanContainer
import com.example.ui.theme.CleanPrimaryContainer
import com.example.ui.theme.CleanStatusAvailableText
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanTextPrimary
import com.example.ui.theme.CleanTextSecondary
import com.example.ui.theme.MyApplicationTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthAvailabilityScreen(
    uiState: HealthUiState,
    onRefresh: () -> Unit,
    onSelectTab: (SelectedDayTab) -> Unit,
    onRequestPermissions: () -> Unit,
    onManagePermissions: () -> Unit,
    onOpenPlayStoreOrSettings: () -> Unit,
    onShowDataBoundaries: (Boolean) -> Unit,
    onChooseExportFolder: () -> Unit,
    onExport: () -> Unit,
    onToggleAutomaticExport: () -> Unit,
    onToggleNightlyReview: () -> Unit,
    onGenerateNightlyReviewNow: () -> Unit,
    onShowNightlyReview: (Boolean) -> Unit,
    onNightlyReviewFeedback: (NightlyReviewFeedback) -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier
) {
    val latestReview = uiState.latestNightlyReview
    if (uiState.showNightlyReview && latestReview != null) {
        NightlyReviewScreen(
            review = latestReview,
            feedback = uiState.nightlyReviewFeedback,
            onBack = { onShowNightlyReview(false) },
            onFeedback = onNightlyReviewFeedback,
            modifier = modifier
        )
        return
    }

    val timeFormatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(zoneId)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("health_availability_screen"),
        containerColor = CleanBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Salud Disponibilidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CleanTextPrimary
                        )
                        Text(
                            text = "HEALTH CONNECT LOCAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = CleanTextSecondary
                        )
                    }
                },
                actions = {
                    // Refresh Button (Clean Minimalism Styled)
                    Button(
                        onClick = onRefresh,
                        enabled = !uiState.isRefreshing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CleanPrimaryContainer,
                            contentColor = CleanStatusAvailableText
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("btn_refresh")
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CleanStatusAvailableText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Actualizando…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CleanStatusAvailableText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.refresh),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = { onShowDataBoundaries(true) },
                        modifier = Modifier.testTag("btn_data_boundaries_action")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.data_boundaries_title),
                            tint = CleanTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CleanBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SDK Availability Banner (if not installed / update needed)
            if (uiState.sdkAvailability != SdkAvailability.AVAILABLE) {
                item {
                    SdkStatusBanner(
                        sdkAvailability = uiState.sdkAvailability,
                        onConnectClick = onOpenPlayStoreOrSettings
                    )
                }
            }

            // Permissions action if permissions are missing
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.timezone_label, zoneId.id),
                        fontSize = 11.sp,
                        color = CleanTextSecondary
                    )
                    FilledTonalButton(
                        onClick = if (uiState.requiredPermissionsGranted) {
                            onManagePermissions
                        } else {
                            onRequestPermissions
                        },
                        modifier = Modifier.testTag("btn_request_permissions"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CleanContainer,
                            contentColor = if (uiState.requiredPermissionsGranted) {
                                CleanStatusAvailableText
                            } else {
                                CleanTextPrimary
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.requiredPermissionsGranted) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Shield
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                if (uiState.requiredPermissionsGranted) {
                                    R.string.manage_permissions
                                } else {
                                    R.string.grant_permissions
                                }
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Error banner if any
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Overall Summary Card
            val currentReport = if (uiState.selectedTab == SelectedDayTab.TODAY) {
                uiState.todayReport
            } else {
                uiState.yesterdayReport
            }

            if (currentReport != null) {
                item {
                    val timestampStr = uiState.lastRefreshed?.let {
                        val dayLabel = if (uiState.selectedTab == SelectedDayTab.TODAY) "Hoy" else "Ayer"
                        "$dayLabel, ${timeFormatter.format(it)}"
                    }
                    OverallStatusCard(
                        report = currentReport,
                        timestampText = timestampStr
                    )
                }

                // Clean Minimalism Nav Pill Tab Bar
                item {
                    CleanDayNavigation(
                        selectedTab = uiState.selectedTab,
                        onSelectTab = onSelectTab
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sincronización diaria",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CleanTextPrimary
                        )
                        Text(
                            text = if (uiState.automaticExportEnabled) {
                                "Activa: cada mañana exportará automáticamente el día anterior. Android puede retrasar la hora exacta."
                            } else {
                                "Elige la carpeta Health context y activa una vez la lectura en segundo plano."
                            },
                            fontSize = 12.sp,
                            color = CleanTextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onChooseExportFolder, modifier = Modifier.weight(1f)) {
                                Text(if (uiState.exportFolderConfigured) "Cambiar carpeta" else "Elegir carpeta")
                            }
                            Button(
                                onClick = onToggleAutomaticExport,
                                enabled = uiState.exportFolderConfigured && uiState.backgroundReadAvailable,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (uiState.automaticExportEnabled) "Pausar automático" else "Activar automático")
                            }
                        }
                        FilledTonalButton(
                            onClick = onExport,
                            enabled = uiState.exportFolderConfigured && !uiState.isExporting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedDay = if (uiState.selectedTab == SelectedDayTab.TODAY) "hoy" else "ayer"
                            Text(if (uiState.isExporting) "Exportando…" else "Exportar $selectedDay ahora")
                        }
                        if (!uiState.backgroundReadAvailable) {
                            Text(
                                "La lectura automática no está disponible en este dispositivo.",
                                fontSize = 12.sp,
                                color = CleanTextSecondary
                            )
                        }
                        uiState.automaticExportStatus?.let { Text(it, fontSize = 12.sp, color = CleanTextSecondary) }
                        uiState.exportMessage?.let { Text(it, fontSize = 12.sp, color = CleanTextSecondary) }
                    }
                }

                item {
                    NightlyReviewControls(
                        enabled = uiState.nightlyReviewEnabled,
                        canEnable = uiState.exportFolderConfigured && uiState.backgroundReadAvailable,
                        ready = uiState.exportFolderConfigured && uiState.backgroundReadPermissionGranted,
                        running = uiState.isGeneratingNightlyReview,
                        status = uiState.nightlyReviewStatus,
                        hasReview = uiState.latestNightlyReview != null,
                        onToggle = onToggleNightlyReview,
                        onGenerateNow = onGenerateNightlyReviewNow,
                        onOpenLatest = { onShowNightlyReview(true) }
                    )
                }

                // 5 Domain Cards
                items(
                    items = currentReport.domains,
                    key = { it.domain.name }
                ) { domainAvailability ->
                    HealthDomainCard(availability = domainAvailability)
                }
            } else if (!uiState.isRefreshing) {
                // Empty state before first refresh
                item {
                    EmptyStateCard(
                        onRefresh = onRefresh,
                        onRequestPermissions = onRequestPermissions
                    )
                }
            }

            // Footer info box
            item {
                Spacer(modifier = Modifier.height(4.dp))
                DataBoundariesFooterBox(onClick = { onShowDataBoundaries(true) })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (uiState.showDataBoundaries) {
        DataBoundariesDialog(onDismiss = { onShowDataBoundaries(false) })
    }
}

@Composable
private fun NightlyReviewControls(
    enabled: Boolean,
    canEnable: Boolean,
    ready: Boolean,
    running: Boolean,
    status: String?,
    hasReview: Boolean,
    onToggle: () -> Unit,
    onGenerateNow: () -> Unit,
    onOpenLatest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CleanSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Revisión nocturna", fontWeight = FontWeight.Bold, color = CleanTextPrimary)
            Text(
                if (enabled) "Activa · aproximadamente a las 22:30" else "Resumen factual y dos posibles acciones para mañana",
                style = MaterialTheme.typography.bodySmall,
                color = CleanTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle, enabled = canEnable, modifier = Modifier.weight(1f)) {
                    Text(if (enabled) "Pausar" else "Activar")
                }
                FilledTonalButton(
                    onClick = onGenerateNow,
                    enabled = ready && !running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (running) "Revisando…" else "Revisar ahora")
                }
            }
            if (hasReview) {
                FilledTonalButton(onClick = onOpenLatest, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver última revisión")
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CleanTextSecondary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightlyReviewScreen(
    review: NightlyReview,
    feedback: NightlyReviewFeedback?,
    onBack: () -> Unit,
    onFeedback: (NightlyReviewFeedback) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("nightly_review_screen"),
        containerColor = CleanBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Revisión del día", fontWeight = FontWeight.SemiBold)
                        Text(review.date.toString(), fontSize = 11.sp, color = CleanTextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CleanBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanPrimaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        review.summary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item { ReviewSection("Qué significa", review.facts, CleanStatusAvailableText) }
            item { ReviewSection("Evolución y confianza", review.gaps, CleanTextSecondary) }
            item { ReviewSection("Sugerencias", review.nextActions, MaterialTheme.colorScheme.primary) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("¿Te ha servido?", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onFeedback(NightlyReviewFeedback.USEFUL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (feedback == NightlyReviewFeedback.USEFUL) "Sí · guardado" else "Sí")
                            }
                            FilledTonalButton(
                                onClick = { onFeedback(NightlyReviewFeedback.NOT_USEFUL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (feedback == NightlyReviewFeedback.NOT_USEFUL) "No · guardado" else "No")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReviewSection(title: String, items: List<String>, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CleanSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = accent)
            if (items.isEmpty()) {
                Text("Nada que señalar con este snapshot.", style = MaterialTheme.typography.bodyMedium, color = CleanTextSecondary)
            } else {
                items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = CleanTextPrimary) }
            }
        }
    }
}

@Composable
private fun CleanDayNavigation(
    selectedTab: SelectedDayTab,
    onSelectTab: (SelectedDayTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .testTag("day_tabs"),
        color = CleanContainer
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hoy Pill
            val isToday = selectedTab == SelectedDayTab.TODAY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isToday) CleanSurface else Color.Transparent)
                    .clickable { onSelectTab(SelectedDayTab.TODAY) }
                    .padding(vertical = 8.dp)
                    .testTag("tab_today"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.today),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isToday) CleanTextPrimary else CleanTextSecondary
                )
            }

            // Ayer Pill
            val isYesterday = selectedTab == SelectedDayTab.YESTERDAY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isYesterday) CleanSurface else Color.Transparent)
                    .clickable { onSelectTab(SelectedDayTab.YESTERDAY) }
                    .padding(vertical = 8.dp)
                    .testTag("tab_yesterday"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.yesterday),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isYesterday) CleanTextPrimary else CleanTextSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("empty_state_card"),
        color = CleanSurface
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CleanPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = CleanStatusAvailableText,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Inspección local lista",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CleanTextPrimary
            )
            Text(
                text = "Pulsa 'Actualizar' para consultar actividad, entrenamientos, sueño, cuerpo, nutrición e indicadores de Health Connect.",
                style = MaterialTheme.typography.bodySmall,
                color = CleanTextSecondary
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.testTag("btn_empty_refresh"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CleanPrimaryContainer,
                    contentColor = CleanStatusAvailableText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.refresh), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HealthAvailabilityScreenPreview() {
    MyApplicationTheme {
        val repo = FakeHealthConnectRepository()
        HealthAvailabilityScreen(
            uiState = HealthUiState(
                sdkAvailability = SdkAvailability.AVAILABLE,
                todayReport = com.example.data.model.HealthStatusMapper.buildDayReport(
                    java.time.LocalDate.now(),
                    ZoneId.systemDefault(),
                    emptyList()
                )
            ),
            onRefresh = {},
            onSelectTab = {},
            onRequestPermissions = {},
            onManagePermissions = {},
            onOpenPlayStoreOrSettings = {},
            onShowDataBoundaries = {},
            onChooseExportFolder = {},
            onExport = {},
            onToggleAutomaticExport = {},
            onToggleNightlyReview = {},
            onGenerateNightlyReviewNow = {},
            onShowNightlyReview = {},
            onNightlyReviewFeedback = {}
        )
    }
}
