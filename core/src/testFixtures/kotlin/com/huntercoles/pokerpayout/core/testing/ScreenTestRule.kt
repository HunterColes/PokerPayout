package com.huntercoles.pokerpayout.core.testing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

/**
 * A Compose test rule whose activity starts on [config]'s screen: size, orientation, density,
 * locale (en-US) and system font scale are applied before the activity launches.
 *
 * Use it from a Robolectric test with native graphics, typically parameterized over
 * [DeviceMatrix.all] or [DeviceMatrix.goldens]:
 * ```
 * @RunWith(ParameterizedRobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [34])
 * class MyScreenTest(config: ScreenConfig) {
 *     @get:Rule val screen = ScreenTestRule(config)
 * }
 * ```
 */
class ScreenTestRule(val config: ScreenConfig) : TestRule {
    val compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>()

    private val chain: RuleChain = RuleChain.outerRule(ConfigureScreen(config)).around(compose)

    override fun apply(base: Statement, description: Description): Statement = chain.apply(base, description)

    private class ConfigureScreen(private val config: ScreenConfig) : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                RuntimeEnvironment.setQualifiers(config.qualifiers)
                RuntimeEnvironment.setFontScale(config.fontScale)
                base.evaluate()
            }
        }
    }
}
