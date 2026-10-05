package com.refit.app.ui.session

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun SessionVoice(instruction: String, enabled: Boolean, repeatToken: Int): VoiceStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(VoiceStatus.LOADING) }
    var initialized by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var active = true
        val engine = TextToSpeech(context) { result ->
            if (active) {
                initialized = result == TextToSpeech.SUCCESS
                if (!initialized) status = VoiceStatus.UNAVAILABLE
            }
        }
        tts = engine
        onDispose { active = false; engine.stop(); engine.shutdown(); tts = null }
    }
    LaunchedEffect(initialized) {
        val engine = tts
        if (initialized && engine != null) {
            val language = engine.setLanguage(Locale.KOREAN)
            status = if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED)
                VoiceStatus.UNAVAILABLE else VoiceStatus.AVAILABLE
        }
    }
    LaunchedEffect(instruction, enabled, repeatToken, status) {
        val engine = tts ?: return@LaunchedEffect
        engine.stop()
        if (enabled && status == VoiceStatus.AVAILABLE && instruction.isNotBlank()) {
            if (engine.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, "patient-guide") == TextToSpeech.ERROR)
                status = VoiceStatus.UNAVAILABLE
        }
    }
    return status
}
