package com.refit.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.components.*
import com.refit.app.ui.session.wristLabel

@Composable
fun HomeScreen(email: String?, sessionRefreshPending: Boolean = false,
               sessions: List<RehabSessionEntity> = emptyList(), wristSide: String? = null, guideDescription: String = "시도 4초 · 휴식 6초 · 3회",
               onConfigure: () -> Unit = {}, onStartExercise: () -> Unit, onOpenHistory: () -> Unit, onSignOut: () -> Unit) {
    var showAccount by remember { mutableStateOf(false) }
    Page("오늘의 운동", topAction = { TextButton({ showAccount = true }) { Text("계정") } },
        footer = { BottomTabs("home", {}, onOpenHistory) }) {
        DemoNotice()
        if (sessionRefreshPending) Hint("계정 연결을 복구 중입니다. 기록은 이 기기에 저장됩니다.")
        Panel {
            Text(wristSide?.let(::wristLabel) ?: "손목 운동", style = MaterialTheme.typography.titleLarge)
            Text("손목 펴기 시도")
            HorizontalDivider()
            Text(guideDescription, fontWeight = FontWeight.SemiBold)
            TextButton(onConfigure) { Text("안내 설정") }
            PrimaryAction("운동 준비하기", onStartExercise)
        }
        Text("최근 운동", style = MaterialTheme.typography.titleMedium)
        val latest = sessions.firstOrNull()
        Panel {
            if (latest == null) {
                Text("아직 운동 기록이 없어요", fontWeight = FontWeight.SemiBold)
            } else {
                Text(formatDate(latest.createdAt), fontWeight = FontWeight.SemiBold)
                LabelValue(latest.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", formatTime(latest.durationSeconds))
                latest.pain?.let { Hint("통증 · $it") }
                latest.fatigue?.let { Hint("피로 · $it") }
                TextButton(onOpenHistory) { Text("운동 기록 보기 ›") }
            }
        }
    }
    if (showAccount) AlertDialog(onDismissRequest = { showAccount = false }, title = { Text("내 계정") },
        text = { Text(email ?: "로그인한 계정") },
        confirmButton = { TextButton(onSignOut) { Text("로그아웃") } },
        dismissButton = { TextButton({ showAccount = false }) { Text("닫기") } })
}
