package com.example

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.MetricAvailability
import com.example.review.NightlyReviewGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NightlyReviewGeneratorTest {
    @Test
    fun `interprets an incomplete morning without calling isolated speed a workout`() {
        val report = report(
            LocalDate.of(2026, 8, 24),
            domain(
                HealthDomain.STEPS,
                HealthAvailabilityStatus.PARTIAL,
                metric("steps", "Pasos", HealthAvailabilityStatus.AVAILABLE, "325 pasos"),
                metric("distance", "Distancia", HealthAvailabilityStatus.AVAILABLE, "15.1 m")
            ),
            domain(
                HealthDomain.EXERCISE,
                HealthAvailabilityStatus.PARTIAL,
                metric("speed_average", "Velocidad media", HealthAvailabilityStatus.AVAILABLE, "2.2 km/h"),
                metric("speed_maximum", "Velocidad maxima", HealthAvailabilityStatus.AVAILABLE, "2.2 km/h")
            ),
            domain(
                HealthDomain.SLEEP,
                HealthAvailabilityStatus.AVAILABLE,
                metric("sleep_session_1", "Sueño", HealthAvailabilityStatus.AVAILABLE, "5h 47m; 21 tramos de fases")
            )
        )

        val review = NightlyReviewGenerator.generate(report, Instant.parse("2026-08-24T08:08:00Z"))

        assertTrue(review.summary.contains("sueño corto"))
        assertTrue(review.summary.contains("jornada sigue en curso"))
        assertTrue(review.facts.any { it.contains("por debajo de siete horas") })
        assertTrue(review.gaps.any { it.contains("provisional") })
        assertTrue(review.gaps.any { it.contains("no una sesión") })
        assertFalse(review.renderPlainText().contains("Velocidad media"))
        assertFalse(review.renderPlainText().contains("Entrenamiento registrado"))
    }

    @Test
    fun `interprets sleep against at least three recent comparable days`() {
        val today = report(
            LocalDate.of(2026, 8, 24),
            domain(
                HealthDomain.SLEEP,
                HealthAvailabilityStatus.AVAILABLE,
                metric("sleep_session_1", "Sueño", HealthAvailabilityStatus.AVAILABLE, "5h 47m")
            )
        )
        val history = listOf(
            sleepReport(LocalDate.of(2026, 8, 23), "7h 15m"),
            sleepReport(LocalDate.of(2026, 8, 22), "7h 30m"),
            sleepReport(LocalDate.of(2026, 8, 21), "7h 00m")
        )

        val review = NightlyReviewGenerator.generate(
            report = today,
            generatedAt = Instant.parse("2026-08-24T20:30:00Z"),
            recentReports = history
        )

        assertTrue(review.summary.contains("por debajo de tu referencia reciente"))
        assertTrue(review.facts.any { it.contains("1 h 28 min menos") && it.contains("7 h 15 min") })
        assertTrue(review.nextActions.any { it.contains("ventana de sueño") })
        assertTrue(review.nextActions.size <= 2)
    }

    @Test
    fun `uses personal recovery baseline cautiously and keeps real workout context`() {
        val today = report(
            LocalDate.of(2026, 8, 24),
            domain(
                HealthDomain.EXERCISE,
                HealthAvailabilityStatus.AVAILABLE,
                metric(
                    "exercise_session_1",
                    "Entrenamiento de fuerza",
                    HealthAvailabilityStatus.AVAILABLE,
                    "30 min",
                    "14:45 - 15:15"
                )
            ),
            recoveryDomain("72 ppm", "23.0 ms")
        )
        val history = listOf(
            recoveryReport(LocalDate.of(2026, 8, 23), "64 ppm", "35.0 ms"),
            recoveryReport(LocalDate.of(2026, 8, 22), "65 ppm", "34.0 ms"),
            recoveryReport(LocalDate.of(2026, 8, 21), "63 ppm", "36.0 ms")
        )

        val review = NightlyReviewGenerator.generate(
            report = today,
            generatedAt = Instant.parse("2026-08-24T20:30:00Z"),
            recentReports = history
        )

        assertTrue(review.facts.any { it.contains("Entrenamiento registrado") && it.contains("Entrenamiento de fuerza") })
        assertTrue(review.facts.any { it.contains("menor recuperación") && it.contains("sin valor diagnóstico") })
        assertTrue(review.nextActions.any { it.contains("próxima sesión") })
        assertFalse(review.renderPlainText().contains("debes"))
    }

    @Test
    fun `does not claim a trend with fewer than three comparable observations`() {
        val today = sleepReport(LocalDate.of(2026, 8, 24), "7h 10m")
        val history = listOf(
            sleepReport(LocalDate.of(2026, 8, 23), "6h 55m"),
            sleepReport(LocalDate.of(2026, 8, 22), "7h 05m")
        )

        val review = NightlyReviewGenerator.generate(
            report = today,
            generatedAt = Instant.parse("2026-08-24T20:30:00Z"),
            recentReports = history
        )

        assertTrue(review.gaps.any { it.contains("al menos 3 días comparables") })
        assertFalse(review.renderPlainText().contains("tendencia"))
    }

    private fun sleepReport(date: LocalDate, duration: String) = report(
        date,
        domain(
            HealthDomain.SLEEP,
            HealthAvailabilityStatus.AVAILABLE,
            metric("sleep_session_1", "Sueño", HealthAvailabilityStatus.AVAILABLE, duration)
        )
    )

    private fun recoveryReport(date: LocalDate, restingHeartRate: String, hrv: String) = report(
        date,
        recoveryDomain(restingHeartRate, hrv)
    )

    private fun recoveryDomain(restingHeartRate: String, hrv: String) = domain(
        HealthDomain.RESTING_HEART_RATE,
        HealthAvailabilityStatus.PARTIAL,
        metric("resting_heart_rate", "Frecuencia cardíaca en reposo", HealthAvailabilityStatus.AVAILABLE, restingHeartRate),
        metric("hrv_rmssd", "Variabilidad cardíaca (RMSSD)", HealthAvailabilityStatus.AVAILABLE, hrv)
    )

    private fun report(date: LocalDate, vararg domains: DomainAvailability) = DayAvailabilityReport(
        date = date,
        zoneId = ZoneId.of("Europe/Madrid"),
        domains = domains.toList(),
        overallStatus = HealthAvailabilityStatus.PARTIAL,
        unavailableDomains = domains.filter { it.status == HealthAvailabilityStatus.UNAVAILABLE }.map { it.domain }
    )

    private fun domain(
        domain: HealthDomain,
        status: HealthAvailabilityStatus,
        vararg metrics: MetricAvailability
    ) = DomainAvailability(
        domain = domain,
        status = status,
        source = "Health Connect",
        coveredThrough = "Total del día",
        reason = when (domain) {
            HealthDomain.SLEEP -> "Sin sesión de sueño utilizable"
            else -> "Cobertura de prueba"
        },
        metrics = metrics.toList()
    )

    private fun metric(
        key: String,
        label: String,
        status: HealthAvailabilityStatus,
        observation: String? = null,
        coverage: String = "Total del día"
    ) = MetricAvailability(
        key = key,
        label = label,
        status = status,
        source = if (status == HealthAvailabilityStatus.AVAILABLE) "Health Connect" else "Fuente no disponible en esta lectura",
        coveredThrough = coverage,
        reason = if (status == HealthAvailabilityStatus.PERMISSION_NEEDED) "Permiso de lectura no concedido" else "Sin registro para este día",
        observation = observation
    )
}
