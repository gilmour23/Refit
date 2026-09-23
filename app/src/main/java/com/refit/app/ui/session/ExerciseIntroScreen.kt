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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitCardShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import com.refit.app.ui.theme.ReFitSuccessSoft

@Composable
fun ExerciseIntroScreen(
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "손목 폄 운동",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text(
            text = "손목을 위로 천천히 들어 올리는 운동입니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            shape = ReFitCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, ReFitBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "동작 안내",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "운동 영상 / 이미지 영역",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "실제 동작 영상이 준비되면 이 영역에 표시합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "운동 방법",
            style = MaterialTheme.typography.titleMedium
        )

        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Instruction("1", "팔을 편안하게 고정합니다.")
            Instruction("2", "손목을 천천히 위로 들어 올립니다.")
            Instruction("3", "2초간 유지합니다.")
            Instruction("4", "천천히 원래 위치로 돌아옵니다.")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "10회 × 5세트",
            style = MaterialTheme.typography.titleMedium
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

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = ReFitButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = ReFitPrimary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "운동 시작",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Instruction(
    number: String,
    text: String
) {
    Row {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 10.dp)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
