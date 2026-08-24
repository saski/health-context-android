package com.example.data.repository

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_HISTORY
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.HealthStatusMapper
import com.example.data.model.MetricAvailability
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/** Central definition of the stable, relevant Health Connect read surface. */
object ComprehensiveHealthPermissions {
    private val stableRecordTypes: Set<KClass<out Record>> = setOf(
        StepsRecord::class,
        StepsCadenceRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        DistanceRecord::class,
        ElevationGainedRecord::class,
        FloorsClimbedRecord::class,
        ExerciseSessionRecord::class,
        SpeedRecord::class,
        PowerRecord::class,
        CyclingPedalingCadenceRecord::class,
        SleepSessionRecord::class,
        WeightRecord::class,
        BodyFatRecord::class,
        BodyWaterMassRecord::class,
        BoneMassRecord::class,
        HeightRecord::class,
        LeanBodyMassRecord::class,
        BasalMetabolicRateRecord::class,
        NutritionRecord::class,
        HydrationRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        OxygenSaturationRecord::class,
        RespiratoryRateRecord::class,
        Vo2MaxRecord::class,
        BloodPressureRecord::class,
        BloodGlucoseRecord::class,
        BodyTemperatureRecord::class
    )

    fun required(client: HealthConnectClient?): Set<String> = buildSet {
        stableRecordTypes.forEach { add(HealthPermission.getReadPermission(it)) }
        if (client?.hasFeature(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == true) {
            add(HealthPermission.getReadPermission(SkinTemperatureRecord::class))
        }
        if (client?.hasFeature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == true) {
            add(PERMISSION_READ_HEALTH_DATA_HISTORY)
        }
    }
}

private fun HealthConnectClient.hasFeature(feature: Int): Boolean = try {
    features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
} catch (_: Exception) {
    false
}

/** Reads a local day into six compact domains with metric-level detail. */
class ComprehensiveHealthReader(
    private val client: HealthConnectClient,
    private val grantedPermissions: Set<String>,
    private val date: LocalDate,
    private val zoneId: ZoneId
) {
    private val startOfDay = date.atStartOfDay(zoneId).toInstant()
    private val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    private val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(zoneId)

    suspend fun readReport(): DayAvailabilityReport {
        val domains = listOf(
            readDailyActivity(),
            readExercise(),
            readSleep(),
            readBody(),
            readNutrition(),
            readVitals()
        )
        return HealthStatusMapper.buildDayReport(date, zoneId, domains)
    }

    private suspend fun readDailyActivity(): DomainAvailability {
        val metrics = listOf(
            aggregate(
                StepsRecord::class,
                StepsRecord.COUNT_TOTAL,
                "steps",
                "Pasos"
            ) { "$it pasos" },
            aggregate(
                ActiveCaloriesBurnedRecord::class,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                "active_calories",
                "Calorías activas"
            ) { "${it.inKilocalories.roundToInt()} kcal" },
            aggregate(
                TotalCaloriesBurnedRecord::class,
                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                "total_calories",
                "Calorías totales"
            ) { "${it.inKilocalories.roundToInt()} kcal" },
            aggregate(
                DistanceRecord::class,
                DistanceRecord.DISTANCE_TOTAL,
                "distance",
                "Distancia"
            ) { formatDistance(it.inMeters) },
            aggregate(
                ElevationGainedRecord::class,
                ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                "elevation_gained",
                "Desnivel acumulado"
            ) { formatDecimal(it.inMeters, "m") },
            aggregate(
                FloorsClimbedRecord::class,
                FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                "floors_climbed",
                "Pisos subidos"
            ) { formatDecimal(it, "pisos") },
            aggregate(
                StepsCadenceRecord::class,
                StepsCadenceRecord.RATE_AVG,
                "steps_cadence",
                "Cadencia media de pasos"
            ) { formatDecimal(it, "pasos/min") },
            aggregate(
                StepsCadenceRecord::class,
                StepsCadenceRecord.RATE_MAX,
                "steps_cadence_max",
                "Cadencia máxima de pasos"
            ) { formatDecimal(it, "pasos/min") }
        )
        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.STEPS,
            metrics,
            "Sin actividad diaria utilizable"
        )
    }

