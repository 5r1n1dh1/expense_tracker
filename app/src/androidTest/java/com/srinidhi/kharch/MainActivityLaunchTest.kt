package com.srinidhi.kharch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-launch smoke test — the regression test for "app doesn't open on
 * Galaxy S23 / One UI 9".
 *
 * Launches the real [MainActivity] (with HiltTestApplication via
 * HiltTestRunner) and asserts the dashboard first frame is reached.
 * This is exactly the path that crashed silently on One UI 9 because:
 * - enableEdgeToEdge() was called before super.onCreate()
 * - the windowBackground shape drawable was rejected by Samsung's
 *   window manager
 * - WorkManager was initialised without a HiltWorkerFactory
 *
 * Runs in CI on API 34/35/36 (instrumented.yml). No local Gradle.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldLaunchReachesDashboardFirstFrame() {
        composeRule.waitForIdle()
        composeRule.waitUntil(
            conditionDescription = "dashboard chart frame visible",
            timeoutMillis = 10_000,
        ) {
            composeRule.onAllNodesWithTag("dashboard-chart-frame")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard-chart-frame").assertIsDisplayed()
    }

    @Test
    fun coldLaunchReachesCategoryFrame() {
        composeRule.waitForIdle()
        composeRule.waitUntil(
            conditionDescription = "dashboard category frame visible",
            timeoutMillis = 10_000,
        ) {
            composeRule.onAllNodesWithTag("dashboard-category-frame")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard-category-frame").assertIsDisplayed()
    }
}
