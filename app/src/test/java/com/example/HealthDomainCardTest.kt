package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.DomainAvailability
import com.example.data.model.HealthAvailabilityStatus
import com.example.data.model.HealthDomain
import com.example.data.model.HealthStatusMapper
import com.example.data.model.MetricAvailability
import com.example.ui.components.HealthDomainCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HealthDomainCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `card shows observed values and names missing metrics instead of generic counts`() {
        val availability = DomainAvailability(
            domain = HealthDomain.STEPS,
            status = HealthAvailabilityStatus.PARTIAL,
            source = "Health Connect",
            coveredThrough = "Total del día",
            reason = "Hay datos y huecos explícitos en este dominio",
            metricSummary = "2 de 4 métricas con datos",
            metrics = listOf(
                metric("steps", "Pasos", "4.218 pasos"),
                metric("distance", "Distancia", "3.2 km"),
                missing("active_calories", "Calorías activas"),
                missing("steps_cadence", "Cadencia media de pasos")
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                HealthDomainCard(availability)
            }
        }

        assertTextExists("Pasos")
        assertTextExists("4.218 pasos")
        assertTextExists("Distancia")
        assertTextExists("3.2 km")
        assertTextExists("Sin registro: Calorías activas · Cadencia media de pasos")
        assertTextDoesNotExist("2 de 4 métricas con datos")
        assertTextDoesNotExist("Hay datos y huecos explícitos en este dominio")
    }

    @Test
    fun `permission gap is separated from neutral missing data`() {
        val availability = DomainAvailability(
            domain = HealthDomain.RESTING_HEART_RATE,
            status = HealthAvailabilityStatus.PERMISSION_NEEDED,
            source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
            coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
            reason = "Faltan permisos de lectura para este dominio",
            metrics = listOf(
                missing("oxygen_saturation", "Saturación de oxígeno"),
                MetricAvailability(
                    key = "heart_rate",
                    label = "Frecuencia cardíaca",
                    status = HealthAvailabilityStatus.PERMISSION_NEEDED,
                    source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
                    coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
                    reason = "Permiso de lectura no concedido"
                )
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                HealthDomainCard(availability)
            }
        }

        assertTextExists("Sin registro: Saturación de oxígeno")
        assertTextExists("Permiso necesario: Frecuencia cardíaca")
    }

    private fun assertTextExists(text: String) {
        composeTestRule.onNodeWithText(text).fetchSemanticsNode()
    }

    private fun assertTextDoesNotExist(text: String) {
        assertTrue(composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private fun metric(key: String, label: String, observation: String) = MetricAvailability(
        key = key,
        label = label,
        status = HealthAvailabilityStatus.AVAILABLE,
        source = "Health Connect",
        coveredThrough = "Total del día",
        reason = "Dato observado",
        observation = observation
    )

    private fun missing(key: String, label: String) = MetricAvailability(
        key = key,
        label = label,
        status = HealthAvailabilityStatus.UNAVAILABLE,
        source = HealthStatusMapper.SOURCE_NOT_AVAILABLE,
        coveredThrough = HealthStatusMapper.NO_USABLE_RECORD,
        reason = "Sin registro para este día"
    )
}
