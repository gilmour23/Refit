package com.refit.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.refit.app.data.local.RehabSessionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    sessions: List<RehabSessionEntity>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "재활 기록",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.padding(top = 12.dp))

        if (sessions.isEmpty()) {
            Text(
                text = "저장된 재활 기록이 없습니다.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = sessions,
                    key = { it.id }
                ) { session ->
                    SessionHistoryCard(session = session)
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("돌아가기")
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: RehabSessionEntity
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = session.exerciseName,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = formatDateTime(session.createdAt),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricText(
                    label = "RMS",
                    value = "%.4f".format(session.rms)
                )
                MetricText(
                    label = "MAV",
                    value = "%.4f".format(session.mav)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricText(
                    label = "Peak",
                    value = "%.4f".format(session.peakActivation)
                )
                MetricText(
                    label = "시간",
                    value = formatDuration(session.durationSeconds)
                )
            }

            Text(
                text = when (session.syncStatus.name) {
                    "SYNCED" -> "클라우드 동기화 완료"
                    "FAILED" -> "동기화 재시도 필요"
                    else -> "로컬 저장됨"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MetricText(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    return Instant
        .ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
