package com.refit.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.refit.app.ui.history.HistoryScreen
import com.refit.app.ui.result.ResultScreen

@Composable
fun SessionScreen(
    viewModel: SessionViewModel
) {
    val state by viewModel.uiState.collectAsState()

    if (state.showHistory) {
        HistoryScreen(
            sessions = state.history,
            onBack = viewModel::closeHistory
        )
        return
    }

    val result = state.result
    if (result != null) {
        ResultScreen(
            result = result,
            onDone = viewModel::resetSession
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Re:Fit",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "손목 폄 운동",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = if (state.connected) {
                "센서 상태: 연결됨"
            } else {
                "센서 상태: 연결 안 됨"
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("현재 근활성")
                Text(
                    text = "%.3f".format(state.currentActivation),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Column {
                Text("운동 시간")
                Text(
                    text = formatTime(state.elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text("실시간 EMG")

        EmgGraph(
            samples = state.samples,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        if (state.isSaving) {
            Text("세션 저장 중...")
        } else if (!state.running) {
            Button(
                onClick = viewModel::startSession,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("재활 시작")
            }

            OutlinedButton(
                onClick = viewModel::openHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("이전 기록")
            }
        } else {
            Button(
                onClick = viewModel::stopSession,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("운동 종료")
            }
        }
    }
}

private fun formatTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
