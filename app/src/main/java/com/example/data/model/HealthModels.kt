package com.example.data.model

import com.example.review.NightlyReview
import com.example.review.NightlyReviewFeedback
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Representa el estado de disponibilidad según las pautas de Health Connect.
 */
enum class HealthAvailabilityStatus {
    AVAILABLE,         // Disponible
    PARTIAL,           // Parcial
    UNAVAILABLE,       // No disponible
    PERMISSION_NEEDED  // Permiso requerido
}

/**
 * Dominios compactos de la interfaz. Cada uno puede contener varias métricas.
 */
enum class HealthDomain(val labelEs: String) {
    STEPS("Actividad diaria"),
    EXERCISE("Entrenamientos"),
    SLEEP("Sueño"),
    WEIGHT("Cuerpo"),
    NUTRITION("Nutrición"),
    RESTING_HEART_RATE("Indicadores")
}

/** Una observación concreta dentro de un dominio, con procedencia y huecos explícitos. */
data class MetricAvailability(
    val key: String,
    val label: String,
    val status: HealthAvailabilityStatus,
    val source: String,
    val coveredThrough: String,
    val reason: String,
    val observation: String? = null
)

/**
 * Estado y metadatos de disponibilidad de un dominio específico.
 */
data class DomainAvailability(
    val domain: HealthDomain,
    val status: HealthAvailabilityStatus,
    val source: String,           // Etiqueta/origen del registro o "Fuente no disponible en esta lectura"
    val coveredThrough: String,   // Período o marca de tiempo de cobertura o "Sin registro utilizable"
    val reason: String,           // Motivo fáctico del estado
    val metricSummary: String? = null, // Resumen descriptivo opcional (sin interpretación médica)
    val metrics: List<MetricAvailability> = emptyList()
)

/**
 * Informe de disponibilidad para un día local del calendario.
 */
data class DayAvailabilityReport(
    val date: LocalDate,
    val zoneId: ZoneId,
    val domains: List<DomainAvailability>,
    val overallStatus: HealthAvailabilityStatus,
    val unavailableDomains: List<HealthDomain>
)

/**
 * Estado general del SDK de Health Connect en el dispositivo.
 */
enum class SdkAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
    UNKNOWN
}

/**
 * Estado completo de la interfaz de usuario.
 */
data class HealthUiState(
    val sdkAvailability: SdkAvailability = SdkAvailability.UNKNOWN,
    val grantedPermissions: Set<String> = emptySet(),
    val requiredPermissionsGranted: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastRefreshed: Instant? = null,
    val selectedTab: SelectedDayTab = SelectedDayTab.TODAY,
    val todayReport: DayAvailabilityReport? = null,
    val yesterdayReport: DayAvailabilityReport? = null,
    val exportFolderConfigured: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val backgroundReadAvailable: Boolean = false,
    val backgroundReadPermissionGranted: Boolean = false,
    val automaticExportEnabled: Boolean = false,
    val automaticExportStatus: String? = null,
    val nightlyReviewEnabled: Boolean = false,
    val nightlyReviewStatus: String? = null,
    val latestNightlyReview: NightlyReview? = null,
    val nightlyReviewFeedback: NightlyReviewFeedback? = null,
    val isGeneratingNightlyReview: Boolean = false,
    val showNightlyReview: Boolean = false,
    val showDataBoundaries: Boolean = false,
    val errorMessage: String? = null
)

enum class SelectedDayTab {
    TODAY,
    YESTERDAY
}

/**
 * Datos brutos intermedios recuperados para evaluar la disponibilidad de forma pura y testeable.
 */
data class StepsDomainData(
    val isPermissionGranted: Boolean,
    val totalSteps: Long?,
    val dataOrigins: Set<String> = emptySet()
)

data class GenericRecordData(
    val isPermissionGranted: Boolean,
    val hasRecord: Boolean,
    val recordTimestamp: Instant? = null,
    val recordEndTime: Instant? = null,
    val dataOriginPackage: String? = null,
    val extraFactualInfo: String? = null
)

object HealthStatusMapper {

    const val SOURCE_NOT_AVAILABLE = "Fuente no disponible en esta lectura"
    const val NO_USABLE_RECORD = "Sin registro utilizable"

