package com.refit.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.*

data class ActivationPresentation(val fraction: Float, val label: String, val message: String)

/** The scale and detection threshold below apply only to MockEmgDataSource's arbitrary units. */
fun activationPresentation(state: SessionUiState): ActivationPresentation {
    val label = if (state.dataMode == "demo") "데모 근활성" else "센서 신호"
    fun empty(message: String) = ActivationPresentation(0f, label, message)
    if (!state.running) return empty("측정 전이에요")
    if (state.signalStatus == SignalStatus.INTERRUPTED || !state.connected) return empty("기기 연결을 확인해주세요")
    if (state.paused) return empty("측정을 잠시 멈췄어요")
    if (state.dataMode != "demo") return empty(if (state.signalStatus == SignalStatus.RECEIVING) "표시 기준 설정이 필요해요" else "센서 신호를 기다리고 있어요")
    if (state.phase == GuidePhase.READY) return empty("곧 안내를 시작해요")
    if (state.samples.isEmpty() || !state.currentActivation.isFinite() || state.currentActivation < 0f)
        return empty("모의 신호를 기다리고 있어요")
    return ActivationPresentation((state.currentActivation / .65f).coerceIn(0f, 1f), label,
        if (state.phase == GuidePhase.REST) "편하게 힘을 빼주세요"
        else if (state.currentActivation >= .12f) "힘을 주려는 신호가 감지됐어요" else "편하게 시도해보세요")
}

@Composable
fun ActivationFeedback(state: SessionUiState, compact: Boolean = false) {
    val feedback = activationPresentation(state)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(feedback.label, color = Muted, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .semantics { contentDescription = feedback.label; progressBarRangeInfo = ProgressBarRangeInfo(feedback.fraction, 0f..1f) }) {
            if (feedback.fraction > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(feedback.fraction).background(Accent, RoundedCornerShape(6.dp)))
        }
        if (!compact) Text(feedback.message, color = Accent, style = MaterialTheme.typography.bodyMedium)
    }
}
