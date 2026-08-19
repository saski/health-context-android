package com.example

import com.example.data.model.GenericRecordData
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.HealthStatusMapper
import com.example.data.model.MetricAvailability
import com.example.data.model.StepsDomainData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthStatusMapperTest {

    @Test
    fun `expanded domain is partial when it contains observed metrics and honest gaps`() {
        val result = HealthStatusMapper.mapMetricDomain(
            domain = HealthDomain.RESTING_HEART_RATE,
            metrics = listOf(
                MetricAvailability(
                    key = "heart_rate",
                    label = "Frecuencia cardíaca",
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.nothing.smartcenter",
                    coveredThrough = "día completo",
                    reason = "Mediciones disponibles",
                    observation = "media 72 ppm"
                ),
                MetricAvailability(
                    key = "oxygen_saturation",
                    label = "Saturación de oxígeno",
                    status = HealthAvailabilityStatus.UNAVAILABLE,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Sin registro para este día"
                )
            ),
            noDataReason = "Sin indicadores disponibles"
        )

        assertEquals(HealthAvailabilityStatus.PARTIAL, result.status)
        assertEquals("1 de 2 métricas con datos", result.metricSummary)
        assertEquals(2, result.metrics.size)
    }

    @Test
    fun `exercise domain is available when Fit session is observed`() {
        val result = HealthStatusMapper.mapMetricDomain(
            domain = HealthDomain.EXERCISE,
            metrics = listOf(
                MetricAvailability(
                    key = "exercise_session_fit_1",
                    label = "Elíptica",
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.google.android.apps.fitness",
                    coveredThrough = "12:05 - 12:35",
                    reason = "Sesión de entrenamiento registrada",
                    observation = "30 min"
                )
            ),
            noDataReason = "Sin entrenamientos registrados"
        )

        assertEquals(HealthAvailabilityStatus.AVAILABLE, result.status)
        assertEquals("1 de 1 métricas con datos", result.metricSummary)
        assertEquals("com.google.android.apps.fitness", result.source)
    }

    @Test
    fun `when permission is missing for steps domain status is PERMISSION_NEEDED`() {
        val result = HealthStatusMapper.mapStepsDomain(
            StepsDomainData(
                isPermissionGranted = false,
                totalSteps = null
            )
        )
        assertEquals(HealthAvailabilityStatus.PERMISSION_NEEDED, result.status)
        assertEquals(HealthStatusMapper.SOURCE_NOT_AVAILABLE, result.source)
        assertEquals(HealthStatusMapper.NO_USABLE_RECORD, result.coveredThrough)
        assertEquals("Permiso de lectura de pasos no concedido", result.reason)
    }

    @Test
    fun `when steps record is missing status is UNAVAILABLE and never maps to zero as error`() {
        val result = HealthStatusMapper.mapStepsDomain(
            StepsDomainData(
                isPermissionGranted = true,
                totalSteps = null
            )
        )
        assertEquals(HealthAvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals(HealthStatusMapper.SOURCE_NOT_AVAILABLE, result.source)
        assertEquals(HealthStatusMapper.NO_USABLE_RECORD, result.coveredThrough)
        assertNull(result.metricSummary)
    }

    @Test
    fun `when aggregate steps are present status is AVAILABLE with correct aggregate provenance`() {
        val result = HealthStatusMapper.mapStepsDomain(
            StepsDomainData(
                isPermissionGranted = true,
                totalSteps = 7500L,
                dataOrigins = setOf("com.google.android.apps.fitness", "com.samsung.health")
            )
        )
        assertEquals(HealthAvailabilityStatus.AVAILABLE, result.status)
        assertTrue(result.source.contains("com.google.android.apps.fitness"))
        assertTrue(result.source.contains("com.samsung.health"))
        assertEquals("7500 pasos agregados", result.metricSummary)
    }

    @Test
    fun `when sleep wearable was not worn or did not sync status is UNAVAILABLE not error`() {
        val result = HealthStatusMapper.mapSleepDomain(
            GenericRecordData(
                isPermissionGranted = true,
                hasRecord = false
            )
        )
        assertEquals(HealthAvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals(HealthStatusMapper.SOURCE_NOT_AVAILABLE, result.source)
        assertEquals(HealthStatusMapper.NO_USABLE_RECORD, result.coveredThrough)
        assertEquals("Dispositivo no utilizado o sin sincronización de sesión de sueño", result.reason)
    }

    @Test
    fun `when sleep permission is missing status is PERMISSION_NEEDED`() {
        val result = HealthStatusMapper.mapSleepDomain(
            GenericRecordData(
                isPermissionGranted = false,
                hasRecord = false
            )
        )
        assertEquals(HealthAvailabilityStatus.PERMISSION_NEEDED, result.status)
        assertEquals("Permiso de lectura de sueño no concedido", result.reason)
    }

    @Test
    fun `when weight measurement is missing status is UNAVAILABLE as event-based absence`() {
        val result = HealthStatusMapper.mapWeightDomain(
            GenericRecordData(
                isPermissionGranted = true,
                hasRecord = false
            )
        )
        assertEquals(HealthAvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals("Sin medición puntual de peso registrada en este día", result.reason)
    }

    @Test
    fun `when nutrition is not manually logged status is UNAVAILABLE not ingestion failure`() {
        val result = HealthStatusMapper.mapNutritionDomain(
            GenericRecordData(
                isPermissionGranted = true,
                hasRecord = false
            )
        )
        assertEquals(HealthAvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals("Sin registro nutricional manual en este día", result.reason)
    }

    @Test
    fun `when resting heart rate is not synced status is UNAVAILABLE and never zero`() {
        val result = HealthStatusMapper.mapRestingHeartRateDomain(
            GenericRecordData(
                isPermissionGranted = true,
                hasRecord = false
            )
        )
        assertEquals(HealthAvailabilityStatus.UNAVAILABLE, result.status)
        assertEquals("Dispositivo no utilizado o sin sincronización de frecuencia en reposo", result.reason)
    }

    @Test
    fun `overall status is COMPLETE only when all domains are AVAILABLE`() {
        val date = LocalDate.of(2026, 8, 18)
        val zone = ZoneId.of("Europe/Madrid")

        val allSixAvailable = listOf(
            HealthStatusMapper.mapStepsDomain(StepsDomainData(true, 5000L)),
            HealthStatusMapper.mapMetricDomain(
                HealthDomain.EXERCISE,
                listOf(
                    MetricAvailability(
                        "exercise_session_1",
                        "Elíptica",
                        HealthAvailabilityStatus.AVAILABLE,
                        "com.google.android.apps.fitness",
                        "12:00 - 12:30",
                        "Sesión registrada",
                        "30 min"
                    )
                ),
                "Sin entrenamientos"
            ),
            HealthStatusMapper.mapSleepDomain(GenericRecordData(true, true, extraFactualInfo = "7h")),
            HealthStatusMapper.mapWeightDomain(GenericRecordData(true, true, extraFactualInfo = "70kg")),
            HealthStatusMapper.mapNutritionDomain(GenericRecordData(true, true, extraFactualInfo = "Comida")),
            HealthStatusMapper.mapRestingHeartRateDomain(GenericRecordData(true, true, extraFactualInfo = "60bpm"))
        )

        val reportComplete = HealthStatusMapper.buildDayReport(date, zone, allSixAvailable)
        assertEquals(HealthAvailabilityStatus.AVAILABLE, reportComplete.overallStatus)
        assertTrue(reportComplete.unavailableDomains.isEmpty())
    }

    @Test
    fun `overall status is PARTIAL when a required domain is omitted`() {
        val report = HealthStatusMapper.buildDayReport(
            LocalDate.of(2026, 8, 19),
            ZoneId.of("Europe/Madrid"),
            listOf(
                HealthStatusMapper.mapStepsDomain(StepsDomainData(true, 5000L))
            )
        )

        assertEquals(HealthAvailabilityStatus.PARTIAL, report.overallStatus)
        assertTrue(report.unavailableDomains.contains(HealthDomain.EXERCISE))
    }

    @Test
    fun `overall status is PARTIAL when any domain is missing and lists unavailable domains`() {
        val date = LocalDate.of(2026, 8, 18)
        val zone = ZoneId.of("Europe/Madrid")

        val partialDomains = listOf(
            HealthStatusMapper.mapStepsDomain(StepsDomainData(true, 5000L)),
            HealthStatusMapper.mapSleepDomain(GenericRecordData(true, false)), // UNAVAILABLE
            HealthStatusMapper.mapWeightDomain(GenericRecordData(true, true, extraFactualInfo = "70kg")),
            HealthStatusMapper.mapNutritionDomain(GenericRecordData(false, false)), // PERMISSION_NEEDED
            HealthStatusMapper.mapRestingHeartRateDomain(GenericRecordData(true, true, extraFactualInfo = "60bpm"))
        )

        val reportPartial = HealthStatusMapper.buildDayReport(date, zone, partialDomains)
        assertEquals(HealthAvailabilityStatus.PARTIAL, reportPartial.overallStatus)
        assertEquals(3, reportPartial.unavailableDomains.size)
        assertTrue(reportPartial.unavailableDomains.contains(HealthDomain.EXERCISE))
        assertTrue(reportPartial.unavailableDomains.contains(HealthDomain.SLEEP))
        assertTrue(reportPartial.unavailableDomains.contains(HealthDomain.NUTRITION))
    }
}
