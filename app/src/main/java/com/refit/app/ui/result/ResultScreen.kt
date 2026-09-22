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
            label = "최대 근활성",
            value = "%.4f".format(result.maxActivation)
        )

        ResultItem(
            label = "수집 샘플",
            value = result.sampleCount.toString()
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
