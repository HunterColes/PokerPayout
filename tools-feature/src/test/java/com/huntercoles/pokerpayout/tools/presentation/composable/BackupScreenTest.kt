package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.R as CoreR
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.BackupPreview
import com.huntercoles.pokerpayout.tools.presentation.BackupUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Backup (Tools) inside the app's shell on every cell of the device matrix: text fits and is never
 * clipped at any scroll position, 48 dp targets that don't overlap. Goldens on [DeviceMatrix.goldens]:
 * `S17_backup` (save and restore), `S17_backup_problem` (a file that isn't a backup) and
 * `S17_backup_preview` (a backup opened: what it holds, Add and Replace), the sheet drawn as it looks
 * open over the screen and its scrim (a modal window doesn't capture under Robolectric). A preview
 * that can only replace, and one partly from a later version, get the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BackupScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    /** False once the golden is taken: the screen behind a modal sheet can't be reached, so only the sheet is checked. */
    private val screenBehind = mutableStateOf(true)

    private val preview = BackupPreview(
        fileName = "poker-payout-2026-10-08.json",
        saved = LocalDate.of(2026, 10, 8),
        appVersion = "1.4.0",
        lines = listOf(
            BackupLine.Counted(CoreR.plurals.backup_line_presets, 3),
            BackupLine.Counted(CoreR.plurals.backup_line_nights, 12),
            BackupLine.Named(CoreR.string.backup_line_chip_set),
            BackupLine.Named(CoreR.string.backup_line_game),
            BackupLine.Named(CoreR.string.backup_line_sound),
            BackupLine.Named(CoreR.string.backup_line_tools),
        ),
        canMerge = true,
    )

    @Test
    fun saveAndRestore() = checkScreen("S17_backup", BackupUiState())

    @Test
    fun aFileThatIsNotABackup() = checkScreen("S17_backup_problem", BackupUiState(problem = CoreR.string.backup_problem_not_backup))

    @Test
    fun busy() = checkScreen(name = null, BackupUiState(busy = true))

    @Test
    fun aBackupOpened() = checkPreview("S17_backup_preview", preview)

    @Test
    fun settingsOnly() = checkPreview(
        name = null,
        preview.copy(lines = listOf(BackupLine.Named(CoreR.string.backup_line_sound)), canMerge = false, appVersion = null),
    )

    @Test
    fun partlyFromALaterVersion() = checkPreview(name = null, preview.copy(partial = true, saved = null, fileName = null))

    private fun checkScreen(name: String?, state: BackupUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                BackupContent(state, onBack = {}, onSave = {}, onOpen = {})
            }
        }
        val where = "${name ?: "S17 (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    private fun checkPreview(name: String?, preview: BackupPreview) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                Box(Modifier.fillMaxSize()) {
                    if (screenBehind.value) BackupContent(BackupUiState(preview = preview), onBack = {}, onSave = {}, onOpen = {})
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM)))
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        PokerSheetContent(title = stringResource(R.string.backup_preview_title)) {
                            BackupPreviewBody(preview, busy = false, onIntent = {})
                        }
                    }
                }
            }
        }
        screen.compose.waitForIdle()
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
        screenBehind.value = false
        screen.compose.waitForIdle()
        val where = "${name ?: "S17 preview (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
    }

    companion object {
        const val GROUP = "screens"
        private const val SCRIM = 0.62f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
