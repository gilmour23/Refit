package com.refit.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.data.model.SyncStatus
import com.refit.app.ui.auth.AuthUiState
import com.refit.app.ui.auth.LoginScreen
import com.refit.app.ui.components.ReFitTheme
import com.refit.app.ui.history.HistoryScreen
import com.refit.app.ui.home.HomeScreen
import com.refit.app.ui.result.ResultScreen
import com.refit.app.ui.session.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PatientScreensTest {
    @get:Rule val compose = createComposeRule()
    private val rows = listOf(RehabSessionEntity(id = 1, userId = "preview", createdAt = Instant.parse("2026-10-04T06:00:00Z").toEpochMilli(),
        exerciseName = "손목 폄 시도 · 데모", durationSeconds = 30, sampleCount = 1500, rms = .2f, mav = .1f, peakActivation = .3f,
        syncStatus = SyncStatus.SYNCED, completedAttempts = 3, plannedAttempts = 3, attemptSeconds = 4, restSeconds = 6,
        endedEarly = false, dataMode = "demo", comfort = "괜찮아요"))
    @Before fun screenshots() { System.setProperty("roborazzi.test.record", "true") }
    private fun capture(name: String) {
        val dir = File("build/outputs/patient-screens").also { it.mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "$name.png").absolutePath)
    }
    @Test fun preparationRequiresAWristChoiceBeforeShowingTheNextAction() {
        var starts = 0
        compose.setContent { ReFitTheme { SessionContent(SessionUiState(), true, {}, { starts++ }, {}, {}, {}, {}) } }
        capture("18-wrist-selection")
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("왼쪽 손목").performClick()
        compose.onNodeWithText("다음").assertIsEnabled().performClick()
        compose.onNodeWithText("팔을 받쳐주세요").assertIsDisplayed()
        assertEquals(0, starts)
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("데모 안내 시작").performClick()
        assertEquals(1, starts)
    }
    @Test fun loginScreen() {
        compose.setContent { ReFitTheme { LoginScreen(AuthUiState(), null, { _, _ -> }, { _, _ -> }) } }
        compose.onNodeWithText("로그인", useUnmergedTree = true).assertIsDisplayed()
        capture("01-login")
    }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun compactHomeKeepsTheStartActionInTheFirstViewport() {
        compose.setContent { ReFitTheme {
            HomeScreen("demo@example.com", sessions = rows, onStartExercise = {}, onOpenHistory = {}, onSignOut = {})
        } }
        assertFullyVisible("운동 준비하기")
        capture("19-compact-home")
    }
    @Test fun homeOpensExercisePreparation() {
        val preparing = mutableStateOf(false)
        compose.setContent { ReFitTheme {
            if (preparing.value) SessionContent(SessionUiState(), true, {}, {}, {}, {}, {}, {})
            else HomeScreen("demo@example.com", sessions = rows, onStartExercise = { preparing.value = true }, onOpenHistory = {}, onSignOut = {})
        } }
        capture("02-home")
        compose.onNodeWithText("운동 준비하기").performClick()
        compose.onNodeWithText("운동 준비").assertIsDisplayed()
        compose.onNodeWithText("왼쪽 손목").performClick()
        compose.onNodeWithText("다음").performClick()
        capture("03-preparation")
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("데모 안내 시작").assertIsDisplayed()
        capture("13-fit-check")
    }
    @Test fun guidanceHasDistinctAttemptRestAndPauseScreens() {
        val state = mutableStateOf(SessionUiState(running = true, connected = true, wristSide = "left", phase = GuidePhase.ATTEMPT, phaseSecondsRemaining = 4, samples = List(100) { kotlin.math.sin(it * .4f) * .2f }))
        compose.setContent { ReFitTheme { SessionContent(state.value, true, {}, {},
            { state.value = state.value.copy(paused = !state.value.paused) }, {}, {}, {}) } }
        capture("04-attempt")
        compose.onNodeWithText("잠시 멈추기").performClick()
        compose.onNodeWithText("이어서 하기").assertIsDisplayed()
        capture("06-paused")
        compose.onNodeWithText("이어서 하기").performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = GuidePhase.REST, completedAttempts = 1, phaseSecondsRemaining = 6, elapsedSeconds = 4) }
        compose.onNodeWithText("편하게 쉬세요").assertIsDisplayed()
        capture("05-rest")
    }
    @Test fun outcomeKeepsSignalMetricsInAnExplicitDetailSection() {
        val result = mutableStateOf(RehabSessionResult(30,1500,.2f,.1f,.3f,savedSessionId = 1,
            completedAttempts = 3, plannedAttempts = 3, dataMode = "demo", endedEarly = false))
        compose.setContent { ReFitTheme { ResultScreen(result.value, {}, { pain, fatigue -> result.value = result.value.copy(pain = pain ?: result.value.pain, fatigue = fatigue ?: result.value.fatigue) }) } }
        compose.onNodeWithText("‹ 뒤로").assertIsDisplayed()
        compose.onNodeWithText("데모 · 모의 신호").assertIsDisplayed()
        compose.onNodeWithText("RMS").assertDoesNotExist()
        capture("07-result")
        compose.onNodeWithText("없어요").performClick()
        compose.onNodeWithText("✓ 없어요").assertIsDisplayed()
        compose.onNodeWithText("측정 상세 보기 +").performScrollTo().performClick()
        compose.onNodeWithText("RMS").assertExists()
        compose.onNodeWithText("수집 샘플").performScrollTo()
        capture("10-signal-detail")
    }
    @Test fun historyOpensTheSelectedSessionInsteadOfShowingRawMetricsInTheList() {
        compose.setContent { ReFitTheme { HistoryScreen(rows, {}, false, null, {}) } }
        compose.onNodeWithText("RMS").assertDoesNotExist()
        capture("08-history")
        compose.onNodeWithText("손목 펴기 시도").performClick()
        compose.onNodeWithText("운동 상세").assertIsDisplayed()
        capture("09-history-detail")
    }
    @Test fun historyGroupsMultipleRecordsWithoutHidingTheirEntryPoints() {
        val sessions = listOf(rows.first().copy(wristSide = "left"),
            rows.first().copy(id = 2, wristSide = "right", createdAt = rows.first().createdAt - 600_000))
        compose.setContent { ReFitTheme { HistoryScreen(sessions, {}, false, null, {}) } }
        compose.onNodeWithText("왼쪽 손목").assertIsDisplayed()
        capture("20-history-list")
        compose.onNodeWithText("오른쪽 손목").assertIsDisplayed().performClick()
        compose.onNodeWithText("운동 상세").assertIsDisplayed()
    }
    @Test fun largerTextStillLeavesPauseAndEndActionsReachable() {
        compose.setContent { ReFitTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f,2f)) {
                SessionContent(SessionUiState(running = true, wristSide = "left", phase = GuidePhase.ATTEMPT, phaseSecondsRemaining = 4), true, {}, {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText("잠시 멈추기").assertIsDisplayed()
        compose.onNodeWithText("운동 마치기").assertIsDisplayed()
        assertFullyVisible("손목을 펴려고\n시도하세요")
        assertFullyVisible("잠시 멈추기")
        assertFullyVisible("운동 마치기")
        capture("11-large-text")
    }
    @Test @Config(qualifiers = "w411dp-h640dp-xhdpi")
    fun shorterScreenWithLargeTextKeepsInstructionAndActionsVisible() {
        compose.setContent { ReFitTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f,2f)) {
                SessionContent(SessionUiState(running = true, wristSide = "left", phase = GuidePhase.ATTEMPT, phaseSecondsRemaining = 4), true, {}, {}, {}, {}, {}, {})
            }
        } }
        assertFullyVisible("손목을 펴려고\n시도하세요")
        assertFullyVisible("잠시 멈추기")
        assertFullyVisible("운동 마치기")
        capture("16-short-large-text")
    }
    @Test fun waitingForASensorSignalDoesNotPromiseAStartCountdown() {
        compose.setContent { ReFitTheme {
            SessionContent(SessionUiState(running = true, connected = true, wristSide = "left", dataMode = "sensor"), true, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("기기 신호를\n기다리고 있어요").assertIsDisplayed()
        compose.onNodeWithText("초 뒤 시작").assertDoesNotExist()
        capture("17-waiting-signal")
    }
    private fun assertFullyVisible(text: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("$text must fit within the viewport", bounds.left >= root.left && bounds.top >= root.top && bounds.right <= root.right && bounds.bottom <= root.bottom)
    }
    @Test fun timingSettingsRejectOutOfRangeValuesAndApplyValidValues() {
        var applied: Triple<Int, Int, Int>? = null
        compose.setContent { ReFitTheme {
            GuideSettingsScreen(GuideTiming(), {}, { a, r, n -> applied = Triple(a, r, n) })
        } }
        capture("12-settings")
        compose.onNodeWithText("시도 시간 · 1~60초").performTextReplacement("0")
        compose.onNodeWithText("적용").assertIsNotEnabled()
        compose.onNodeWithText("시도 시간 · 1~60초").performTextReplacement("5")
        compose.onNodeWithText("적용").performClick()
        assertEquals(Triple(5,6,3), applied)
    }
    @Test fun interruptedSignalLeavesReconnectAndEndReachableWithoutShowingARecoveryScore() {
        var retries = 0; var ends = 0
        compose.setContent { ReFitTheme {
            SessionContent(SessionUiState(running = true, paused = true, wristSide = "right", dataMode = "sensor",
                signalStatus = SignalStatus.INTERRUPTED), false, {}, {}, {}, { ends++ }, {}, {},
                onRetryConnection = { retries++ }, voiceStatus = VoiceStatus.UNAVAILABLE)
        } }
        compose.onNodeWithText("다시 연결").performClick()
        compose.onNodeWithText("운동 마치기").performClick()
        assertEquals(1, retries); assertEquals(1, ends)
        compose.onNodeWithText("안내 다시 듣기").assertIsNotEnabled()
        capture("14-disconnected")
    }
    @Test fun readyShowsTheDirectionBeforeTimedGuidance() {
        compose.setContent { ReFitTheme {
            SessionContent(SessionUiState(running = true, connected = true, wristSide = "left", dataMode = "demo"), true, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("초 뒤 시작").assertIsDisplayed()
        capture("15-ready")
    }
}

