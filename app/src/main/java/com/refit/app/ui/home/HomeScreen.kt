package com.refit.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    email: String?,
    onStartExercise: () -> Unit,
    onOpenHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Re:Fit",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = email ?: "로그인 사용자",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "오늘의 재활",
            style = MaterialTheme.typography.headlineSmall
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartExercise
        ) {
            Text("재활 운동 시작")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenHistory
        ) {
            Text("이전 기록")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSignOut
        ) {
            Text("로그아웃")
        }
    }
}
