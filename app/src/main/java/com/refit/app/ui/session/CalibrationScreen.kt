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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            BackNavButton(
                label = "센서 확인",
                onClick = onBack,
                enabled = state.calibrationPhase == CalibrationPhase.IDLE ||
                    state.calibrationPhase == CalibrationPhase.COMPLETE
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "준비 2 / 3",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "예시 기준값을\n먼저 생성합니다.",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "현재 데모에서는 실제 사용자의 근전도를 측정하지 않고, 휴식·가벼운 수축 예시 데이터를 순서대로 생성합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

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
                        text = "✓ 예시 기준값 생성 완료",
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
                text = "이 기준값은 실제 사용자 측정값이 아닌 시연용 예시 데이터이며 임상적 해석에 사용할 수 없습니다.",
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

            Spacer(modifier = Modifier.height(24.dp))
        }

        when (state.calibrationPhase) {
            CalibrationPhase.IDLE -> {
                Button(
                    onClick = onStartCalibration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp),
                    shape = ReFitButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "예시 기준값 생성",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            CalibrationPhase.COMPLETE -> {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
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
                Text(
                    text = "예시 데이터 생성 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                )
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
        CalibrationPhase.IDLE -> "약 6초 동안 예시 데이터를 생성합니다."
        CalibrationPhase.REST -> "휴식 상태 예시 데이터"
        CalibrationPhase.CONTRACT -> "가벼운 수축 예시 데이터"
        CalibrationPhase.COMPLETE -> "예시 기준값이 준비됐습니다."
    }
}

private fun phaseDescription(phase: CalibrationPhase): String {
    return when (phase) {
        CalibrationPhase.IDLE -> "휴식 상태 3초와 가벼운 수축 상태 3초의 예시 신호를 순서대로 생성합니다."
        CalibrationPhase.REST -> "낮은 진폭의 휴식 상태 예시 신호를 생성하고 있습니다."
        CalibrationPhase.CONTRACT -> "휴식보다 높은 가벼운 수축 상태 예시 신호를 생성하고 있습니다."
        CalibrationPhase.COMPLETE -> "이 값은 데모 세션의 계산과 기록에 함께 사용됩니다."
    }
}

private fun formatBaseline(value: Float?): String =
    value?.let { "%.4f".format(it) } ?: "-"
