package com.refit.app.ui.session

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import com.refit.app.ui.theme.ReFitSuccessSoft

@Composable
fun LiveSessionScreen(
    state: SessionUiState,
    onStop: () -> Unit
) {
    var showSignal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "손목 폄 운동",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "재활 세션 진행 중",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReFitSubtext,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = formatLiveTime(state.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedWristExerciseCard(compact = true)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "애니메이션을 따라 손목을 천천히 움직이세요.",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "목표 10회 × 5세트",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 5.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ReFitSuccessSoft
                ),
                border = BorderStroke(1.dp, ReFitBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "sEMG 센서",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReFitSubtext
                            )

                            Text(
                                text = if (state.connected) "●  연결됨" else "연결 종료",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.connected) ReFitSuccess else ReFitSubtext,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "현재 상대 근활성",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReFitSubtext
                            )

                            Text(
                                text = "%.3f".format(state.currentActivation),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { showSignal = !showSignal },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (showSignal) "실시간 신호 닫기" else "실시간 신호 보기",
                            color = ReFitPrimary
                        )
                    }

                    if (showSignal) {
                        Text(
                            text = "Mock sEMG · 단위 미정",
                            style = MaterialTheme.typography.bodySmall,
                            color = ReFitSubtext,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            EmgGraph(
                                samples = state.samples,
                                graphHeight = 82.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "운동은 화면의 동작 안내를 기준으로 진행하고, 센서 수치는 보조 기록으로 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (state.isSaving) {
            Text(
                text = "세션 저장 중...",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            )
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(54.dp),
                shape = ReFitButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = ReFitDanger),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "운동 종료",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatLiveTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
