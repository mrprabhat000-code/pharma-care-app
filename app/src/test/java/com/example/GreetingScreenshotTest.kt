package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.Medicine
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Column(modifier = Modifier.padding(innerPadding)) {
            MedicalHeader(
              developerName = "Prabhat Singh",
              contactNumber = "9696971627",
              onCallClick = {},
              onBackupClick = {}
            )
            
            StatsSection(
              totalCount = 4,
              expiredCount = 1,
              nearExpiryCount = 1,
              lowStockCount = 2,
              totalInvestment = 1250.0,
              totalRevenue = 1580.0
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            MedicineStockCard(
              medicine = Medicine(
                id = 101,
                name = "Amoxicillin 500mg Capsule",
                mrp = 120.00,
                buyPrice = 75.50,
                sellPrice = 95.00,
                expiryTimestamp = System.currentTimeMillis() + 15L * 24 * 60 * 60 * 1000,
                stockQty = 4,
                batchNumber = "AMX-8012"
              ),
              currentTimestamp = System.currentTimeMillis(),
              onEditClick = {},
              onDeleteClick = {}
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
