package com.refit.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.BackNavButton
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
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

            BackNavButton(
                label = "홈",
                onClick = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "재활 기록",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "완료한 세션과 sEMG 측정 기록을 모아봅니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 5.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryValue(
                    label = "완료한 세션",
                    value = sessions.size.toString() + "회"
                )
                SummaryValue(
                    label = "최근 재활",
                    value = sessions.firstOrNull()?.let { formatShortDate(it.createdAt) } ?: "-"
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "RMS 추이",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "같은 운동에서 기록된 센서값을 세션별로 비교합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            TrendSection(sessions)

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "세션",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "아직 저장된 재활 기록이 없습니다.",
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
                SessionHistoryRow(session)
                HorizontalDivider(color = ReFitBorder)
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SummaryValue(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun TrendSection(
    sessions: List<RehabSessionEntity>
) {
    val latestSession = sessions.firstOrNull()
    val latestExercise = latestSession?.exerciseName
    val latestSource = latestSession?.signalSource
    val recent = sessions
        .filter {
            (latestExercise == null || it.exerciseName == latestExercise) &&
                (latestSource == null || it.signalSource == latestSource)
        }
        .take(7)
        .reversed()

    if (recent.size < 3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ReFitPrimarySoft,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = "세션이 3회 이상 쌓이면 추이를 보여드립니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (recent.isNotEmpty()) {
                Text(
                    text = "최근 RMS  " + "%.4f".format(recent.last().rms),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = ReFitPrimarySoft,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "%.4f".format(recent.last().rms),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "최근 RMS",
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        RmsTrendChart(
            values = recent.map { it.rms },
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatShortDate(recent.first().createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )
            Text(
                text = formatShortDate(recent.last().createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )
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
            val x = size.width * index / (values.size - 1)
            val normalized = (value - min) / range
            val y = size.height - (normalized * size.height * 0.64f + size.height * 0.18f)
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
                radius = 5f,
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
            .padding(vertical = 15.dp)
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
            text = session.orthosisProfile,
            style = MaterialTheme.typography.bodySmall,
            color = ReFitPrimary,
            modifier = Modifier.padding(top = 5.dp)
        )

        Text(
            text = signalSourceLabel(session.signalSource) + " · RMS " + "%.4f".format(session.rms),
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp)
        )

        if (session.calibrationRest != null && session.calibrationActive != null) {
            Text(
                text = "기준 측정 포함",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

private fun formatShortDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM.dd"))

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun signalSourceLabel(signalSource: String): String =
    if (signalSource == "MOCK") "예시 데이터" else "BLE 센서"
