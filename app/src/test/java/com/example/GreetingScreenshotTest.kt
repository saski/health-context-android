package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.HealthUiState
import com.example.data.model.SdkAvailability
import com.example.data.repository.FakeHealthConnectRepository
import com.example.ui.HealthAvailabilityScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val fakeRepo = FakeHealthConnectRepository()
    val zoneId = ZoneId.of("Europe/Madrid")
    val date = LocalDate.of(2026, 8, 18)

    val todayReport = runBlocking { fakeRepo.loadDayAvailability(date, zoneId) }
    val yesterdayReport = runBlocking { fakeRepo.loadDayAvailability(date.minusDays(1), zoneId) }

    composeTestRule.setContent {
      MyApplicationTheme {
        HealthAvailabilityScreen(
          uiState = HealthUiState(
            sdkAvailability = SdkAvailability.AVAILABLE,
            lastRefreshed = Instant.parse("2026-08-18T10:00:00Z"),
            todayReport = todayReport,
            yesterdayReport = yesterdayReport
          ),
          onRefresh = {},
          onSelectTab = {},
          onRequestPermissions = {},
          onOpenPlayStoreOrSettings = {},
          onShowDataBoundaries = {},
          onChooseExportFolder = {},
          onExport = {},
          onToggleAutomaticExport = {},
          zoneId = zoneId
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
