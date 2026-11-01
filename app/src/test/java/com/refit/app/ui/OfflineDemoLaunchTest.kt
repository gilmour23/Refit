package com.refit.app.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.startup.InitializationProvider
import androidx.test.core.app.ApplicationProvider
import com.refit.app.MainActivity
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.SyncStatus
import com.refit.app.data.remote.SupabaseClientProvider
import com.refit.app.security.SecurityTestApplication
import com.refit.app.ui.fitting.BraceSetupStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = SecurityTestApplication::class, sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineDemoLaunchTest {
    @get:Rule(order = 0) val startup = object : ExternalResource() {
        override fun before() {
            assumeFalse("This integration suite runs with empty backend configuration", SupabaseClientProvider.isConfigured)
            val field = ReFitDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            (field.get(null) as? ReFitDatabase)?.close()
            field.set(null, null)
            ApplicationProvider.getApplicationContext<Application>().deleteDatabase("refit.db")
            ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(BraceSetupStore.PREFS_NAME, 0).edit().clear().commit()
            Robolectric.setupContentProvider(InitializationProvider::class.java)
        }
        override fun after() {
            val field = ReFitDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            (field.get(null) as? ReFitDatabase)?.close()
            field.set(null, null)
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchRequiresBraceSetupAndKeepsItsRegistrationAfterRecreation() {
        awaitText("보조기 제작")
        compose.onNodeWithText("운동 준비하기").assertDoesNotExist()
        compose.onNodeWithText("홈").assertDoesNotExist()
        compose.onNodeWithText("운동 기록").assertDoesNotExist()
        compose.onNodeWithText("로그인", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("이메일").assertDoesNotExist()
        compose.onNodeWithText("계정").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        awaitText("보조기 제작")
        compose.onNodeWithText("운동 준비하기").assertDoesNotExist()
        completeBraceSetup()
        compose.activityRule.scenario.recreate()
        awaitText("운동 준비하기")
        compose.onNodeWithText("운동 준비하기").assertIsEnabled()
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("left", BraceSetupStore(app).readDemoSide("__offline_demo_v1__"))
        assertNull(BraceSetupStore(app).readDemoSide("another-owner"))
    }

    @Test fun offlineHistoryIsSeparateFromAccountRecordsAndHasNoCloudActions() {
        completeBraceSetup()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = ReFitDatabase.getInstance(app).rehabSessionDao()
        runBlocking {
            dao.insert(record("__offline_demo_v1__", "left"))
            dao.insert(record("7c65c147-5f27-4b43-8811-8209954a8a58", "right"))
        }
        compose.onNodeWithText("전체 보기 ›").performScrollTo().performClick()
        awaitText("왼쪽 손목")
        compose.onNodeWithText("오른쪽 손목").assertDoesNotExist()
        compose.onNodeWithText("다시 동기화").assertDoesNotExist()
        compose.onNodeWithText("동기화 대기", substring = true).assertDoesNotExist()
        assertEquals(1, runBlocking { dao.getPendingSync("7c65c147-5f27-4b43-8811-8209954a8a58").size })
    }

    @Test fun aDemoExerciseCanBeSavedAndReopenedWithoutSigningIn() {
        completeBraceSetup()
        compose.onNodeWithText("운동 준비하기").performScrollTo().performClick()
        // Registration already supplies the wrist side; the preparation choice stays editable.
        compose.onNodeWithText("팔을 받쳐주세요").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("데모 안내 시작").performClick()
        awaitText("운동 마치기")
        compose.onNodeWithText("운동 마치기").performClick()
        compose.onNodeWithText("기록하고 마치기").performClick()
        awaitText("오늘의 운동을 기록했어요.")
        compose.onNodeWithText("기록이 저장되지 않았습니다.").assertDoesNotExist()
        compose.onNodeWithText("홈으로").performClick()
        awaitText("운동 준비하기")
        compose.activityRule.scenario.recreate()
        awaitText("운동 준비하기")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rows = runBlocking { ReFitDatabase.getInstance(app).rehabSessionDao().getPendingSync("__offline_demo_v1__") }
        assertEquals(1, rows.size)
        assertEquals("demo", rows.single().dataMode)
        assertEquals("left", rows.single().wristSide)
        assertEquals(SyncStatus.LOCAL_ONLY, rows.single().syncStatus)
        assertNull(rows.single().remoteId)
        compose.onNodeWithText("전체 보기 ›").performScrollTo().performClick()
        awaitText("왼쪽 손목")
        compose.onNodeWithText("다시 동기화").assertDoesNotExist()
    }

    @Test fun offlineHomeOpensAndReturnsFromFittingWithoutLogin() {
        completeBraceSetup()
        compose.onNodeWithText("보조기 제작 체험").assertDoesNotExist()
        compose.onNodeWithText("보조기").assertIsDisplayed().performClick()
        compose.onNodeWithText("보조기 제작").assertIsDisplayed()
        compose.onNodeWithText("로그인", useUnmergedTree = true).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        awaitText("보조기 제작")
        compose.onNodeWithText("운동 기록").assertIsDisplayed().performClick()
        awaitText("이 기기에 저장된 데모 기록입니다.")
        compose.onNodeWithText("보조기").assertIsDisplayed().performClick()
        compose.onNodeWithText("보조기 제작").assertIsDisplayed()
        compose.onNodeWithText("홈").assertIsDisplayed().performClick()
        awaitText("운동 준비하기")
        compose.onNodeWithText("운동 준비하기").assertIsEnabled()
    }

    private fun completeBraceSetup() {
        awaitText("보조기 제작")
        compose.onNodeWithText("왼쪽 손목").performScrollTo().performClick()
        compose.onNodeWithText("사진 준비하기").performClick()
        compose.onNodeWithText("예시 그림으로 진행").performScrollTo().performClick()
        compose.onNodeWithText("손목 위치 확인하기").performClick()
        compose.onNodeWithText("예시 치수 보기").performClick()
        compose.onNodeWithText("임시 모델 미리보기").performClick()
        compose.onNodeWithText("시연용 보조기 등록하고 운동으로").performClick()
        awaitText("운동 준비하기")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun record(owner: String, side: String) = RehabSessionEntity(
        userId = owner, createdAt = 1_799_999_000_000L, exerciseName = "손목 폄 시도 · 데모",
        durationSeconds = 2, sampleCount = 10, rms = .1f, mav = .1f, peakActivation = .2f,
        syncStatus = SyncStatus.LOCAL_ONLY, dataMode = "demo", wristSide = side
    )
}

