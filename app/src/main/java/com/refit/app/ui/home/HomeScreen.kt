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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.refit.app.ui.theme.ReFitCardShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import com.refit.app.ui.theme.ReFitSuccessSoft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    history: List<RehabSessionEntity>,
    onStartRehab: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Re:Fit",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "김OO님",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "오늘 예정된 재활이 1개 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        TodayRehabCard(onStartRehab = onStartRehab)

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "이번 주 목표",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        WeeklyGoal()

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "최근 활동",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "최근 2개",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
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

        Spacer(modifier = Modifier.weight(1f))

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
            Text("재활 기록 보기")
        }
    }
}

@Composable
private fun TodayRehabCard(
    onStartRehab: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ReFitCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ReFitBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "오늘의 재활",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )

            Text(
                text = "손목 폄 운동",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                text = "약 10분 · 5세트",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "●  센서 연결 준비됨",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSuccess,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .background(
                        color = ReFitSuccessSoft,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )

            Button(
                onClick = onStartRehab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(52.dp),
                shape = ReFitButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "재활 시작",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WeeklyGoal() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ReFitCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, ReFitBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "3 / 5일",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "재활 완료",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReFitSubtext,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = "60%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        .fillMaxWidth(0.6f)
                        .height(8.dp)
                        .background(
                            color = ReFitPrimary,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
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
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = formatDate(session.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext
        )
        Text(
            text = session.exerciseName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RMS %.3f".format(session.rms),
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
            Text(
                text = formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM.dd")
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
