package com.refit.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimaryDark
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSurface
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Re:Fit",
                style = MaterialTheme.typography.headlineSmall,
                color = ReFitPrimaryDark
            )

            Text(
                text = formatToday(),
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = "오늘은 손목 폄 운동을\n진행할 차례예요.",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "약 10분 · 10회 × 5세트",
            style = MaterialTheme.typography.bodyLarge,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ReFitPrimaryDark,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitPrimarySoft
                )

                Text(
                    text = "손목 폄 운동",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ReFitSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Text(
                    text = "손목을 천천히 들어 올리고 2초간 유지합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSurface.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 5.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ReFitSurface, CircleShape)
                    )
                    Text(
                        text = "sEMG 센서 준비됨",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReFitSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Button(
                    onClick = onStartRehab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .height(54.dp),
                    shape = ReFitButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReFitSurface,
                        contentColor = ReFitPrimaryDark
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "운동 시작",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "이번 주",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = completedDays.toString() + "일 완료 · 목표 " + weeklyTarget + "일",
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        WeeklyDots(completedDays = completedDays, target = weeklyTarget)

        Spacer(modifier = Modifier.height(34.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 재활",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "전체 보기",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitPrimary,
                modifier = Modifier.clickable(onClick = onOpenHistory)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                text = "아직 완료한 재활이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            history.take(2).forEachIndexed { index, session ->
                RecentSessionRow(session)
                if (index < history.take(2).lastIndex) {
                    HorizontalDivider(color = ReFitBorder)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun WeeklyDots(
    completedDays: Int,
    target: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(target) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        color = if (index < completedDays) ReFitPrimary else ReFitBorder,
                        shape = RoundedCornerShape(99.dp)
                    )
            )
        }
    }
}

@Composable
private fun RecentSessionRow(
    session: RehabSessionEntity
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = session.exerciseName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = formatDateTime(session.createdAt) + " · RMS " + "%.4f".format(session.rms),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            text = formatDuration(session.durationSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext
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

private fun formatToday(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일"))

private fun formatDateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
