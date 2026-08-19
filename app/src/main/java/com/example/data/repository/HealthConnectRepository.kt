package com.example.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.GenericRecordData
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.HealthStatusMapper
import com.example.data.model.SdkAvailability
import com.example.data.model.StepsDomainData
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

interface HealthConnectRepository {
    fun getSdkAvailability(): SdkAvailability
    suspend fun getGrantedPermissions(): Set<String>
    suspend fun loadDayAvailability(date: LocalDate, zoneId: ZoneId): DayAvailabilityReport
    fun getRequiredPermissions(): Set<String>
    fun getBackgroundReadPermission(): String
    fun isBackgroundReadAvailable(): Boolean
}

class RealHealthConnectRepository(
    private val context: Context
) : HealthConnectRepository {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    override fun getRequiredPermissions(): Set<String> {
        return ComprehensiveHealthPermissions.required(getClient())
    }

    override fun getBackgroundReadPermission(): String {
        return PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }

    override fun isBackgroundReadAvailable(): Boolean {
        val client = getClient() ?: return false
        return try {
            client.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    override fun getSdkAvailability(): SdkAvailability {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> SdkAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SdkAvailability.UPDATE_REQUIRED
                HealthConnectClient.SDK_UNAVAILABLE -> SdkAvailability.UNAVAILABLE
                else -> SdkAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            SdkAvailability.UNAVAILABLE
        }
    }

    private fun getClient(): HealthConnectClient? {
        return try {
            if (getSdkAvailability() == SdkAvailability.AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getGrantedPermissions(): Set<String> {
        val client = getClient() ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            emptySet()
        }
    }

    override suspend fun loadDayAvailability(date: LocalDate, zoneId: ZoneId): DayAvailabilityReport {
        val client = getClient()
        val granted = getGrantedPermissions()

        if (client != null) {
            return ComprehensiveHealthReader(client, granted, date, zoneId).readReport()
        }

        val startOfDay = date.atStartOfDay(zoneId).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

        val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)
        val nutritionPermission = HealthPermission.getReadPermission(NutritionRecord::class)
        val rhrPermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

        // 1. Actividad (pasos) - Agregación diaria
        val stepsDomain = if (!granted.contains(stepsPermission) || client == null) {
            HealthStatusMapper.mapStepsDomain(
                StepsDomainData(
                    isPermissionGranted = granted.contains(stepsPermission),
                    totalSteps = null
                )
            )
        } else {
            try {
                val aggregateResponse = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = timeRange
                    )
                )
                val total = aggregateResponse[StepsRecord.COUNT_TOTAL]
                val origins = aggregateResponse.dataOrigins.map { it.packageName }.toSet()
                HealthStatusMapper.mapStepsDomain(
                    StepsDomainData(
                        isPermissionGranted = true,
                        totalSteps = total,
                        dataOrigins = origins
                    )
                )
            } catch (e: Exception) {
                HealthStatusMapper.mapStepsDomain(
                    StepsDomainData(
                        isPermissionGranted = true,
                        totalSteps = null
                    )
                )
            }
        }

        // 2. Sueño - Registro más reciente
        val sleepDomain = if (!granted.contains(sleepPermission) || client == null) {
            HealthStatusMapper.mapSleepDomain(
                GenericRecordData(isPermissionGranted = granted.contains(sleepPermission), hasRecord = false)
            )
        } else {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = timeRange,
                        ascendingOrder = false
                    )
                )
                val latest = response.records.maxByOrNull { it.endTime }
                if (latest != null) {
                    val startStr = timeFormatter.withZone(zoneId).format(latest.startTime)
                    val endStr = timeFormatter.withZone(zoneId).format(latest.endTime)
                    val durationMin = Duration.between(latest.startTime, latest.endTime).toMinutes()
                    val hours = durationMin / 60
                    val minutes = durationMin % 60
                    val durationText = "${hours}h ${minutes}m registradas"
                    HealthStatusMapper.mapSleepDomain(
                        data = GenericRecordData(
                            isPermissionGranted = true,
                            hasRecord = true,
                            recordTimestamp = latest.startTime,
                            recordEndTime = latest.endTime,
                            dataOriginPackage = latest.metadata.dataOrigin.packageName,
                            extraFactualInfo = "$durationText ($startStr - $endStr)"
                        ),
                        formattedTime = "$startStr - $endStr"
                    )
                } else {
                    HealthStatusMapper.mapSleepDomain(
                        GenericRecordData(isPermissionGranted = true, hasRecord = false)
                    )
                }
            } catch (e: Exception) {
                HealthStatusMapper.mapSleepDomain(
                    GenericRecordData(isPermissionGranted = true, hasRecord = false)
                )
            }
        }

        // 3. Peso - Registro más reciente
        val weightDomain = if (!granted.contains(weightPermission) || client == null) {
            HealthStatusMapper.mapWeightDomain(
                GenericRecordData(isPermissionGranted = granted.contains(weightPermission), hasRecord = false)
            )
        } else {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = timeRange,
                        ascendingOrder = false
                    )
                )
                val latest = response.records.maxByOrNull { it.time }
                if (latest != null) {
                    val timeStr = timeFormatter.withZone(zoneId).format(latest.time)
                    val weightKg = latest.weight.inKilograms
                    HealthStatusMapper.mapWeightDomain(
                        data = GenericRecordData(
                            isPermissionGranted = true,
                            hasRecord = true,
                            recordTimestamp = latest.time,
                            dataOriginPackage = latest.metadata.dataOrigin.packageName,
                            extraFactualInfo = String.format(Locale.US, "%.1f kg medidos", weightKg)
                        ),
                        formattedTime = timeStr
                    )
                } else {
                    HealthStatusMapper.mapWeightDomain(
                        GenericRecordData(isPermissionGranted = true, hasRecord = false)
                    )
                }
            } catch (e: Exception) {
                HealthStatusMapper.mapWeightDomain(
                    GenericRecordData(isPermissionGranted = true, hasRecord = false)
                )
            }
        }

        // 4. Nutrición - Registro más reciente
        val nutritionDomain = if (!granted.contains(nutritionPermission) || client == null) {
            HealthStatusMapper.mapNutritionDomain(
                GenericRecordData(isPermissionGranted = granted.contains(nutritionPermission), hasRecord = false)
            )
        } else {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = NutritionRecord::class,
                        timeRangeFilter = timeRange,
                        ascendingOrder = false
                    )
                )
                val latest = response.records.maxByOrNull { it.endTime }
                if (latest != null) {
                    val timeStr = timeFormatter.withZone(zoneId).format(latest.endTime)
                    val energyStr = latest.energy?.let { "${it.inKilocalories.roundToInt()} kcal" } ?: "Energía: No disponible"
                    val nameStr = latest.name ?: "Comida sin nombre"
                    HealthStatusMapper.mapNutritionDomain(
                        data = GenericRecordData(
                            isPermissionGranted = true,
                            hasRecord = true,
                            recordTimestamp = latest.startTime,
                            recordEndTime = latest.endTime,
                            dataOriginPackage = latest.metadata.dataOrigin.packageName,
                            extraFactualInfo = "$nameStr ($energyStr)"
                        ),
                        formattedTime = timeStr
                    )
                } else {
                    HealthStatusMapper.mapNutritionDomain(
                        GenericRecordData(isPermissionGranted = true, hasRecord = false)
                    )
                }
            } catch (e: Exception) {
                HealthStatusMapper.mapNutritionDomain(
                    GenericRecordData(isPermissionGranted = true, hasRecord = false)
                )
            }
        }

        // 5. Frecuencia cardíaca en reposo - Registro más reciente
        val rhrDomain = if (!granted.contains(rhrPermission) || client == null) {
            HealthStatusMapper.mapRestingHeartRateDomain(
                GenericRecordData(isPermissionGranted = granted.contains(rhrPermission), hasRecord = false)
            )
        } else {
            try {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = timeRange,
                        ascendingOrder = false
                    )
                )
                val latest = response.records.maxByOrNull { it.time }
                if (latest != null) {
                    val timeStr = timeFormatter.withZone(zoneId).format(latest.time)
                    val bpm = latest.beatsPerMinute
                    HealthStatusMapper.mapRestingHeartRateDomain(
                        data = GenericRecordData(
                            isPermissionGranted = true,
                            hasRecord = true,
                            recordTimestamp = latest.time,
                            dataOriginPackage = latest.metadata.dataOrigin.packageName,
                            extraFactualInfo = "$bpm ppm registrados"
                        ),
                        formattedTime = timeStr
                    )
                } else {
                    HealthStatusMapper.mapRestingHeartRateDomain(
                        GenericRecordData(isPermissionGranted = true, hasRecord = false)
                    )
                }
            } catch (e: Exception) {
                HealthStatusMapper.mapRestingHeartRateDomain(
                    GenericRecordData(isPermissionGranted = true, hasRecord = false)
                )
            }
        }

        val domainList = listOf(stepsDomain, sleepDomain, weightDomain, nutritionDomain, rhrDomain)
        return HealthStatusMapper.buildDayReport(date, zoneId, domainList)
    }
}