    /** Agrupa métricas heterogéneas sin ocultar permisos ausentes ni registros faltantes. */
    fun mapMetricDomain(
        domain: HealthDomain,
        metrics: List<MetricAvailability>,
        noDataReason: String,
        optionalKeys: Set<String> = emptySet()
    ): DomainAvailability {
        val relevantMetrics = metrics.filterNot { metric ->
            metric.key in optionalKeys && metric.status != HealthAvailabilityStatus.AVAILABLE
        }
        val available = relevantMetrics.filter { it.status == HealthAvailabilityStatus.AVAILABLE }
        val permissionNeeded = relevantMetrics.filter { it.status == HealthAvailabilityStatus.PERMISSION_NEEDED }
        val status = when {
            relevantMetrics.isEmpty() -> HealthAvailabilityStatus.UNAVAILABLE
            available.size == relevantMetrics.size -> HealthAvailabilityStatus.AVAILABLE
            available.isNotEmpty() -> HealthAvailabilityStatus.PARTIAL
            permissionNeeded.isNotEmpty() -> HealthAvailabilityStatus.PERMISSION_NEEDED
            else -> HealthAvailabilityStatus.UNAVAILABLE
        }
        val sources = available.map { it.source }
            .filter { it != SOURCE_NOT_AVAILABLE }
            .distinct()
        val source = sources.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: SOURCE_NOT_AVAILABLE
        val coverage = available.map { it.coveredThrough }
            .filter { it != NO_USABLE_RECORD }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
            ?: NO_USABLE_RECORD
        val reason = when (status) {
            HealthAvailabilityStatus.AVAILABLE -> "Todas las métricas representadas tienen datos"
            HealthAvailabilityStatus.PARTIAL -> "Hay datos y huecos explícitos en este dominio"
            HealthAvailabilityStatus.PERMISSION_NEEDED -> "Faltan permisos de lectura para este dominio"
            HealthAvailabilityStatus.UNAVAILABLE -> noDataReason
        }

        return DomainAvailability(
            domain = domain,
            status = status,
            source = source,
            coveredThrough = coverage,
            reason = reason,
            metricSummary = "${available.size} de ${relevantMetrics.size} métricas con datos",
            metrics = relevantMetrics
        )
    }

    /**
     * Mapeo puro para el dominio de Actividad (pasos).
     * Utiliza la agregación calculada por Health Connect; nunca selecciona una fuente bruta como total.
     */
    fun mapStepsDomain(data: StepsDomainData): DomainAvailability {
        if (!data.isPermissionGranted) {
            return DomainAvailability(
                domain = HealthDomain.STEPS,
                status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Permiso de lectura de pasos no concedido"
            )
        }

        val total = data.totalSteps
        if (total != null && total > 0) {
            val sourceText = if (data.dataOrigins.isNotEmpty()) {
                data.dataOrigins.joinToString(", ")
            } else {
                "Agregación de Health Connect"
            }
            return DomainAvailability(
                domain = HealthDomain.STEPS,
                status = HealthAvailabilityStatus.AVAILABLE,
                source = sourceText,
                coveredThrough = "Agregación acumulada del día",
                reason = "Agregación diaria procesada por Health Connect",
                metricSummary = "$total pasos agregados"
            )
        } else {
            return DomainAvailability(
                domain = HealthDomain.STEPS,
                status = HealthAvailabilityStatus.UNAVAILABLE,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "No hay pasos agregados registrados en este intervalo"
            )
        }
    }

    /**
     * Mapeo puro para Sueño.
     * Si no se usó el wearable o no sincronizó, es 'No disponible', no cero ni error de app.
     */
    fun mapSleepDomain(data: GenericRecordData, formattedTime: String? = null): DomainAvailability {
        if (!data.isPermissionGranted) {
            return DomainAvailability(
                domain = HealthDomain.SLEEP,
                status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Permiso de lectura de sueño no concedido"
            )
        }

        if (!data.hasRecord) {
            return DomainAvailability(
                domain = HealthDomain.SLEEP,
                status = HealthAvailabilityStatus.UNAVAILABLE,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Dispositivo no utilizado o sin sincronización de sesión de sueño"
            )
        }

        val source = data.dataOriginPackage?.takeIf { it.isNotBlank() } ?: SOURCE_NOT_AVAILABLE
        val coverage = formattedTime ?: "Última sesión registrada"

        return DomainAvailability(
            domain = HealthDomain.SLEEP,
            status = HealthAvailabilityStatus.AVAILABLE,
            source = source,
            coveredThrough = coverage,
            reason = "Sesión de sueño disponible en Health Connect",
            metricSummary = data.extraFactualInfo
        )
    }

