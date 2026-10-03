package com.refit.app.ui.session

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
import com.refit.app.ui.components.BackNavButton
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess

@Composable
fun ExerciseIntroScreen(
    errorMessage: String?,
    onBack: () -> Unit,
    onStart: () -> Unit
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
                label = "예시 기준값",
                onClick = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "준비 3 / 3",
                style = MaterialTheme.typography.bodySmall,
                color = ReFitPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "손목 폄 운동",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "손목을 위로 천천히 움직여 폄 동작을 연습합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedWristExerciseCard()

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlanValue(label = "반복", value = "10회")
                PlanValue(label = "세트", value = "5세트")
                PlanValue(label = "예상 시간", value = "시연 30초+")
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "시작하기 전에",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(14.dp))

            Instruction(
                number = "01",
                text = "팔과 전완을 편안한 위치에 고정하세요."
            )
            Instruction(
                number = "02",
                text = "손목만 움직이도록 하고 통증이 생기면 멈추세요."
            )
            Instruction(
                number = "03",
                text = "애니메이션 속도에 맞춰 천천히 반복하세요."
            )

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "●  예시 sEMG 데이터 준비됨",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSuccess
            )

            Text(
                text = "현재 빌드는 실제 센서 대신 생성된 예시 sEMG 데이터를 사용하며 운동 중 자동 기록됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 5.dp)
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitDanger,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(54.dp),
            shape = ReFitButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "데모 세션 시작",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlanValue(
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
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun Instruction(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 14.dp)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