/**
 * Repositorio de prueba para Compose Previews y pruebas sin emulador.
 */
class FakeHealthConnectRepository(
    private val initialSdkAvailability: SdkAvailability = SdkAvailability.AVAILABLE,
    private val mockAllAvailable: Boolean = true
) : HealthConnectRepository {

    override fun getRequiredPermissions(): Set<String> {
        return setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ELEVATION_GAINED",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_WEIGHT",
            "android.permission.health.READ_BODY_FAT",
            "android.permission.health.READ_NUTRITION",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_OXYGEN_SATURATION"
        )
    }

    override fun getBackgroundReadPermission(): String =
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    override fun isBackgroundReadAvailable(): Boolean = true

    override fun getSdkAvailability(): SdkAvailability = initialSdkAvailability

    override suspend fun getGrantedPermissions(): Set<String> =
        getRequiredPermissions() + getBackgroundReadPermission()

    override suspend fun loadDayAvailability(date: LocalDate, zoneId: ZoneId): DayAvailabilityReport {
        val domains = if (mockAllAvailable) {
            listOf(
                DomainAvailability(
                    domain = HealthDomain.STEPS,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.google.android.apps.fitness",
                    coveredThrough = "Agregación acumulada del día",
                    reason = "Agregación diaria procesada por Health Connect",
                    metricSummary = "8.420 pasos agregados"
                ),
                DomainAvailability(
                    domain = HealthDomain.EXERCISE,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.google.android.apps.fitness",
                    coveredThrough = "08:10 - 08:42",
                    reason = "Sesión de entrenamiento registrada",
                    metricSummary = "1 entrenamiento"
                ),
                DomainAvailability(
                    domain = HealthDomain.SLEEP,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.fitbit.FitbitMobile",
                    coveredThrough = "23:15 - 07:10",
                    reason = "Sesión de sueño disponible en Health Connect",
                    metricSummary = "7h 55m registradas"
                ),
                DomainAvailability(
                    domain = HealthDomain.WEIGHT,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.withings.wiscale2",
                    coveredThrough = "07:30",
                    reason = "Medición puntual de peso registrada",
                    metricSummary = "72.4 kg medidos"
                ),
                DomainAvailability(
                    domain = HealthDomain.NUTRITION,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.myfitnesspal.android",
                    coveredThrough = "14:15",
                    reason = "Registro manual de nutrición detectado",
                    metricSummary = "Almuerzo (650 kcal)"
                ),
                DomainAvailability(
                    domain = HealthDomain.RESTING_HEART_RATE,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.garmin.android.apps.connectmobile",
                    coveredThrough = "06:45",
                    reason = "Registro de frecuencia cardíaca en reposo detectado",
                    metricSummary = "62 ppm registrados"
                )
            )
        } else {
            listOf(
                DomainAvailability(
                    domain = HealthDomain.STEPS,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "Agregación de Health Connect",
                    coveredThrough = "Agregación acumulada del día",
                    reason = "Agregación diaria procesada por Health Connect",
                    metricSummary = "3.200 pasos agregados"
                ),
                DomainAvailability(
                    domain = HealthDomain.EXERCISE,
                    status = HealthAvailabilityStatus.UNAVAILABLE,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Sin entrenamiento registrado en este día"
                ),
                DomainAvailability(
                    domain = HealthDomain.SLEEP,
                    status = HealthAvailabilityStatus.UNAVAILABLE,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Dispositivo no utilizado o sin sincronización de sesión de sueño"
                ),
                DomainAvailability(
                    domain = HealthDomain.WEIGHT,
                    status = HealthAvailabilityStatus.UNAVAILABLE,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Sin medición puntual de peso registrada en este día"
                ),
                DomainAvailability(
                    domain = HealthDomain.NUTRITION,
                    status = HealthAvailabilityStatus.UNAVAILABLE,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Sin registro nutricional manual en este día"
                ),
                DomainAvailability(
                    domain = HealthDomain.RESTING_HEART_RATE,
                    status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Permiso de lectura de frecuencia cardíaca no concedido"
                )
            )
        }
        return HealthStatusMapper.buildDayReport(date, zoneId, domains)
    }
}
