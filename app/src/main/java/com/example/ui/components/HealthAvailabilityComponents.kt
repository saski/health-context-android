package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.HealthStatusMapper
import com.example.data.model.SdkAvailability
import com.example.ui.theme.CleanContainer
import com.example.ui.theme.CleanPrimary
import com.example.ui.theme.CleanPrimaryContainer
import com.example.ui.theme.CleanStatusAvailableBg
import com.example.ui.theme.CleanStatusAvailableText
import com.example.ui.theme.CleanStatusPartialAccent
import com.example.ui.theme.CleanStatusPartialBg
import com.example.ui.theme.CleanStatusPartialText
import com.example.ui.theme.CleanStatusPermissionBg
import com.example.ui.theme.CleanStatusPermissionBorder
import com.example.ui.theme.CleanStatusPermissionTagBg
import com.example.ui.theme.CleanStatusPermissionTagText
import com.example.ui.theme.CleanStatusPermissionText
import com.example.ui.theme.CleanStatusUnavailableBg
import com.example.ui.theme.CleanStatusUnavailableText
import com.example.ui.theme.CleanSurface
import com.example.ui.theme.CleanSurfaceBorder
import com.example.ui.theme.CleanTextPrimary
import com.example.ui.theme.CleanTextSecondary

@Composable
fun StatusBadge(
    status: HealthAvailabilityStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        HealthAvailabilityStatus.AVAILABLE -> Triple(
            CleanStatusAvailableBg,
            CleanStatusAvailableText,
            "DISPONIBLE"
        )
        HealthAvailabilityStatus.PARTIAL -> Triple(
            CleanStatusPartialBg,
            CleanStatusPartialText,
            "PARCIAL"
        )
        HealthAvailabilityStatus.UNAVAILABLE -> Triple(
            CleanStatusUnavailableBg,
            CleanStatusUnavailableText,
            "NO DISPONIBLE"
        )
        HealthAvailabilityStatus.PERMISSION_NEEDED -> Triple(
            CleanStatusPermissionTagBg,
            CleanStatusPermissionTagText,
            "PERMISO NECESARIO"
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .testTag("status_badge_${status.name.lowercase()}"),
        color = bgColor
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SdkStatusBanner(
    sdkAvailability: SdkAvailability,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (sdkAvailability == SdkAvailability.AVAILABLE) return

    val (title, description, buttonLabel, icon) = when (sdkAvailability) {
        SdkAvailability.UPDATE_REQUIRED -> Quad(
            "Actualización requerida",
            stringResource(R.string.hc_update_required),
            stringResource(R.string.open_play_store),
            Icons.Filled.Warning
        )
        SdkAvailability.UNAVAILABLE -> Quad(
            "Health Connect no disponible",
            stringResource(R.string.hc_unavailable),
            stringResource(R.string.open_play_store),
            Icons.Filled.ErrorOutline
        )
        else -> Quad(
            "Comprobar disponibilidad",
            "Verifica la compatibilidad de Health Connect en este dispositivo.",
            stringResource(R.string.check_availability),
            Icons.Filled.Info
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("sdk_status_banner"),
        color = CleanContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CleanTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CleanTextPrimary
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = CleanTextSecondary
            )
            Button(
                onClick = onConnectClick,
                modifier = Modifier.testTag("btn_connect_health_connect"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CleanPrimaryContainer,
                    contentColor = CleanStatusAvailableText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverallStatusCard(
    report: DayAvailabilityReport,
    timestampText: String? = null,
    modifier: Modifier = Modifier
) {
    val isComplete = report.overallStatus == HealthAvailabilityStatus.AVAILABLE

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .testTag("overall_status_card"),
        color = CleanContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Estado: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CleanTextPrimary
                    )
                    Text(
                        text = if (isComplete) "Completo" else "Parcial",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isComplete) CleanPrimary else CleanStatusPartialAccent
                    )
                }
                if (timestampText != null) {
                    Text(
                        text = timestampText,
                        fontSize = 11.sp,
                        color = CleanTextSecondary
                    )
                }
            }

            if (!isComplete && report.unavailableDomains.isNotEmpty()) {
                val missingCount = report.unavailableDomains.size
                Text(
                    text = "Faltan permisos o registros en $missingCount ${if (missingCount == 1) "dominio" else "dominios"}.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = CleanTextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    report.unavailableDomains.forEach { domain ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CleanSurface,
                            border = BorderStroke(1.dp, CleanSurfaceBorder)
                        ) {
                            Text(
                                text = domain.labelEs,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                color = CleanTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Todos los dominios representados tienen registros utilizables para esta fecha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CleanTextSecondary
                )
            }
        }
    }
}

@Composable
fun HealthDomainCard(
    availability: DomainAvailability,
    modifier: Modifier = Modifier
) {
    val icon = when (availability.domain) {
        HealthDomain.STEPS -> Icons.Filled.DirectionsWalk
        HealthDomain.EXERCISE -> Icons.Filled.FitnessCenter
        HealthDomain.SLEEP -> Icons.Filled.Nightlight
        HealthDomain.WEIGHT -> Icons.Filled.MonitorWeight
        HealthDomain.NUTRITION -> Icons.Filled.Restaurant
        HealthDomain.RESTING_HEART_RATE -> Icons.Filled.Favorite
    }

    val (iconBg, iconTint, cardBg, cardBorder, isDimmed) = when (availability.status) {
        HealthAvailabilityStatus.AVAILABLE -> Quint(
            CleanPrimaryContainer,
            CleanStatusAvailableText,
            CleanSurface,
            CleanSurfaceBorder,
            false
        )
        HealthAvailabilityStatus.UNAVAILABLE -> Quint(
            CleanStatusUnavailableBg,
            CleanStatusUnavailableText,
            CleanSurface,
            CleanSurfaceBorder,
            true
        )
        HealthAvailabilityStatus.PERMISSION_NEEDED -> Quint(
            CleanStatusPermissionBorder,
            CleanStatusPermissionText,
            CleanStatusPermissionBg,
            CleanStatusPermissionBorder,
            false
        )
        HealthAvailabilityStatus.PARTIAL -> Quint(
            CleanPrimaryContainer,
            CleanStatusAvailableText,
            CleanSurface,
            CleanSurfaceBorder,
            false
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .alpha(if (isDimmed) 0.65f else 1.0f)
            .testTag("domain_card_${availability.domain.name.lowercase()}"),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Square
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Top Row: Domain Title + Status Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = availability.domain.labelEs,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (availability.status == HealthAvailabilityStatus.PERMISSION_NEEDED) CleanStatusPermissionText else CleanTextPrimary
                    )
                    StatusBadge(status = availability.status)
                }

                // Row 1: Metric summary / source
                val primaryLine = buildString {
                    if (!availability.metricSummary.isNullOrBlank()) {
                        append(availability.metricSummary)
                        if (availability.source != HealthStatusMapper.SOURCE_NOT_AVAILABLE) {
                            append(" • ")
                            append(availability.source)
                        }
                    } else {
                        append(availability.source)
                    }
                }
                Text(
                    text = primaryLine,
                    fontSize = 11.sp,
                    color = if (availability.status == HealthAvailabilityStatus.PERMISSION_NEEDED) CleanStatusPermissionText else CleanTextSecondary,
                    maxLines = 1
                )

                // Row 2: Coverage / Reason
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = availability.reason,
                        fontSize = 11.sp,
                        color = if (availability.status == HealthAvailabilityStatus.UNAVAILABLE) CleanStatusPartialAccent else CleanTextSecondary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )
                    if (availability.coveredThrough != HealthStatusMapper.NO_USABLE_RECORD) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = availability.coveredThrough,
                            fontSize = 11.sp,
                            color = CleanTextSecondary
                        )
                    }
                }
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

