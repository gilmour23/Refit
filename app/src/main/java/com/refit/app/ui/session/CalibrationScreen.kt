package com.refit.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.BackNavButton
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimaryDark
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess

@Composable
fun CalibrationScreen(
    state: SessionUiState,
    onBack: () -> Unit,
    onStartCalibration: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        BackNavButton(
            label = "센서 확인",
            onClick = onBack,
            enabled = state.calibrationPhase == CalibrationPhase.IDLE ||
                state.calibrationPhase == CalibrationPhase.COMPLETE
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "오늘의 기준값을\n짧게 맞춥니다.",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "운동 전 짧은 기준 측정을 통해 이번 세션의 sEMG 신호를 같은 조건에서 기록합니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(34.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ReFitPrimarySoft,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(20.dp)
        ) {
            Text(
                text = phaseEyebrow(state.calibrationPhase),
                style = MaterialTheme.typography.bodySmall,
                color = ReFitPrimaryDark
            )

            Text(
                text = phaseTitle(state.calibrationPhase),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                text = phaseDescription(state.calibrationPhase),
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (
                state.calibrationPhase == CalibrationPhase.REST ||
                state.calibrationPhase == CalibrationPhase.CONTRACT
            ) {
                Spacer(modifier = Modifier.height(22.dp))

                LinearProgressIndicator(
                    progress = { state.calibrationProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ReFitPrimary,
                    trackColor = ReFitPrimary.copy(alpha = 0.14f)
                )

                Text(
                    text = ((state.calibrationProgress * 3f)
                        .coerceIn(0f, 3f))
                        .let { "%.1f / 3.0초".format(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.calibrationPhase == CalibrationPhase.COMPLETE) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "✓ 기준 설정 완료",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSuccess,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "휴식 기준  " + formatBaseline(state.calibrationRest),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Text(
                    text = "가벼운 수축 기준  " + formatBaseline(state.calibrationActive),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (state.sourceMode.isMock) {
                "현재 MVP의 기준값은 데모 sEMG 신호에서 계산되며 임상적 정상 범위를 판정하지 않습니다."
            } else {
                "기준값은 이번 세션 내 비교를 위한 값이며 임상적 정상 범위를 판정하지 않습니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitDanger,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        when (state.calibrationPhase) {
            CalibrationPhase.IDLE -> {
                Button(
                    onClick = onStartCalibration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(54.dp),
                    shape = ReFitButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "기준 측정 시작",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            CalibrationPhase.COMPLETE -> {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(54.dp),
                    shape = ReFitButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "운동 확인으로 이동",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            CalibrationPhase.REST,
            CalibrationPhase.CONTRACT -> {
                Spacer(modifier = Modifier.height(78.dp))
            }
        }
    }
}

private fun phaseEyebrow(phase: CalibrationPhase): String {
    return when (phase) {
        CalibrationPhase.IDLE -> "BASELINE"
        CalibrationPhase.REST -> "1 / 2"
        CalibrationPhase.CONTRACT -> "2 / 2"
        CalibrationPhase.COMPLETE -> "READY"
    }
}

private fun phaseTitle(phase: CalibrationPhase): String {
    return when (phase) {
        CalibrationPhase.IDLE -> "약 6초면 끝나요."
        CalibrationPhase.REST -> "힘을 빼고 편안하게"
        CalibrationPhase.CONTRACT -> "가볍게 힘을 주세요"
        CalibrationPhase.COMPLETE -> "이제 운동을 시작할 수 있어요."
    }
}

private fun phaseDescription(phase: CalibrationPhase): String {
    return when (phase) {
        CalibrationPhase.IDLE -> "3초 휴식 상태와 3초 가벼운 수축 상태를 순서대로 측정합니다."
        CalibrationPhase.REST -> "손목과 손가락에 힘을 빼고 그대로 유지하세요."
        CalibrationPhase.CONTRACT -> "통증이 없는 범위에서 손목에 가볍게 힘을 주세요."
        CalibrationPhase.COMPLETE -> "이번 세션의 센서 기록에 이 기준값을 함께 저장합니다."
    }
}

private fun formatBaseline(value: Float?): String =
    value?.let { "%.4f".format(it) } ?: "-"