    /**
     * Mapeo puro para Peso.
     * Medición basada en eventos: la ausencia de pesaje es 'No disponible', no un día fallido.
     */
    fun mapWeightDomain(data: GenericRecordData, formattedTime: String? = null): DomainAvailability {
        if (!data.isPermissionGranted) {
            return DomainAvailability(
                domain = HealthDomain.WEIGHT,
                status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Permiso de lectura de peso no concedido"
            )
        }

        if (!data.hasRecord) {
            return DomainAvailability(
                domain = HealthDomain.WEIGHT,
                status = HealthAvailabilityStatus.UNAVAILABLE,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Sin medición puntual de peso registrada en este día"
            )
        }

        val source = data.dataOriginPackage?.takeIf { it.isNotBlank() } ?: SOURCE_NOT_AVAILABLE
        val coverage = formattedTime ?: "Registro puntual"

        return DomainAvailability(
            domain = HealthDomain.WEIGHT,
            status = HealthAvailabilityStatus.AVAILABLE,
            source = source,
            coveredThrough = coverage,
            reason = "Medición puntual de peso registrada",
            metricSummary = data.extraFactualInfo
        )
    }

    /**
     * Mapeo puro para Nutrición.
     * Registro manual: la ausencia de entrada es 'No disponible', no un fallo de ingestión.
     * Campos no especificados se etiquetan como no disponibles, nunca cero.
     */
    fun mapNutritionDomain(data: GenericRecordData, formattedTime: String? = null): DomainAvailability {
        if (!data.isPermissionGranted) {
            return DomainAvailability(
                domain = HealthDomain.NUTRITION,
                status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Permiso de lectura de nutrición no concedido"
            )
        }

        if (!data.hasRecord) {
            return DomainAvailability(
                domain = HealthDomain.NUTRITION,
                status = HealthAvailabilityStatus.UNAVAILABLE,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Sin registro nutricional manual en este día"
            )
        }

        val source = data.dataOriginPackage?.takeIf { it.isNotBlank() } ?: SOURCE_NOT_AVAILABLE
        val coverage = formattedTime ?: "Registro puntual"

        return DomainAvailability(
            domain = HealthDomain.NUTRITION,
            status = HealthAvailabilityStatus.AVAILABLE,
            source = source,
            coveredThrough = coverage,
            reason = "Registro manual de nutrición detectado",
            metricSummary = data.extraFactualInfo ?: "Registro con campos nutricionales disponibles"
        )
    }

    /**
     * Mapeo puro para Frecuencia cardíaca en reposo.
     * Si no se usó o sincronizó, es 'No disponible', nunca cero ni error de aplicación.
     */
    fun mapRestingHeartRateDomain(data: GenericRecordData, formattedTime: String? = null): DomainAvailability {
        if (!data.isPermissionGranted) {
            return DomainAvailability(
                domain = HealthDomain.RESTING_HEART_RATE,
                status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Permiso de lectura de frecuencia cardíaca no concedido"
            )
        }

        if (!data.hasRecord) {
            return DomainAvailability(
                domain = HealthDomain.RESTING_HEART_RATE,
                status = HealthAvailabilityStatus.UNAVAILABLE,
                source = SOURCE_NOT_AVAILABLE,
                coveredThrough = NO_USABLE_RECORD,
                reason = "Dispositivo no utilizado o sin sincronización de frecuencia en reposo"
            )
        }

        val source = data.dataOriginPackage?.takeIf { it.isNotBlank() } ?: SOURCE_NOT_AVAILABLE
        val coverage = formattedTime ?: "Registro puntual"

        return DomainAvailability(
            domain = HealthDomain.RESTING_HEART_RATE,
            status = HealthAvailabilityStatus.AVAILABLE,
            source = source,
            coveredThrough = coverage,
            reason = "Registro de frecuencia cardíaca en reposo detectado",
            metricSummary = data.extraFactualInfo
        )
    }

    /**
     * Calcula el informe diario completo.
     * El estado general es Completo (AVAILABLE) solo si todos los dominios están disponibles.
     * De lo contrario, es Parcial (PARTIAL) y enumera los dominios no disponibles.
     */
    fun buildDayReport(
        date: LocalDate,
        zoneId: ZoneId,
        domains: List<DomainAvailability>
    ): DayAvailabilityReport {
        val representedDomains = domains.map { it.domain }.toSet()
        val missingDomains = HealthDomain.entries.filterNot { it in representedDomains }
        val allAvailable = missingDomains.isEmpty() &&
            domains.size == representedDomains.size &&
            domains.all { it.status == HealthAvailabilityStatus.AVAILABLE }
        val overallStatus = if (allAvailable) {
            HealthAvailabilityStatus.AVAILABLE
        } else {
            HealthAvailabilityStatus.PARTIAL
        }

        val unavailableList = domains
            .filter { it.status != HealthAvailabilityStatus.AVAILABLE }
            .map { it.domain }
            .plus(missingDomains)
            .distinct()

        return DayAvailabilityReport(
            date = date,
            zoneId = zoneId,
            domains = domains,
            overallStatus = overallStatus,
            unavailableDomains = unavailableList
        )
    }
}
