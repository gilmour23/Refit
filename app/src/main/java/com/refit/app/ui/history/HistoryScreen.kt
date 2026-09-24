package com.refit.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    sessions: List<RehabSessionEntity>,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "‹  홈",
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.clickable(onClick = onBack)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "재활 기록",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "완료한 재활과 센서 측정 기록을 확인합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            SummaryCard(sessions = sessions)

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "RMS 측정 추이",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "같은 운동의 센서 측정값을 세션별로 비교합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            TrendCard(sessions = sessions)

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "세션 기록",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "저장된 재활 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
        } else {
            items(
                items = sessions,
                key = { it.id }
            ) { session ->
                SessionHistoryRow(session = session)
                HorizontalDivider(color = ReFitBorder)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCard(
    sessions: List<RehabSessionEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ReFitBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "완료한 세션",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )
                Text(
                    text = sessions.size.toString() + "회",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Column {
                Text(
                    text = "최근 운동",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )
                Text(
                    text = sessions.firstOrNull()?.let { formatShortDateTime(it.createdAt) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    sessions: List<RehabSessionEntity>
) {
    val recent = sessions.take(7).reversed()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ReFitBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (recent.size < 3) {
                Text(
                    text = "세션이 3회 이상 쌓이면 추이를 표시합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext
                )

                if (recent.isNotEmpty()) {
                    Text(
                        text = "최근 RMS  " + "%.4f".format(recent.last().rms),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                return@Column
            }

            Text(
                text = "%.4f".format(recent.last().rms),
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "최근 RMS 상대값",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            RmsTrendChart(
                values = recent.map { it.rms },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatShortDateTime(recent.first().createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )
                Text(
                    text = formatShortDateTime(recent.last().createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )
            }
        }
    }
}

@Composable
private fun RmsTrendChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(0.01f)

        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) {
                size.width / 2f
            } else {
                size.width * index / (values.size - 1)
            }
            val normalized = (value - min) / range
            val y = size.height - (normalized * (size.height * 0.65f) + size.height * 0.18f)
            Offset(x, y)
        }

        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = ReFitPrimary,
                start = start,
                end = end,
                strokeWidth = 4f
            )
        }

        points.forEach { point ->
            drawCircle(
                color = ReFitPrimary,
                radius = 6f,
                center = point
            )
        }
    }
}

@Composable
private fun SessionHistoryRow(
    session: RehabSessionEntity
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDateTime(session.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )

            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )
        }

        Text(
            text = session.exerciseName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "RMS %.4f".format(session.rms),
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 5.dp)
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

private fun formatShortDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM.dd HH:mm")
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
