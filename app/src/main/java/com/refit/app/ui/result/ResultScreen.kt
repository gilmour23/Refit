package com.refit.app.ui.result

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess

@Composable
fun ResultScreen(
    result: RehabSessionResult,
    onDone: () -> Unit
) {
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
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "오늘 재활을 마쳤어요.",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = if (result.saveSucceeded) {
                    "손목 폄 운동 기록을 저장했습니다."
                } else {
                    "운동 결과를 계산했습니다. 기록 저장 상태를 확인해주세요."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = result.orthosisProfile,
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitPrimary,
                fontWeight = FontWeight.SemiBold
            )

            if (result.calibrationRest != null && result.calibrationActive != null) {
                Text(
                    text = "오늘 기준 측정값과 함께 저장됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryValue(
                    label = "운동 시간",
                    value = formatTime(result.durationSeconds)
                )
                SummaryValue(
                    label = "수집 샘플",
                    value = result.sampleCount.toString()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "sEMG 기록",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = if (result.signalSource == "MOCK") {
                    "운동 중 생성된 데모 sEMG 신호의 요약입니다."
                } else {
                    "운동 중 센서에서 기록된 sEMG 신호의 요약입니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ReFitPrimarySoft,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(18.dp)
            ) {
                MetricRow("RMS", "%.4f".format(result.rms))
                MetricRow(
                    label = "MAV",
                    value = "%.4f".format(result.mav),
                    modifier = Modifier.padding(top = 14.dp)
                )
                MetricRow(
                    label = "최대 sEMG 신호값",
                    value = "%.4f".format(result.maxActivation),
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (result.calibrationRest != null && result.calibrationActive != null) {
                HorizontalDivider(color = ReFitBorder)
                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "오늘 기준값",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = if (result.signalSource == "MOCK") {
                        "운동 전 데모 sEMG 신호에서 측정한 기준값입니다."
                    } else {
                        "운동 전 실제 센서 신호에서 측정한 세션 기준값입니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                MetricRow(
                    label = "휴식 기준",
                    value = "%.4f".format(result.calibrationRest)
                )
                MetricRow(
                    label = "가벼운 수축 기준",
                    value = "%.4f".format(result.calibrationActive),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "이전 세션",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (result.previousRms == null) {
                Text(
                    text = "아직 비교할 이전 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext
                )
            } else {
                MetricRow(
                    label = "이전 RMS",
                    value = "%.4f".format(result.previousRms)
                )
                MetricRow(
                    label = "이번 RMS",
                    value = "%.4f".format(result.rms),
                    modifier = Modifier.padding(top = 10.dp)
                )
                MetricRow(
                    label = "차이",
                    value = "%+.4f".format(result.rms - result.previousRms),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "RMS 등 센서값의 증감은 회복 정도를 직접 의미하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitSubtext
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (result.saveSucceeded) {
                    "✓ 기록 저장 완료"
                } else {
                    "기기 내 기록 저장 실패"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.saveSucceeded) ReFitSuccess else ReFitDanger
            )

            if (!result.saveSucceeded && result.saveErrorMessage != null) {
                Text(
                    text = result.saveErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(54.dp),
            shape = ReFitButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "홈으로 돌아가기",
                fontWeight = FontWeight.SemiBold
            )
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
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
