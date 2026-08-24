package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.HealthUiState
import com.example.ui.HealthAvailabilityScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthPermissionActionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `all required permissions expose management instead of another grant request`() {
        var requested = 0
        var managed = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                HealthAvailabilityScreen(
                    uiState = HealthUiState(requiredPermissionsGranted = true),
                    onRefresh = {},
                    onSelectTab = {},
                    onRequestPermissions = { requested += 1 },
                    onManagePermissions = { managed += 1 },
                    onOpenPlayStoreOrSettings = {},
                    onShowDataBoundaries = {},
                    onChooseExportFolder = {},
                    onExport = {},
                    onToggleAutomaticExport = {},
                    onToggleNightlyReview = {},
                    onGenerateNightlyReviewNow = {},
                    onShowNightlyReview = {},
                    onNightlyReviewFeedback = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Gestionar permisos")
            .assertIsDisplayed()
            .performClick()

        assertEquals(0, requested)
        assertEquals(1, managed)
    }

    @Test
    fun `missing required permissions expose the grant action`() {
        var requested = 0
        var managed = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                HealthAvailabilityScreen(
                    uiState = HealthUiState(requiredPermissionsGranted = false),
                    onRefresh = {},
                    onSelectTab = {},
                    onRequestPermissions = { requested += 1 },
                    onManagePermissions = { managed += 1 },
                    onOpenPlayStoreOrSettings = {},
                    onShowDataBoundaries = {},
                    onChooseExportFolder = {},
                    onExport = {},
                    onToggleAutomaticExport = {},
                    onToggleNightlyReview = {},
                    onGenerateNightlyReviewNow = {},
                    onShowNightlyReview = {},
                    onNightlyReviewFeedback = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Conceder permisos")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, requested)
        assertEquals(0, managed)
    }
}
