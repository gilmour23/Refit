package com.refit.app.ui.result

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
import com.refit.app.data.model.RehabSessionResult
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitCardShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import com.refit.app.ui.theme.ReFitSuccessSoft

@Composable
fun ResultScreen(
    result: RehabSessionResult,
    onDone: () -> Unit
) {
    val rmsComparison = result.previousRms
        ?.takeIf { it != 0f }
        ?.let { previous ->
            ((result.rms - previous) / previous) * 100f
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "재활 결과",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "손목 폄 운동",
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "세션이 저장되었습니다.",
            style = MaterialTheme.typography.titleMedium,
            color = ReFitSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ReFitSuccessSoft,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "측정값",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                ResultMetricRow(
                    label = "운동 시간",
                    value = formatTime(result.durationSeconds)
                )

                HorizontalDivider(
                    color = ReFitBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                ResultMetricRow(
                    label = "RMS",
                    value = "%.4f".format(result.rms)
                )

                ResultMetricRow(
                    label = "MAV",
                    value = "%.4f".format(result.mav),
                    modifier = Modifier.padding(top = 12.dp)
                )

                ResultMetricRow(
                    label = "최대 근활성",
                    value = "%.4f".format(result.maxActivation),
                    modifier = Modifier.padding(top = 12.dp)
                )

                ResultMetricRow(
                    label = "수집 샘플",
                    value = result.sampleCount.toString(),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "이전 기록과 비교",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                    text = "RMS 변화",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )

                Text(
                    text = when {
                        result.previousRms == null -> "첫 기록"
                        rmsComparison == null -> "비교 불가"
                        else -> "%+.1f%%".format(rmsComparison)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (rmsComparison != null) ReFitPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (result.previousRms != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "이전",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReFitSubtext
                            )
                            Text(
                                text = "%.4f".format(result.previousRms),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column {
                            Text(
                                text = "현재",
                                style = MaterialTheme.typography.bodySmall,
                                color = ReFitSubtext
                            )
                            Text(
                                text = "%.4f".format(result.rms),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "현재 MVP는 mock sEMG 상대값을 사용합니다. 측정값은 회복 정도를 자동 판정하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 14.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = ReFitButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "확인",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ResultMetricRow(
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
