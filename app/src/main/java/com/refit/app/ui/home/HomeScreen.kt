package com.refit.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun HomeScreen(
    history: List<RehabSessionEntity>,
    onStartRehab: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val completedDays = completedDaysThisWeek(history)
    val weeklyTarget = 5
    val progress = (completedDays.toFloat() / weeklyTarget).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Re:Fit",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "오늘의 재활",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "손목 움직임을 천천히 확인하며 진행하세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        TodayProgramCard(onStartRehab = onStartRehab)

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "이번 주",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = completedDays.toString() + " / " + weeklyTarget + "일 완료",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = ReFitBorder,
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .background(
                        color = ReFitPrimary,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "최근 재활",
                style = MaterialTheme.typography.titleMedium
            )
            if (history.isNotEmpty()) {
                Text(
                    text = "최근 " + history.take(2).size + "개",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                text = "아직 저장된 재활 기록이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(vertical = 18.dp)
            )
        } else {
            history.take(2).forEachIndexed { index, session ->
                RecentSessionRow(session = session)
                if (index < history.take(2).lastIndex) {
                    HorizontalDivider(color = ReFitBorder)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onOpenHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ReFitButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, ReFitBorder),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("전체 기록 보기")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun TodayProgramCard(
    onStartRehab: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ReFitBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ReFitPrimarySoft)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "오늘의 프로그램",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext
                )
                Text(
                    text = "손목 폄 운동",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    text = "10회 × 5세트 · 약 10분",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "●  sEMG 센서 준비됨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSuccess
                )

                Text(
                    text = "운동 중 센서 신호는 보조 정보로 함께 기록됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 5.dp)
                )

                Button(
                    onClick = onStartRehab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(54.dp),
                    shape = ReFitButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "오늘 재활 시작",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(
    session: RehabSessionEntity
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp)
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
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun completedDaysThisWeek(history: List<RehabSessionEntity>): Int {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    return history
        .map {
            Instant.ofEpochMilli(it.createdAt)
                .atZone(zone)
                .toLocalDate()
        }
        .filter { !it.isBefore(monday) && !it.isAfter(today) }
        .distinct()
        .size
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM.dd HH:mm")
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
