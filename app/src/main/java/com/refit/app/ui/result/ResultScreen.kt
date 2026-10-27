package com.refit.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.ui.components.*
import com.refit.app.ui.session.wristLabel

@Composable
fun ResultScreen(result: RehabSessionResult, onDone: () -> Unit, onSymptoms: (String?, String?) -> Unit = { _, _ -> }, message: String? = null) {
    var details by remember { mutableStateOf(false) }
    Page("운동 마침", onDone, footer = { PrimaryAction("홈으로", onDone) }) {
        if (result.savedSessionId == null) Text("기록이 저장되지 않았습니다.", color = MaterialTheme.colorScheme.error)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result.dataMode == "demo") DemoNotice()
        Text("수고하셨어요", style = MaterialTheme.typography.headlineLarge)
        Hint(if (result.savedSessionId != null) "오늘의 운동을 기록했어요." else "저장 상태를 확인해주세요.")
        Spacer(Modifier.height(8.dp))
        LabelValue("안내 시간", formatTime(result.durationSeconds))
        LabelValue("진행한 안내", "${result.completedAttempts ?: "—"} / ${result.plannedAttempts ?: "—"}회")
        if (result.endedEarly == true) Hint("중간에 마쳤어요.")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("통증이 있었나요?")
        ChoiceRow(listOf("없어요", "조금 아파요", "많이 아파요"), result.pain, { onSymptoms(it, null) }, result.savedSessionId != null)
        Text("얼마나 힘드셨나요?")
        ChoiceRow(listOf("괜찮아요", "조금 힘듦", "많이 힘듦"), result.fatigue, { onSymptoms(null, it) }, result.savedSessionId != null)
        TextButton({ details = !details }) { Text(if (details) "측정 상세 접기 −" else "측정 상세 보기 +") }
        if (details) SignalDetails(result.rms, result.mav, result.peakActivation, result.sampleCount, result.dataMode)
    }
}

@Composable
fun SignalDetails(rms: Float, mav: Float, peak: Float, count: Int, dataMode: String) {
    Panel {
        Text(if (dataMode == "demo") "모의 신호" else "센서 신호", style = MaterialTheme.typography.titleMedium)
        Hint(if (dataMode == "demo") "실제 측정값이 아닙니다." else "움직임·회복률을 뜻하지 않습니다.")
        LabelValue("RMS", if (count == 0) "미수집" else "%.4f".format(rms))
        LabelValue("MAV", if (count == 0) "미수집" else "%.4f".format(mav))
        LabelValue("Peak", if (count == 0) "미수집" else "%.4f".format(peak))
        LabelValue("수집 샘플", count.toString())
    }
}

