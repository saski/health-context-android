package com.example

import com.example.data.model.DayAvailabilityReport
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.MetricAvailability
import com.example.export.DailyContextMarkdownRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyContextMarkdownRendererTest {
    @Test
    fun `renders date named artifact and never turns an unavailable domain into zero`() {
        val report = DayAvailabilityReport(
            date = LocalDate.of(2026, 8, 18),
            zoneId = ZoneId.of("Europe/Madrid"),
            domains = listOf(
                DomainAvailability(HealthDomain.STEPS, HealthAvailabilityStatus.AVAILABLE, "Health Connect", "day", "aggregate", "1200 steps"),
                DomainAvailability(HealthDomain.SLEEP, HealthAvailabilityStatus.UNAVAILABLE, "Fuente no disponible en esta lectura", "Sin registro utilizable", "wearable not worn")
            ),
            overallStatus = HealthAvailabilityStatus.PARTIAL,
            unavailableDomains = listOf(HealthDomain.SLEEP)
        )

        val markdown = DailyContextMarkdownRenderer.render(report, Instant.parse("2026-08-18T20:00:00Z"))

        assertTrue(DailyContextMarkdownRenderer.fileName(report) == "health-context-2026-08-18.md")
        assertTrue(markdown.contains("overall_status: partial"))
        assertTrue(markdown.contains("gap: unavailable; no value is inferred as zero"))
        assertFalse(markdown.contains("sleep: 0"))
    }

    @Test
    fun `renders every workout and metric with provenance without route data`() {
        val report = DayAvailabilityReport(
            date = LocalDate.of(2026, 8, 19),
            zoneId = ZoneId.of("Europe/Madrid"),
            domains = listOf(
                DomainAvailability(
                    domain = HealthDomain.EXERCISE,
                    status = HealthAvailabilityStatus.AVAILABLE,
                    source = "com.google.android.apps.fitness",
                    coveredThrough = "día completo",
                    reason = "1 entrenamiento registrado",
                    metricSummary = "1 de 1 métricas con datos",
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
                    )
                )
            ),
            overallStatus = HealthAvailabilityStatus.AVAILABLE,
            unavailableDomains = emptyList()
        )

        val markdown = DailyContextMarkdownRenderer.render(report, Instant.parse("2026-08-19T13:00:00Z"))

        assertTrue(markdown.contains("schema: health-context/v2"))
        assertTrue(markdown.contains("### Elíptica"))
        assertTrue(markdown.contains("source: com.google.android.apps.fitness"))
        assertTrue(markdown.contains("observation: 30 min"))
        assertFalse(markdown.contains("latitude"))
        assertFalse(markdown.contains("longitude"))
    }
}
