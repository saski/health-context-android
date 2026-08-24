package com.example

import com.example.ui.AutomationHealth
import com.example.ui.AutomationHealthState
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationHealthTest {
    @Test
    fun `is paused only when both automatic paths are disabled`() {
        assertEquals(
            AutomationHealthState.PAUSED,
            AutomationHealth.evaluate(false, false, true, true, true, null, null)
        )
    }

    @Test
    fun `requires attention for missing access or a recorded technical failure`() {
        assertEquals(
            AutomationHealthState.ATTENTION_REQUIRED,
            AutomationHealth.evaluate(true, true, true, true, false, null, null)
        )
        assertEquals(
            AutomationHealthState.ATTENTION_REQUIRED,
            AutomationHealth.evaluate(true, true, true, true, true, "Última exportación automática falló", null)
        )
    }

    @Test
    fun `is ready when both schedules have their technical prerequisites`() {
        assertEquals(
            AutomationHealthState.READY,
            AutomationHealth.evaluate(true, true, true, true, true, "Completada", "Completada")
        )
    }
}
