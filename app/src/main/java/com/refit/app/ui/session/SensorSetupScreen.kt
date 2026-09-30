package com.refit.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.BackNavButton
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitButtonShape
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimaryDark
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext
import com.refit.app.ui.theme.ReFitSuccess

@Composable
fun SensorSetupScreen(
    orthosisProfile: String,
    sourceMode: com.refit.app.data.source.EmgSourceMode,
    onBack: () -> Unit,
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
                label = "홈",
                onClick = onBack
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "보조기와 센서 위치를\n먼저 확인할게요.",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "보조기 착용 상태와 sEMG 센서 부착 위치 예시를 확인한 뒤 데모를 진행합니다."
                style = MaterialTheme.typography.bodyLarge,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "01  보조기",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = orthosisProfile,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                text = "손목이 과하게 눌리지 않고, 전완과 손목이 안정적으로 고정됐는지 확인하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 5.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ReFitSuccess, CircleShape)
                )
                Text(
                    text = "현재 재활 프로그램에 사용할 보조기입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSuccess,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = ReFitBorder)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "02  sEMG 센서 위치 예시",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "전완부 부착 위치 예시",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                text = "센서가 피부에 밀착되도록 부착하고, 보조기 가장자리와 직접 겹치지 않게 두세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReFitSubtext,
                modifier = Modifier.padding(top = 5.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            SensorPlacementVisual()

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = ReFitPrimarySoft,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = if (sourceMode.isMock) "데모 모드" else "BLE 센서 모드",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReFitPrimaryDark
                )
                Text(
                    text = if (sourceMode.isMock) {
                        "현재 빌드는 실제 센서와 연결되지 않습니다. 아래 단계에서는 생성된 예시 sEMG 데이터를 사용합니다."
                    } else {
                        "이번 세션은 실제 BLE sEMG 센서 신호를 사용합니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReFitSubtext,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

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
                text = "확인 완료"
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SensorPlacementVisual() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(
                color = ReFitPrimarySoft,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val skin = Color(0xFFC9D8D1)
            val sensor = ReFitPrimaryDark

            drawRoundRect(
                color = skin,
                topLeft = Offset(w * 0.10f, h * 0.42f),
                size = Size(w * 0.72f, h * 0.20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.10f)
            )

            drawRoundRect(
                color = sensor,
                topLeft = Offset(w * 0.42f, h * 0.34f),
                size = Size(w * 0.16f, h * 0.36f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f)
            )

            drawCircle(
                color = Color.White,
                radius = 7f,
                center = Offset(w * 0.47f, h * 0.52f)
            )

            drawCircle(
                color = Color.White,
                radius = 7f,
                center = Offset(w * 0.53f, h * 0.52f)
            )
        }

        Text(
            text = "센서",
            style = MaterialTheme.typography.bodySmall,
            color = ReFitPrimaryDark,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        )

        Text(
            text = "전완부에 밀착 · 보조기와 직접 겹치지 않게",
            style = MaterialTheme.typography.bodySmall,
            color = ReFitSubtext,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
