package com.liftly.app

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualRegressionSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootRendersToScreenshot() {
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
    }
}
