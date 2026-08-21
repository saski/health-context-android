package com.example

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.MetricAvailability
import com.example.review.NightlyReviewGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NightlyReviewGeneratorTest {
    @Test
    fun `summarizes observed facts and explicit gaps without evaluating health`() {
        val report = report(
            domain(
                HealthDomain.STEPS,
                HealthAvailabilityStatus.PARTIAL,
                metric("steps", "Pasos", HealthAvailabilityStatus.AVAILABLE, "6.210 pasos"),
                metric("distance", "Distancia", HealthAvailabilityStatus.AVAILABLE, "4,8 km"),
                metric("active_calories", "Calorías activas", HealthAvailabilityStatus.UNAVAILABLE)
            ),
            domain(
                HealthDomain.EXERCISE,
                HealthAvailabilityStatus.AVAILABLE,
                metric("exercise_session_1", "Elíptica", HealthAvailabilityStatus.AVAILABLE, "30 min", "18:00 - 18:30")
            ),
            domain(
                HealthDomain.SLEEP,
                HealthAvailabilityStatus.UNAVAILABLE,
                metric("sleep_sessions", "Sesiones de sueño", HealthAvailabilityStatus.UNAVAILABLE)
            )
        )

        val review = NightlyReviewGenerator.generate(report, Instant.parse("2026-08-20T20:30:00Z"))

        assertEquals("2 de 3 áreas aportan datos; sueño queda sin cobertura.", review.summary)
        assertTrue(review.facts.contains("Actividad diaria: Pasos 6.210 pasos; Distancia 4,8 km."))
        assertTrue(
            review.facts.contains(
                "Entrenamientos: Elíptica 30 min (18:00 - 18:30; source: Health Connect)."
            )
        )
        assertTrue(review.gaps.contains("Sueño: Sin sesión de sueño utilizable."))
        assertFalse(review.renderPlainText().contains("0 horas"))
        assertFalse(review.renderPlainText().contains("bien"))
        assertFalse(review.renderPlainText().contains("mal"))
    }

    @Test
    fun `puts workout facts first so bounded retrieval keeps the training context`() {
        val report = report(
            domain(
                HealthDomain.STEPS,
                HealthAvailabilityStatus.AVAILABLE,
                metric("steps", "Pasos", HealthAvailabilityStatus.AVAILABLE, "5.413 pasos")
            ),
            domain(
                HealthDomain.NUTRITION,
                HealthAvailabilityStatus.PARTIAL,
                metric("nutrition_energy_total", "Energía total", HealthAvailabilityStatus.AVAILABLE, "950 kcal")
            ),
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
            )
        )

        val review = NightlyReviewGenerator.generate(report, Instant.parse("2026-08-20T20:30:00Z"))

        assertEquals(
            "Entrenamientos: Entrenamiento de fuerza 30 min (14:45 - 15:15; source: Health Connect).",
            review.facts.first()
        )
    }

    @Test
    fun `offers at most two cautious actions and explains recovery uncertainty`() {
        val report = report(
            domain(
                HealthDomain.EXERCISE,
                HealthAvailabilityStatus.AVAILABLE,
                metric("exercise_session_1", "Fuerza", HealthAvailabilityStatus.AVAILABLE, "45 min")
            ),
            domain(
                HealthDomain.SLEEP,
                HealthAvailabilityStatus.UNAVAILABLE,
                metric("sleep_sessions", "Sesiones de sueño", HealthAvailabilityStatus.UNAVAILABLE)
            ),
            domain(
                HealthDomain.NUTRITION,
                HealthAvailabilityStatus.PARTIAL,
                metric("nutrition_energy_total", "Energía total", HealthAvailabilityStatus.AVAILABLE, "1.200 kcal"),
                metric("hydration", "Hidratación", HealthAvailabilityStatus.UNAVAILABLE)
            ),
            domain(
                HealthDomain.RESTING_HEART_RATE,
                HealthAvailabilityStatus.PERMISSION_NEEDED,
                metric("resting_heart_rate", "Frecuencia cardíaca en reposo", HealthAvailabilityStatus.PERMISSION_NEEDED)
            )
        )

        val review = NightlyReviewGenerator.generate(report, Instant.parse("2026-08-20T20:30:00Z"))

        assertEquals(2, review.nextActions.size)
        assertTrue(review.nextActions.first().contains("recuperación"))
        assertTrue(review.nextActions.any { it.contains("permisos") })
        assertFalse(review.nextActions.any { it.contains("debes") })
    }

    private fun report(vararg domains: DomainAvailability) = DayAvailabilityReport(
        date = LocalDate.of(2026, 8, 20),
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
