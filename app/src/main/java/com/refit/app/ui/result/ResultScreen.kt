package com.refit.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.refit.app.data.model.RehabSessionResult

@Composable
fun ResultScreen(
    result: RehabSessionResult,
    onDone: () -> Unit
) {
    val rmsComparison = result.previousRms
        ?.takeIf { it != 0f }
        ?.let { previous ->
            ((result.rms - previous) / previous) * 100f
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "재활 완료",
            style = MaterialTheme.typography.headlineLarge
        )

        ResultItem(
            label = "운동 시간",
            value = formatTime(result.durationSeconds)
        )

        ResultItem(
            label = "RMS",
            value = "%.4f".format(result.rms)
        )

        ResultItem(
            label = "MAV",
            value = "%.4f".format(result.mav)
        )

        ResultItem(
            label = "Peak 근활성",
            value = "%.4f".format(result.peakActivation)
        )

        ResultItem(
            label = "수집 샘플",
            value = result.sampleCount.toString()
        )

        ResultItem(
            label = "이전 세션 대비 RMS",
            value = when {
                result.previousRms == null -> "첫 기록"
                rmsComparison == null -> "비교 불가"
                else -> "%+.1f%%".format(rmsComparison)
            }
        )

        Text(
            text = "※ 현재 비교값은 MVP 데모용입니다. 실제 임상 비교에는 전극 위치 통제와 정규화가 필요합니다.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("완료")
        }
    }
}

@Composable
private fun ResultItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

private fun formatTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
