package com.refit.app.ui.session

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess

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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "손목 폄 운동",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "재활 세션",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReFitSubtext,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = formatLiveTime(state.elapsedSeconds),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedWristExerciseCard(compact = true)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "애니메이션 속도에 맞춰 움직이세요.",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "목표 10회 × 5세트",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "sEMG",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReFitSubtext
                    )
                    Text(
                        text = if (state.connected) {
                            if (state.sourceMode.isMock) "●  예시 데이터 수신 중" else "●  BLE 센서 신호 수신 중"
                        } else {
                            "신호 수신 종료"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.connected) ReFitSuccess else ReFitSubtext,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "현재 sEMG 신호값",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReFitSubtext
                    )
                    Text(
                        text = "%.3f".format(state.currentSignalValue),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ReFitPrimarySoft,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (state.sourceMode.isMock) {
                        "예시 sEMG 기록 중 · 현재 파형은 사용자 움직임과 연동되지 않은 시연용 데이터입니다."
                    } else {
                        "sEMG 센서 기록 중"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ReFitPrimary
                )

                if (state.calibrationRest != null && state.calibrationActive != null) {
                    Text(
                        text = if (state.sourceMode.isMock) "앞서 생성한 예시 기준값을 적용해 기록 중" else "오늘 측정한 기준값을 적용해 기록 중",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReFitSubtext,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Text(
                text = if (showSignal) "신호 접기" else "실시간 신호 보기",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitPrimary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { showSignal = !showSignal }
            )

            if (showSignal) {
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = ReFitPrimarySoft,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (state.sourceMode.isMock) "예시 sEMG 파형 · 시연용 무단위 값" else "sEMG · 센서 원시값",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReFitSubtext
                    )

                    EmgGraph(
                        samples = state.samples,
                        graphHeight = 88.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReFitDanger
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "세션 끝내기",
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