@Composable
fun DataBoundariesFooterBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("btn_data_boundaries_footer"),
        color = CleanContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = CleanTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "LÍMITES DE DATOS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = CleanTextSecondary
                )
            }
            Text(
                text = "Lectura local y exportación diaria automática al proveedor de documentos elegido. Sin red propia, rutas GPS ni escritura en Health Connect.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = CleanTextSecondary
            )
        }
    }
}

@Composable
fun DataBoundariesDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = CleanPrimary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.data_boundaries_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = CleanTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.data_boundaries_statement),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CleanTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = CleanContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BoundaryItem("✓ Lectura amplia de actividad, entrenamientos, sueño, cuerpo, nutrición e indicadores.")
                        BoundaryItem("✓ Agregación diaria calculada en el dispositivo.")
                        BoundaryItem("✓ Exportación automática al proveedor de documentos elegido.")
                        BoundaryItem("✗ Sin permiso de Internet propio ni OAuth.")
                        BoundaryItem("✗ Sin acceso a rutas de ejercicio ni GPS.")
                        BoundaryItem("✗ Sin modificaciones ni escrituras en Health Connect.")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_data_boundaries"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CleanPrimaryContainer,
                    contentColor = CleanStatusAvailableText
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CleanSurface,
        modifier = modifier.testTag("dialog_data_boundaries")
    )
}

@Composable
private fun BoundaryItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CleanTextSecondary
    )
}