    private suspend fun readExercise(): DomainAvailability {
        val permission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val metrics = mutableListOf<MetricAvailability>()
        if (permission !in grantedPermissions) {
            metrics += missingPermission("exercise_sessions", "Sesiones de entrenamiento")
        } else {
            val sessions = readAll(ExerciseSessionRecord::class)
            if (sessions.isEmpty()) {
                metrics += unavailable(
                    "exercise_sessions",
                    "Sesiones de entrenamiento",
                    "Sin entrenamiento registrado en este día"
                )
            } else {
                sessions.sortedBy { it.startTime }.forEachIndexed { index, session ->
                    val duration = Duration.between(session.startTime, session.endTime)
                    val typeLabel = exerciseTypeLabel(session.exerciseType)
                    val extras = buildList {
                        add(formatDuration(duration))
                        session.title?.takeIf { it.isNotBlank() }?.let { add("título: ${sanitize(it)}") }
                        session.notes?.takeIf { it.isNotBlank() }?.let { add("notas: ${sanitize(it)}") }
                        if (session.laps.isNotEmpty()) add("${session.laps.size} vueltas")
                        if (session.segments.isNotEmpty()) add("${session.segments.size} segmentos")
                    }
                    metrics += available(
                        key = "exercise_session_${index + 1}",
                        label = typeLabel,
                        source = session.metadata.dataOrigin.packageName.ifBlank { "Health Connect" },
                        coverage = "${timeFormatter.format(session.startTime)} - ${timeFormatter.format(session.endTime)}",
                        observation = extras.joinToString("; "),
                        reason = "Sesión de entrenamiento registrada"
                    )
                }
            }
        }
        metrics += aggregate(
            SpeedRecord::class,
            SpeedRecord.SPEED_AVG,
            "speed_average",
            "Velocidad media"
        ) { formatDecimal(it.inKilometersPerHour, "km/h") }
        metrics += aggregate(
            SpeedRecord::class,
            SpeedRecord.SPEED_MAX,
            "speed_maximum",
            "Velocidad máxima"
        ) { formatDecimal(it.inKilometersPerHour, "km/h") }
        metrics += aggregate(
            PowerRecord::class,
            PowerRecord.POWER_AVG,
            "power_average",
            "Potencia media"
        ) { formatDecimal(it.inWatts, "W") }
        metrics += aggregate(
            PowerRecord::class,
            PowerRecord.POWER_MAX,
            "power_maximum",
            "Potencia máxima"
        ) { formatDecimal(it.inWatts, "W") }
        metrics += aggregate(
            CyclingPedalingCadenceRecord::class,
            CyclingPedalingCadenceRecord.RPM_AVG,
            "cycling_cadence",
            "Cadencia media de pedaleo"
        ) { formatDecimal(it, "rpm") }
        metrics += aggregate(
            CyclingPedalingCadenceRecord::class,
            CyclingPedalingCadenceRecord.RPM_MAX,
            "cycling_cadence_max",
            "Cadencia máxima de pedaleo"
        ) { formatDecimal(it, "rpm") }

        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.EXERCISE,
            metrics,
            "Sin entrenamientos ni métricas asociadas"
        )
    }

    private suspend fun readSleep(): DomainAvailability {
        val permission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val metrics = mutableListOf<MetricAvailability>()
        if (permission !in grantedPermissions) {
            metrics += missingPermission("sleep_sessions", "Sesiones de sueño")
        } else {
            val sessions = readAll(SleepSessionRecord::class)
            if (sessions.isEmpty()) {
                metrics += unavailable(
                    "sleep_sessions",
                    "Sesiones de sueño",
                    "Dispositivo no utilizado o sesión no sincronizada"
                )
            } else {
                sessions.sortedBy { it.startTime }.forEachIndexed { index, session ->
                    val duration = Duration.between(session.startTime, session.endTime)
                    val stageText = if (session.stages.isEmpty()) {
                        "sin fases detalladas"
                    } else {
                        "${session.stages.size} tramos de fases"
                    }
                    metrics += available(
                        key = "sleep_session_${index + 1}",
                        label = session.title?.takeIf { it.isNotBlank() } ?: "Sesión de sueño",
                        source = session.metadata.dataOrigin.packageName.ifBlank { "Health Connect" },
                        coverage = "${timeFormatter.format(session.startTime)} - ${timeFormatter.format(session.endTime)}",
                        observation = "${formatDuration(duration)}; $stageText",
                        reason = "Sesión de sueño registrada"
                    )
                }
            }
        }
        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.SLEEP,
            metrics,
            "Sin sesión de sueño utilizable"
        )
    }

    private suspend fun readBody(): DomainAvailability {
        val metrics = listOf(
            latest(WeightRecord::class, "weight", "Peso", { it.time }) {
                formatDecimal(it.weight.inKilograms, "kg")
            },
            latest(BodyFatRecord::class, "body_fat", "Grasa corporal", { it.time }) {
                formatDecimal(it.percentage.value, "%")
            },
            latest(BodyWaterMassRecord::class, "body_water_mass", "Agua corporal", { it.time }) {
                formatDecimal(it.mass.inKilograms, "kg")
            },
            latest(BoneMassRecord::class, "bone_mass", "Masa ósea", { it.time }) {
                formatDecimal(it.mass.inKilograms, "kg")
            },
            latest(LeanBodyMassRecord::class, "lean_body_mass", "Masa magra", { it.time }) {
                formatDecimal(it.mass.inKilograms, "kg")
            },
            latest(HeightRecord::class, "height", "Altura", { it.time }) {
                formatDecimal(it.height.inMeters * 100.0, "cm")
            },
            latest(BasalMetabolicRateRecord::class, "basal_metabolic_rate", "Metabolismo basal", { it.time }) {
                formatDecimal(it.basalMetabolicRate.inKilocaloriesPerDay, "kcal/día")
            }
        )
        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.WEIGHT,
            metrics,
            "Sin mediciones corporales registradas",
            optionalKeys = setOf("height")
        )
    }

    private suspend fun readNutrition(): DomainAvailability {
        val metrics = mutableListOf<MetricAvailability>()
        val nutritionPermission = HealthPermission.getReadPermission(NutritionRecord::class)
        if (nutritionPermission !in grantedPermissions) {
            metrics += missingPermission("nutrition_entries", "Entradas de alimentación")
        } else {
            val entries = readAll(NutritionRecord::class)
            if (entries.isEmpty()) {
                metrics += unavailable(
                    "nutrition_entries",
                    "Entradas de alimentación",
                    "Sin registro nutricional manual en este día"
                )
            } else {
                entries.sortedBy { it.startTime }.forEachIndexed { index, entry ->
                    val values = buildList {
                        entry.energy?.let { add("${it.inKilocalories.roundToInt()} kcal") }
                        entry.protein?.let { add(formatDecimal(it.inGrams, "g proteína")) }
                        entry.totalCarbohydrate?.let { add(formatDecimal(it.inGrams, "g carbohidratos")) }
                        entry.totalFat?.let { add(formatDecimal(it.inGrams, "g grasa")) }
                    }
                    metrics += available(
                        key = "nutrition_entry_${index + 1}",
                        label = entry.name?.takeIf { it.isNotBlank() } ?: mealTypeLabel(entry.mealType),
                        source = entry.metadata.dataOrigin.packageName.ifBlank { "Health Connect" },
                        coverage = timeFormatter.format(entry.endTime),
                        observation = values.ifEmpty { listOf("registro sin energía ni macronutrientes") }.joinToString("; "),
                        reason = "Entrada nutricional distinta registrada"
                    )
                }
                nutritionTotals(entries).forEach { (key, label, value) ->
                    metrics += available(
                        key = key,
                        label = label,
                        source = entries.map { it.metadata.dataOrigin.packageName.ifBlank { "Health Connect" } }
                            .distinct()
                            .joinToString(", "),
                        coverage = "Total del día",
                        observation = value,
                        reason = "Suma de campos presentes; los ausentes no cuentan como cero"
                    )
                }
            }
        }
        metrics += aggregate(
            HydrationRecord::class,
            HydrationRecord.VOLUME_TOTAL,
            "hydration",
            "Hidratación"
        ) { formatDecimal(it.inLiters, "L") }
        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.NUTRITION,
            metrics,
            "Sin alimentación ni hidratación registradas"
        )
    }

    private suspend fun readVitals(): DomainAvailability {
        val metrics = mutableListOf<MetricAvailability>()
        metrics += heartRateSummary()
        metrics += latest(RestingHeartRateRecord::class, "resting_heart_rate", "Frecuencia cardíaca en reposo", { it.time }) {
            "${it.beatsPerMinute} ppm"
        }
        metrics += latest(HeartRateVariabilityRmssdRecord::class, "hrv_rmssd", "Variabilidad cardíaca (RMSSD)", { it.time }) {
            formatDecimal(it.heartRateVariabilityMillis, "ms")
        }
        metrics += latest(OxygenSaturationRecord::class, "oxygen_saturation", "Saturación de oxígeno", { it.time }) {
            formatDecimal(it.percentage.value, "%")
        }
        metrics += latest(RespiratoryRateRecord::class, "respiratory_rate", "Frecuencia respiratoria", { it.time }) {
            formatDecimal(it.rate, "resp/min")
        }
        metrics += latest(Vo2MaxRecord::class, "vo2_max", "VO₂ max", { it.time }) {
            formatDecimal(it.vo2MillilitersPerMinuteKilogram, "ml/kg/min")
        }
        metrics += latest(BloodPressureRecord::class, "blood_pressure", "Presión arterial", { it.time }) {
            "${it.systolic.inMillimetersOfMercury.roundToInt()}/${it.diastolic.inMillimetersOfMercury.roundToInt()} mmHg"
        }
        metrics += latest(BloodGlucoseRecord::class, "blood_glucose", "Glucosa", { it.time }) {
            formatDecimal(it.level.inMilligramsPerDeciliter, "mg/dL")
        }
        metrics += latest(BodyTemperatureRecord::class, "body_temperature", "Temperatura corporal", { it.time }) {
            formatDecimal(it.temperature.inCelsius, "°C")
        }
        if (client.hasFeature(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE)) {
            metrics += latest(SkinTemperatureRecord::class, "skin_temperature", "Temperatura cutánea", { it.endTime }) { record ->
                val latestDelta = record.deltas.maxByOrNull { it.time }
                buildList {
                    record.baseline?.let { add("base ${formatDecimal(it.inCelsius, "°C")}") }
                    latestDelta?.let { add("delta ${formatSignedDecimal(it.delta.inCelsius, "°C")}") }
                }.ifEmpty { listOf("registro sin base ni delta") }.joinToString("; ")
            }
        } else {
            metrics += unavailable(
                "skin_temperature",
                "Temperatura cutánea",
                "Función no compatible con este proveedor de Health Connect"
            )
        }
        return HealthStatusMapper.mapMetricDomain(
            HealthDomain.RESTING_HEART_RATE,
            metrics,
            "Sin indicadores fisiológicos registrados"
        )
    }

    private suspend fun heartRateSummary(): MetricAvailability {
        val permission = HealthPermission.getReadPermission(HeartRateRecord::class)
        if (permission !in grantedPermissions) return missingPermission("heart_rate", "Frecuencia cardíaca")
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX,
                        HeartRateRecord.MEASUREMENTS_COUNT
                    ),
                    timeRangeFilter = timeRange
                )
            )
            val average = response[HeartRateRecord.BPM_AVG]
            val minimum = response[HeartRateRecord.BPM_MIN]
            val maximum = response[HeartRateRecord.BPM_MAX]
            val count = response[HeartRateRecord.MEASUREMENTS_COUNT]
            if (average == null && minimum == null && maximum == null) {
                unavailable("heart_rate", "Frecuencia cardíaca", "Sin muestras para este día")
            } else {
                available(
                    "heart_rate",
                    "Frecuencia cardíaca",
                    response.dataOrigins.map { it.packageName }.distinct().joinToString(", ").ifBlank { "Health Connect" },
                    "Resumen del día",
                    buildList {
                        average?.let { add("media $it ppm") }
                        minimum?.let { add("mín $it") }
                        maximum?.let { add("máx $it") }
                        count?.let { add("$it muestras") }
                    }.joinToString("; "),
                    "Agregación calculada por Health Connect"
                )
            }
        } catch (_: Exception) {
            unavailable("heart_rate", "Frecuencia cardíaca", "No se pudo obtener el resumen del día")
        }
    }

    private suspend fun <R : Record, T : Any> aggregate(
        recordType: KClass<R>,
        metric: AggregateMetric<T>,
        key: String,
        label: String,
        format: (T) -> String
    ): MetricAvailability {
        val permission = HealthPermission.getReadPermission(recordType)
        if (permission !in grantedPermissions) return missingPermission(key, label)
        return try {
            val response = client.aggregate(AggregateRequest(setOf(metric), timeRange))
            val value = response[metric]
            if (value == null) {
                unavailable(key, label, "Sin registro para este día")
            } else {
                available(
                    key,
                    label,
                    response.dataOrigins.map { it.packageName }.distinct().joinToString(", ").ifBlank { "Health Connect" },
                    "Total del día",
                    format(value),
                    "Agregación calculada por Health Connect"
                )
            }
        } catch (_: Exception) {
            unavailable(key, label, "No se pudo consultar esta métrica")
        }
    }

    private suspend fun <R : Record> latest(
        recordType: KClass<R>,
        key: String,
        label: String,
        time: (R) -> Instant,
        format: (R) -> String
    ): MetricAvailability {
        val permission = HealthPermission.getReadPermission(recordType)
        if (permission !in grantedPermissions) return missingPermission(key, label)
        return try {
            val latest = readAll(recordType).maxByOrNull(time)
            if (latest == null) {
                unavailable(key, label, "Sin registro para este día")
            } else {
                available(
                    key,
                    label,
                    latest.metadata.dataOrigin.packageName.ifBlank { "Health Connect" },
                    timeFormatter.format(time(latest)),
                    format(latest),
                    "Último registro disponible del día"
                )
            }
        } catch (_: Exception) {
            unavailable(key, label, "No se pudo consultar esta métrica")
        }
    }

    private suspend fun <R : Record> readAll(recordType: KClass<R>): List<R> {
        val records = mutableListOf<R>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = timeRange,
                    ascendingOrder = true,
                    pageSize = 1000,
                    pageToken = pageToken
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records.distinctBy { it.metadata.id }
    }

    private fun nutritionTotals(records: List<NutritionRecord>): List<Triple<String, String, String>> {
        val result = mutableListOf<Triple<String, String, String>>()
        fun energy(key: String, label: String, value: Double?) {
            value?.let { result += Triple(key, label, "${it.roundToInt()} kcal") }
        }
        fun mass(key: String, label: String, value: Double?) {
            value?.let { result += Triple(key, label, formatMass(it)) }
        }
        energy("nutrition_energy_total", "Energía total", records.mapNotNull { it.energy?.inKilocalories }.takeIf { it.isNotEmpty() }?.sum())
        energy("nutrition_energy_from_fat_total", "Energía procedente de grasa", records.mapNotNull { it.energyFromFat?.inKilocalories }.takeIf { it.isNotEmpty() }?.sum())
        mass("nutrition_protein_total", "Proteína total", sumMass(records) { it.protein?.inGrams })
        mass("nutrition_carbohydrate_total", "Carbohidratos totales", sumMass(records) { it.totalCarbohydrate?.inGrams })
        mass("nutrition_fat_total", "Grasa total", sumMass(records) { it.totalFat?.inGrams })
        mass("nutrition_fiber_total", "Fibra total", sumMass(records) { it.dietaryFiber?.inGrams })
        mass("nutrition_sugar_total", "Azúcar total", sumMass(records) { it.sugar?.inGrams })
        mass("nutrition_saturated_fat_total", "Grasa saturada", sumMass(records) { it.saturatedFat?.inGrams })
        mass("nutrition_unsaturated_fat_total", "Grasa insaturada", sumMass(records) { it.unsaturatedFat?.inGrams })
        mass("nutrition_sodium_total", "Sodio", sumMass(records) { it.sodium?.inGrams })
        mass("nutrition_caffeine_total", "Cafeína", sumMass(records) { it.caffeine?.inGrams })
        mass("nutrition_calcium_total", "Calcio", sumMass(records) { it.calcium?.inGrams })
        mass("nutrition_biotin_total", "Biotina", sumMass(records) { it.biotin?.inGrams })
        mass("nutrition_chloride_total", "Cloruro", sumMass(records) { it.chloride?.inGrams })
        mass("nutrition_cholesterol_total", "Colesterol", sumMass(records) { it.cholesterol?.inGrams })
        mass("nutrition_chromium_total", "Cromo", sumMass(records) { it.chromium?.inGrams })
        mass("nutrition_copper_total", "Cobre", sumMass(records) { it.copper?.inGrams })
        mass("nutrition_folate_total", "Folato", sumMass(records) { it.folate?.inGrams })
        mass("nutrition_folic_acid_total", "Ácido fólico", sumMass(records) { it.folicAcid?.inGrams })
        mass("nutrition_iodine_total", "Yodo", sumMass(records) { it.iodine?.inGrams })
        mass("nutrition_iron_total", "Hierro", sumMass(records) { it.iron?.inGrams })
        mass("nutrition_magnesium_total", "Magnesio", sumMass(records) { it.magnesium?.inGrams })
        mass("nutrition_manganese_total", "Manganeso", sumMass(records) { it.manganese?.inGrams })
        mass("nutrition_molybdenum_total", "Molibdeno", sumMass(records) { it.molybdenum?.inGrams })
        mass("nutrition_monounsaturated_fat_total", "Grasa monoinsaturada", sumMass(records) { it.monounsaturatedFat?.inGrams })
        mass("nutrition_niacin_total", "Niacina", sumMass(records) { it.niacin?.inGrams })
        mass("nutrition_pantothenic_acid_total", "Ácido pantoténico", sumMass(records) { it.pantothenicAcid?.inGrams })
        mass("nutrition_phosphorus_total", "Fósforo", sumMass(records) { it.phosphorus?.inGrams })
        mass("nutrition_polyunsaturated_fat_total", "Grasa poliinsaturada", sumMass(records) { it.polyunsaturatedFat?.inGrams })
        mass("nutrition_potassium_total", "Potasio", sumMass(records) { it.potassium?.inGrams })
        mass("nutrition_riboflavin_total", "Riboflavina", sumMass(records) { it.riboflavin?.inGrams })
        mass("nutrition_selenium_total", "Selenio", sumMass(records) { it.selenium?.inGrams })
        mass("nutrition_thiamin_total", "Tiamina", sumMass(records) { it.thiamin?.inGrams })
        mass("nutrition_trans_fat_total", "Grasa trans", sumMass(records) { it.transFat?.inGrams })
        mass("nutrition_vitamin_a_total", "Vitamina A", sumMass(records) { it.vitaminA?.inGrams })
        mass("nutrition_vitamin_b12_total", "Vitamina B12", sumMass(records) { it.vitaminB12?.inGrams })
        mass("nutrition_vitamin_b6_total", "Vitamina B6", sumMass(records) { it.vitaminB6?.inGrams })
        mass("nutrition_vitamin_c_total", "Vitamina C", sumMass(records) { it.vitaminC?.inGrams })
        mass("nutrition_vitamin_d_total", "Vitamina D", sumMass(records) { it.vitaminD?.inGrams })
        mass("nutrition_vitamin_e_total", "Vitamina E", sumMass(records) { it.vitaminE?.inGrams })
        mass("nutrition_vitamin_k_total", "Vitamina K", sumMass(records) { it.vitaminK?.inGrams })
        mass("nutrition_zinc_total", "Zinc", sumMass(records) { it.zinc?.inGrams })
        return result
    }

    private fun sumMass(records: List<NutritionRecord>, value: (NutritionRecord) -> Double?): Double? {
        val present = records.mapNotNull(value)
        return present.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun available(
        key: String,
        label: String,
        source: String,
        coverage: String,
        observation: String,
        reason: String,
        sourcePackages: Set<String> = source.split(',')
            .map(String::trim)
            .filter { it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }
            .toSet()
    ) = MetricAvailability(
        key = key,
        label = label,
        status = HealthAvailabilityStatus.AVAILABLE,
        source = source,
        coveredThrough = coverage,
        reason = reason,
        observation = observation,
        sourcePackages = sourcePackages
    )

    private fun unavailable(key: String, label: String, reason: String) = MetricAvailability(
        key = key,
        label = label,
        status = HealthAvailabilityStatus.UNAVAILABLE,
        source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
        coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
        reason = reason
    )

    private fun missingPermission(key: String, label: String) = MetricAvailability(
        key = key,
        label = label,
        status = HealthAvailabilityStatus.PERMISSION_NEEDED,
        source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
        coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
        reason = "Permiso de lectura no concedido"
    )

    private fun exerciseTypeLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elíptica"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Carrera"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Cinta de correr"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminar"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Senderismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Bicicleta estática"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Entrenamiento de fuerza"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Levantamiento de peso"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natación en piscina"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        else -> "Entrenamiento (tipo $type)"
    }

    private fun mealTypeLabel(type: Int): String = when (type) {
        MealType.MEAL_TYPE_BREAKFAST -> "Desayuno"
        MealType.MEAL_TYPE_LUNCH -> "Comida"
        MealType.MEAL_TYPE_DINNER -> "Cena"
        MealType.MEAL_TYPE_SNACK -> "Tentempié"
        else -> "Entrada nutricional"
    }

    private fun formatDuration(duration: Duration): String {
        val minutes = duration.toMinutes()
        val hours = minutes / 60
        val remaining = minutes % 60
        return if (hours > 0) "${hours}h ${remaining}m" else "${remaining} min"
    }

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000.0) formatDecimal(meters / 1000.0, "km") else formatDecimal(meters, "m")

    private fun formatDecimal(value: Double, unit: String): String =
        String.format(Locale.US, "%.1f %s", value, unit)

    private fun formatSignedDecimal(value: Double, unit: String): String =
        String.format(Locale.US, "%+.1f %s", value, unit)

    private fun formatMass(grams: Double): String = when {
        grams >= 1.0 -> formatDecimal(grams, "g")
        grams >= 0.001 -> formatDecimal(grams * 1_000.0, "mg")
        else -> formatDecimal(grams * 1_000_000.0, "µg")
    }

    private fun sanitize(value: String): String = value.replace('\n', ' ').replace('\r', ' ').trim()

}
