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
import com.refit.app.ui.theme.ReFitDanger
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess
import com.refit.app.ui.theme.ReFitSuccessSoft

@Composable
fun LiveSessionScreen(
    state: SessionUiState,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "손목 폄 운동",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "2 / 5세트",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                    text = "동작 안내",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )

                Text(
                    text = "손목을 위로 올리고 2초간 유지한 뒤 내려주세요.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "6 / 10",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "반복",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ReFitSuccessSoft,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "센서",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitSubtext
                )

                Text(
                    text = if (state.connected) "● 연결됨" else "연결 종료",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSuccess,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column {
                Text(
                    text = "상대 근활성",
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

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "실시간 EMG",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Mock 데이터 · 단위 미정",
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, ReFitBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            EmgGraph(
                samples = state.samples,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "운동 시간",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext
            )

            Text(
                text = formatLiveTime(state.elapsedSeconds),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.isSaving) {
            Text(
                text = "세션 저장 중...",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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
