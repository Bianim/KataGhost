package io.github.bianim.kataghost

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.github.bianim.kataghost.engine.BoardSnapshot
import io.github.bianim.kataghost.engine.EnginePhase
import io.github.bianim.kataghost.engine.GoRules
import io.github.bianim.kataghost.engine.KataGoViewModel
import io.github.bianim.kataghost.ui.AppPreferences
import io.github.bianim.kataghost.ui.record.BoardPoint
import io.github.bianim.kataghost.ui.record.RecordViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsFeatureUiTest {
    @get:Rule(order = 0) val locale = AppLocaleRule("zh")
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var document: RecordViewModel
    private lateinit var engine: KataGoViewModel

    @Before fun freshManualGame() {
        ui.runOnUiThread {
            document = ViewModelProvider(ui.activity)[RecordViewModel::class.java]
            engine = ViewModelProvider(ui.activity)[KataGoViewModel::class.java]
            document.updatePreferences(AppPreferences(engineEnabled = false))
            document.record.applyRules(GoRules.CHINESE, 7.5f)
            document.record.newGame()
        }
        ui.waitForIdle()
    }

    @After fun restoreDefaults() {
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.runOnUiThread {
            document.updatePreferences(AppPreferences(engineEnabled = false))
            document.record.applyRules(GoRules.CHINESE, 7.5f)
            document.record.reset()
            ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitForIdle()
    }

    private fun openSettings() {
        ui.onNodeWithContentDescription("更多选项").performClick()
        ui.onNodeWithText("设置").performClick()
    }
    private fun setEngine(enabled: Boolean) {
        val on = ui.onNodeWithTag("engine-switch").fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
        if (on != enabled) ui.onNodeWithTag("engine-switch").performClick()
    }
    private fun count() = ui.runOnIdle { document.record.basePosition.moves.size }
    private fun waitForMoves(expected: Int) = ui.waitUntil(30000) { document.record.basePosition.moves.size >= expected }
    private fun waitForReady() = ui.waitUntil(30000) { engine.controller.state.value.let { it.completedAnalysis?.position == document.record.position && it.phase in listOf(EnginePhase.READY, EnginePhase.ANALYZING) } }
    private fun play(coordinate: String) {
        val point = BoardPoint.parse(coordinate)
        ui.onNodeWithTag("board-input").performTouchInput {
            click(Offset(width * (41 + point.column * 31) / 640f, width * (41 + point.row * 31) / 640f))
        }
    }
    private fun playLegal() {
        val point = ui.runOnIdle {
            val board = BoardSnapshot.from(document.record.position)
            BoardPoint.fromIndex((0..360).first(board::isLegal))!!.label
        }
        play(point)
    }

    @Test fun handicapPersistsAndNavigationSelectsHumanTurns() {
        ui.onNodeWithText("候选变化").assertIsDisplayed()
        openSettings()
        val title = ui.onNodeWithText("白方让子").getUnclippedBoundsInRoot()
        val hint = ui.onNodeWithText("新建棋局时生效。让子摆在星位。").getUnclippedBoundsInRoot()
        val dropdown = ui.onNodeWithText("不让").getUnclippedBoundsInRoot()
        assertTrue(hint.top >= title.bottom)
        assertTrue(dropdown.left > title.right && dropdown.top < hint.bottom && dropdown.bottom > title.top)
        ui.onNodeWithTag("wait-win-rate-change").assertIsOn().performClick()
        ui.onNodeWithTag("auto-white").performClick()
        ui.onNodeWithText("不让").performClick()
        ui.onNodeWithText("让九子").performClick()
        ui.onNodeWithTag("apply-settings").performClick()
        val saved = AppPreferences.load(ui.activity)
        assertEquals(9, saved.whiteHandicap)
        assertFalse(saved.waitForWinRateChange)
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("更多选项").performClick()
        ui.onNodeWithTag("menu-clear-board").performClick()
        ui.onNodeWithTag("confirm-clear-board").performClick()
        ui.runOnIdle {
            val position = document.record.position
            assertEquals(2, position.nextPlayer)
            assertEquals(9, (0..360).count { BoardSnapshot.from(position).color(it) == 1 })
            assertTrue(document.record.exportSgf().contains("HA[9]"))
            assertTrue(document.record.play(BoardPoint.parse("Q5").index))
            assertTrue(document.record.play(BoardPoint.parse("Q6").index))
            assertTrue(document.record.play(BoardPoint.parse("Q7").index))
        }
        ui.onNodeWithContentDescription("上一手").performClick()
        assertEquals(1, count())
        ui.onNodeWithContentDescription("上一手").assertIsNotEnabled()
        ui.onNodeWithContentDescription("下一手").performClick()
        assertEquals(3, count())
        ui.onNodeWithContentDescription("下一手").assertIsNotEnabled()
        val board = ui.onNodeWithTag("go-board").getUnclippedBoundsInRoot()
        val controls = ui.onNodeWithTag("territory").getUnclippedBoundsInRoot()
        ui.onNodeWithTag("territory").performClick()
        ui.onNodeWithText("地盘归属估计").assertIsDisplayed()
        assertEquals(board, ui.onNodeWithTag("go-board").getUnclippedBoundsInRoot())
        assertEquals(controls, ui.onNodeWithTag("territory-back").getUnclippedBoundsInRoot())
        ui.onNodeWithTag("territory-back").performClick()
    }

    @Test fun undoBeforeAiReplyReturnsToTheHumanTurn() {
        for (autoBlack in listOf(false, true)) {
            ui.runOnIdle {
                document.updatePreferences(AppPreferences(engineEnabled = false, autoBlack = autoBlack, autoWhite = !autoBlack))
                document.record.newGame()
                for (coordinate in listOf("D4", "Q16", "D16", "Q4").take(if (autoBlack) 4 else 3)) {
                    assertTrue(document.record.play(BoardPoint.parse(coordinate).index))
                }
            }
            ui.onNodeWithContentDescription("上一手").performClick()
            assertEquals(if (autoBlack) 3 else 2, count())
            ui.runOnIdle { assertEquals(if (autoBlack) 2 else 1, document.record.position.nextPlayer) }
            ui.onNodeWithContentDescription("下一手").assertIsNotEnabled()
            for (automatic in listOf(false, true)) {
                ui.runOnIdle {
                    document.updatePreferences(document.preferences.copy(autoBlack = automatic, autoWhite = automatic))
                }
                ui.onNodeWithContentDescription("下一手").performClick()
                assertEquals(if (autoBlack) 4 else 3, count())
                ui.onNodeWithContentDescription("上一手").performClick()
                assertEquals(if (autoBlack) 3 else 2, count())
            }
        }
    }

    @Test fun aiSearchesWhileThePreviousMoveDeltaFades() = verifyDeltaFade(false)

    @Test fun aiAlsoWaitsForScoreChangeToFade() = verifyDeltaFade(true)

    private fun verifyDeltaFade(scoreOnly: Boolean) {
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle {
                document.updatePreferences(AppPreferences(engineEnabled = true, autoWhite = true, maxVisits = 4, scoreChangeOnly = scoreOnly))
                assertTrue(document.record.play(BoardPoint.parse("Q16").index))
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 30000
            while (engine.controller.state.value.opponentResult == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                ui.mainClock.advanceTimeByFrame()
                Thread.sleep(10)
            }
            assertNotNull(engine.controller.state.value.opponentResult)
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithTag("win-rate-delta").assertIsDisplayed()
            val delta = ui.onNodeWithTag("win-rate-delta", useUnmergedTree = true).onChildren().onFirst().fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            assertEquals(!scoreOnly, delta.endsWith("%"))
            assertEquals(1, count())
            ui.mainClock.advanceTimeBy(1600)
            waitForMoves(2)
            assertEquals(2, count())
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun automaticPassBlocksManualPassUntilNoticeFinishes() {
        val pass = io.github.bianim.kataghost.engine.PASS
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnUiThread { check(document.record.play(pass, automatic = true)) }
            ui.mainClock.advanceTimeBy(500)
            ui.onNodeWithText(ui.activity.getString(R.string.black_pass_notice)).assertIsDisplayed()
            ui.onNodeWithTag("board-pass").assertIsNotEnabled()
            ui.mainClock.advanceTimeBy(2000)
            ui.onNodeWithText(ui.activity.getString(R.string.black_pass_notice)).assertDoesNotExist()
            ui.onNodeWithTag("board-pass").assertIsEnabled()
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun automaticSecondPassOpensSettlementOnceAfterNotice() {
        val pass = io.github.bianim.kataghost.engine.PASS
        val confirm = ui.activity.getString(R.string.confirm_game_end)
        val settlement = ui.activity.getString(R.string.game_settlement)
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnUiThread {
                check(document.record.play(pass))
                check(document.record.play(pass, automatic = true))
            }
            ui.mainClock.advanceTimeBy(500)
            ui.onNodeWithText(ui.activity.getString(R.string.white_pass_notice)).assertIsDisplayed()
            ui.onNodeWithText(settlement).assertDoesNotExist()
            ui.mainClock.advanceTimeBy(2000)
            ui.onNodeWithText(confirm).assertDoesNotExist()
            ui.onNodeWithText(settlement).assertIsDisplayed()
        } finally {
            ui.mainClock.autoAdvance = true
        }
        ui.onNodeWithText(ui.activity.getString(android.R.string.ok)).performClick()
        ui.onNodeWithText(settlement).assertDoesNotExist()
        ui.onNodeWithTag("board-pass").assertIsNotEnabled()
    }

    @Test fun moreMenuOmitsPassAndAutomaticControls() {
        ui.runOnIdle { document.updatePreferences(document.preferences.copy(autoWhite = true)) }
        ui.onNodeWithTag("board-pass").assertIsDisplayed()
        ui.onNodeWithContentDescription("更多选项").performClick()
        ui.onNodeWithTag("menu-settings").assertIsDisplayed()
        ui.onNodeWithTag("pass-move").assertDoesNotExist()
        ui.onNodeWithTag("automatic-control").assertDoesNotExist()
    }

    @Test fun opponentBudgetPersistsIndependentlyOfAnalysis() {
        openSettings()
        ui.onNodeWithTag("opponent-visits-input").performScrollTo().performTextReplacement("64")
        ui.onNodeWithTag("apply-settings").performClick()
        ui.activityRule.scenario.recreate()
        val saved = AppPreferences.load(ui.activity)
        assertEquals(64, saved.effectiveOpponentSearch.visits)
        assertEquals(500, saved.maxVisits)
    }

    @Test fun customSearchRulesAndKomiPersistAndDriveActualAnalysis() {
        openSettings()
        ui.onNodeWithTag("search-limit-input").performTextReplacement("0")
        ui.onNodeWithTag("apply-settings").assertIsNotEnabled()
        ui.onNodeWithTag("search-limit-input").performTextReplacement("333")
        assertEquals(500, document.preferences.maxVisits) // Entire draft is still pending.
        ui.onNodeWithTag("rule-japanese").performScrollTo().performClick()
        ui.onNodeWithTag("komi-input").performTextReplacement("-2.25")
        ui.onNodeWithTag("apply-settings").performClick()
        ui.waitUntil(5000) { !document.busy && document.record.rules == GoRules.JAPANESE }
        assertEquals(-2.25f, document.record.komi, 0f)
        ui.activityRule.scenario.recreate()
        assertEquals(333, AppPreferences.load(ui.activity).maxVisits)
        assertEquals(GoRules.JAPANESE, AppPreferences.load(ui.activity).rules)
        assertEquals(-2.25f, AppPreferences.load(ui.activity).komi, 0f)
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithTag("record-description").assertTextContains("日本 · 贴 -2.25", substring = true)
        setEngine(true)
        waitForReady()
        assertEquals(333, engine.controller.state.value.completedAnalysis?.visits)
        assertEquals(-2.25f, engine.controller.state.value.completedAnalysis!!.position.komi, 0f)
        assertEquals(GoRules.JAPANESE, engine.controller.state.value.completedAnalysis!!.position.rules)
    }

    @Test fun landscapeUsesAvailableHeightAndRetainsTheGame() {
        play("Q16")
        val original = document.record.position
        ui.runOnUiThread { ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(10000) {
            runCatching { ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                ui.onRoot().fetchSemanticsNode().boundsInRoot.width > ui.onRoot().fetchSemanticsNode().boundsInRoot.height }.getOrDefault(false)
        }
        ui.waitForIdle()
        val root = ui.onRoot().fetchSemanticsNode().boundsInRoot
        val board = ui.onNodeWithTag("go-board").fetchSemanticsNode().boundsInRoot
        assertEquals(board.width, board.height, 1f)
        assertTrue("Landscape board should occupy at least 80% of screen height: $board / $root", board.height > root.height * .8f)
        assertTrue(ui.onNodeWithTag("engine-switch").fetchSemanticsNode().boundsInRoot.left > board.right)
        ui.onNodeWithTag("tab-tree").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("上一手").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("下一手").performClick()
        assertEquals(original, document.record.position)
        ui.activityRule.scenario.recreate()
        assertEquals(original, document.record.position)
    }

    @Test fun eitherColorCanMoveAutomaticallyWithOneVisitAndHistoryPausesIt() {
        ui.runOnIdle { document.updatePreferences(document.preferences.copy(maxVisits = 1, autoBlack = true, engineEnabled = true)) }
        waitForMoves(1)
        assertEquals(listOf(1), document.record.position.moveColors)
        playLegal() // Manual white, then automatic black.
        waitForMoves(3)
        assertEquals(listOf(1, 2, 1), document.record.position.moveColors)
        setEngine(false)
        playLegal()
        assertEquals(4, count())
        ui.mainClock.advanceTimeBy(1000)
        assertEquals(4, count())
        setEngine(true)
        waitForMoves(5)
        ui.onNodeWithContentDescription("上一手").performClick()
        waitForReady()
        ui.mainClock.advanceTimeBy(1000)
        assertEquals(4, count())
        assertFalse(document.record.autoPlayArmed)
        ui.runOnIdle { document.updatePreferences(document.preferences.copy(autoBlack = false, autoWhite = true)) }
        playLegal() // Manual black, then automatic white.
        waitForMoves(6)
        assertEquals(2, document.record.position.moveColors.last())
        assertEquals(6, count())
    }

    @Test fun pausedAutomaticTurnHasDirectResumeAndReenablesTheEngine() {
        ui.runOnIdle {
            document.updatePreferences(document.preferences.copy(maxVisits = 1, autoBlack = true, engineEnabled = false))
            document.record.pauseAutomatic()
        }
        ui.onNodeWithTag("turn-label", useUnmergedTree = true).assertTextEquals("自动已暂停")
        ui.onNodeWithTag("resume-automatic").assertIsDisplayed().assertIsEnabled().performClick()
        waitForMoves(1)
        assertTrue(document.preferences.engineEnabled)
        assertTrue(document.record.autoPlayArmed)
        assertEquals(listOf(1), document.record.position.moveColors)
        ui.onNodeWithTag("resume-automatic").assertDoesNotExist() // White is manual.

        ui.onNodeWithContentDescription("上一手").performClick()
        assertFalse(document.record.autoPlayArmed)
        ui.onNodeWithTag("resume-automatic").assertIsDisplayed().performClick()
        waitForMoves(1)
        assertEquals(listOf(1), document.record.position.moveColors)
    }

    @Test fun resumeActionSurvivesRecreationAndLandscapeButStaysHiddenDuringPreviewAndAfterGameOver() {
        ui.runOnIdle {
            document.updatePreferences(document.preferences.copy(maxVisits = 1, autoBlack = true, autoWhite = true, engineEnabled = false))
            document.record.pauseAutomatic()
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("resume-automatic").assertIsDisplayed()
        ui.runOnUiThread { ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(10000) { ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        ui.onNodeWithTag("resume-automatic").assertIsDisplayed().assertIsEnabled()
        ui.runOnIdle {
            document.record.startPreview(io.github.bianim.kataghost.ui.record.Candidate("A", listOf(60)))
        }
        ui.onNodeWithTag("resume-automatic").assertDoesNotExist()
        ui.runOnIdle { document.record.exitPreview() }
        ui.onNodeWithTag("resume-automatic").assertIsDisplayed()
        ui.runOnIdle {
            assertTrue(document.record.play(io.github.bianim.kataghost.engine.PASS))
            assertTrue(document.record.play(io.github.bianim.kataghost.engine.PASS))
        }
        ui.onNodeWithTag("resume-automatic").assertDoesNotExist()
        ui.onNodeWithTag("turn-label").assertTextEquals("棋局结束")
    }

    @Test fun selfPlayStopsForSettingsBackgroundAndEngineOff() {
        ui.runOnIdle { document.updatePreferences(document.preferences.copy(maxVisits = 32, autoBlack = true, autoWhite = true, engineEnabled = true)) }
        waitForMoves(4)
        openSettings()
        ui.waitUntil(5000) { engine.controller.state.value.phase == EnginePhase.SUSPENDED }
        val paused = count()
        ui.mainClock.advanceTimeBy(1000)
        assertEquals(paused, count())
        ui.onNodeWithContentDescription("返回").performClick()
        waitForMoves(paused + 1)
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val backgroundCount = document.record.position.moves.size
        assertEquals(EnginePhase.SUSPENDED, engine.controller.state.value.phase)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForMoves(backgroundCount + 1)
        setEngine(false)
        val offCount = count()
        ui.mainClock.advanceTimeBy(1000)
        assertEquals(offCount, count())
        assertEquals(EnginePhase.OFF, engine.controller.state.value.phase)
        ui.onNodeWithContentDescription("上一手").performClick()
        assertFalse(document.record.autoPlayArmed)
        ui.onNodeWithContentDescription("更多选项").performClick()
        ui.onNodeWithTag("automatic-control").assertDoesNotExist()
        ui.onNodeWithTag("pass-move").assertDoesNotExist()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.onNodeWithTag("resume-automatic").performClick()
        waitForMoves(offCount + 1)
        setEngine(false)
    }

    @Test fun fileOperationsKeepBranchesAndFailedImportKeepsTheCurrentDocument() {
        val directory = ui.activity.cacheDir
        val source = File(directory, "roundtrip-input.sgf")
        val output = File(directory, "roundtrip-output.sgf")
        val bad = File(directory, "bad-input.sgf")
        try {
            source.writeText("(;CA[UTF-8]SZ[19]RU[Japanese]KM[0]GN[文件测试]C[保留注释];B[dd](;W[pq])(;W[qp]))")
            ui.runOnIdle { document.importSgf(Uri.fromFile(source)) }
            ui.waitUntil(5000) { !document.busy && document.record.recordName == "文件测试" }
            assertFalse(document.record.autoPlayArmed)
            assertEquals(2, count())
            val before = document.record.exportSgf()
            bad.writeText("(;SZ[19];B[dd];W[dd])")
            ui.runOnIdle { document.importSgf(Uri.fromFile(bad)) }
            ui.waitUntil(5000) { !document.busy }
            assertEquals(before, document.record.exportSgf())
            ui.runOnIdle { document.prepareExport(); document.exportSgf(Uri.fromFile(output)) }
            ui.waitUntil(5000) { !document.busy && output.exists() }
            assertEquals(before, output.readText())
            ui.activityRule.scenario.recreate()
            assertEquals(before, document.record.exportSgf())
            source.writeText("(;SZ[19]GN[一局])(;SZ[19]GN[二局];W[qq])")
            ui.runOnIdle { document.importSgf(Uri.fromFile(source)) }
            ui.waitUntil(5000) { document.importChoices.size == 2 }
            assertEquals(before, document.record.exportSgf())
            ui.onNodeWithText("2. 二局").performClick()
            ui.waitUntil(5000) { !document.busy && document.record.recordName == "二局" }
            assertEquals(listOf(2), document.record.position.moveColors)
        } finally { source.delete(); output.delete(); bad.delete() }
    }
}
