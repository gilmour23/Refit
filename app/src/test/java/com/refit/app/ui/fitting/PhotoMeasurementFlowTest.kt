package com.refit.app.ui.fitting

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.vision.TestHttpServer
import com.refit.app.security.SecurityTestApplication
import com.refit.app.ui.components.ReFitTheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class)
@Config(application = SecurityTestApplication::class, sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMeasurementFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun response(withReport: Boolean): String {
        val obj = Json.parseToJsonElement(javaClass.getResource("/vision/measurement.json")!!.readText()).jsonObject
        return JsonObject(obj.toMutableMap().apply {
            if (withReport) {
                val bytes = ByteArrayOutputStream()
                Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
                put("report_jpg_base64", JsonPrimitive(Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)))
            }
        }).toString()
    }

    @Test fun automaticPhotosShowElevenDimensionsAndRegisterWithoutManualPoints() {
        val server = TestHttpServer(200, response(true))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("refit_vision_connection", 0).edit().putString("server", "http://127.0.0.1:${server.port}").commit()
        val file = File(app.cacheDir, "top-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var saved: String? = null
        try {
            compose.setContent { ReFitTheme { PhotoMeasurementFlow(Uri.fromFile(file).toString(), Uri.fromFile(file).toString(), "left",
                onRegister = { side, json -> assertEquals("left", side); saved = json; true }, onBack = {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("측정 결과").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("11. 검지 기준점 높이의 두께").performScrollTo().assertExists()
            compose.onNodeWithText("25.8 mm").assertExists()
            compose.onNodeWithText("손목 위치 확인").assertDoesNotExist()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("보고서 확대하기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("치수 등록하고 운동으로").assertIsEnabled().performClick()
            compose.waitUntil(10_000) { saved != null }
            val store = BraceSetupStore(app)
            store.registerMeasurement("measurement-owner", "left", saved!!)
            assertEquals("left", store.readDemoSide("measurement-owner"))
            val stored = store.readMeasurement("measurement-owner")!!
            assertFalse(stored.contains("report_jpg_base64"))
            assertTrue(stored.contains("53.8"))
            assertNull(store.readMeasurement("another-owner"))
            val prefs = app.getSharedPreferences(BraceSetupStore.PREFS_NAME, 0)
            assertTrue(prefs.all.values.filterIsInstance<String>().none { it.contains("dims_mm") })
            System.setProperty("roborazzi.test.record", "true")
            val directory = File("build/outputs/patient-screens").also { it.mkdirs() }
            compose.onRoot().captureRoboImage(File(directory, "31-measured-dimensions.png").absolutePath)
        } finally { server.close(); file.delete() }
    }

    @Test fun systemBackIsConsumedWhileRegistrationIsSaving() {
        val server = TestHttpServer(200, response(true))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("refit_vision_connection", 0).edit().putString("server", "http://127.0.0.1:${server.port}").commit()
        val file = File(app.cacheDir, "save-photo.jpg").apply { writeBytes(byteArrayOf(1)) }
        val release = kotlinx.coroutines.CompletableDeferred<Boolean>()
        var parentBack = 0; var childBack = 0
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        try {
            compose.setContent { ReFitTheme {
                dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                androidx.activity.compose.BackHandler { parentBack++ }
                PhotoMeasurementFlow(Uri.fromFile(file).toString(), Uri.fromFile(file).toString(), "left",
                    onRegister = { _, _ -> release.await() }, onBack = { childBack++ })
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("보고서 확대하기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("치수 등록하고 운동으로").performClick()
            compose.onNodeWithText("측정 결과 저장 중…").assertExists()
            compose.runOnIdle { dispatcher!!.onBackPressed() }
            assertEquals(0, parentBack); assertEquals(0, childBack)
            compose.onNodeWithText("측정 결과 저장 중…").assertExists()
            release.complete(false)
        } finally { release.complete(false); server.close(); file.delete() }
    }

    @Test fun missingReportCannotBeRegisteredAsCheckedMeasurement() {
        val server = TestHttpServer(200, response(false))
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("refit_vision_connection", 0).edit().putString("server", "http://127.0.0.1:${server.port}").commit()
        val file = File(app.cacheDir, "other-photo.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            compose.setContent { ReFitTheme { PhotoMeasurementFlow(Uri.fromFile(file).toString(), Uri.fromFile(file).toString(), "right",
                onRegister = { _, _ -> fail("Missing report registered"); false }, onBack = {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("측정 결과").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("치수 등록하고 운동으로").assertIsNotEnabled()
        } finally { server.close(); file.delete() }
    }
}
