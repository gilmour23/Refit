package com.refit.app.ui.fitting

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.refit.app.ui.components.ReFitTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FittingDemoTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sampleFlowRequiresASideAndPhotosOrAnExplicitExampleChoice() {
        var exits = 0
        compose.setContent { ReFitTheme { FittingDemoScreen { exits++ } } }
        compose.onNodeWithText("사진 준비하기").assertIsNotEnabled()
        compose.onNodeWithText("왼쪽 손목").performScrollTo().performClick()
        compose.onNodeWithText("사진 준비하기").performClick()
        compose.onNodeWithText("손목 위치 확인하기").assertIsNotEnabled()
        compose.onNodeWithText("사진 없이 예시로 체험").performScrollTo().performClick()
        compose.onNodeWithText("손목 위치 확인하기").performClick()
        capture("21-fitting-guide")
        compose.onNodeWithText("옆 사진").performScrollTo().performClick()
        compose.onNodeWithContentDescription("옆 사진 손목 기준선").performTouchInput { click(center) }
        compose.onNodeWithText("예시 치수 보기").performClick()
        compose.onNodeWithText("54 mm").assertExists()
        capture("22-fitting-dimensions")
        compose.onNodeWithText("임시 모델 미리보기").performClick()
        compose.onNodeWithText("데모 · 예시 치수 / 임시 모델").assertExists()
        compose.onNodeWithContentDescription("예시 손목 커프 3D 미리보기, 드래그하여 회전").performScrollTo().performTouchInput { swipeLeft() }
        capture("23-fitting-preview")
        compose.onNodeWithText("데모 STL 내보내기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("홈으로 돌아가기").performClick()
        assertEquals(1, exits)
    }

    @Test fun backKeepsTheChosenSideAndExampleSelection() {
        compose.setContent { ReFitTheme { FittingDemoScreen {} } }
        compose.onNodeWithText("오른쪽 손목").performScrollTo().performClick()
        compose.onNodeWithText("사진 준비하기").performClick()
        compose.onNodeWithText("사진 없이 예시로 체험").performScrollTo().performClick()
        compose.onNodeWithText("손목 위치 확인하기").performClick()
        compose.onNodeWithText("‹ 뒤로").performClick()
        compose.onNodeWithText("손목 위치 확인하기").assertIsEnabled()
        compose.onNodeWithText("‹ 뒤로").performClick()
        compose.onNodeWithText("✓ 오른쪽 손목").assertExists()
    }

    private fun capture(name: String) {
        System.setProperty("roborazzi.test.record", "true")
        val dir = File("build/outputs/patient-screens").also { it.mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "$name.png").absolutePath)
    }
}
