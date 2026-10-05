package com.refit.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.startup.InitializationProvider
import com.refit.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityLaunchTest {
    // Robolectric does not start the manifest content provider when launching
    // this activity. Run the real provider before the activity, as Android
    // does, including the declared Supabase and settings initializers.
    @get:Rule(order = 0) val startup = object : ExternalResource() {
        override fun before() {
            Robolectric.setupContentProvider(InitializationProvider::class.java)
        }
    }

    // Launch the production activity and its default repository/ViewModel
    // wiring. Separate screen renders do not exercise this startup path.
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun freshLaunchAndActivityRecreationKeepTheSignedOutUserAtLogin() {
        assertLoginIsReachable()
        compose.activityRule.scenario.recreate()
        assertLoginIsReachable()
    }

    private fun assertLoginIsReachable() {
        compose.onNodeWithText("로그인", useUnmergedTree = true)
            .assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("이메일").assertIsDisplayed()
        compose.onNodeWithText("비밀번호").assertIsDisplayed()
        compose.onNodeWithText("운동 준비하기").assertDoesNotExist()
    }
}
