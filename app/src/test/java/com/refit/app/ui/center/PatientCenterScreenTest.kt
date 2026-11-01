package com.refit.app.ui.center

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.refit.app.ui.components.ReFitTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PatientCenterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invitationCannotBeAcceptedUntilConsentIsChecked() {
        var accepted = ""
        compose.setContent { ReFitTheme {
            PatientCenterScreen(PatientCenterUiState(), null, true, {}, { code, consent ->
                if (consent) accepted = code
            }, {}, {}, {})
        } }
        compose.onNodeWithText("동의하고 센터 연결").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("치료사에게 받은 초대코드").performScrollTo().performTextInput("opaque-code")
        compose.onNodeWithText("동의하고 센터 연결").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("동의하고 센터 연결").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("opaque-code", accepted) }
        compose.onNodeWithText("동의하고 센터 연결").assertIsNotEnabled()
    }
}
