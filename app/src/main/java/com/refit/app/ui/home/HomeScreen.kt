package com.refit.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.ui.components.*
import com.refit.app.ui.session.wristLabel
import com.refit.app.ui.session.WristIllustration

@Composable
fun HomeScreen(email: String?, sessionRefreshPending: Boolean = false,
               sessions: List<RehabSessionEntity> = emptyList(), wristSide: String? = null, guideDescription: String = "시도 4초 · 휴식 6초 · 3회",
               onConfigure: () -> Unit = {}, onStartExercise: () -> Unit, onOpenHistory: () -> Unit, onSignOut: (() -> Unit)?) {
    var showAccount by remember { mutableStateOf(false) }
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 720 || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f
    Page("오늘의 손목 운동", topAction = {
        if (onSignOut != null) TextButton({ showAccount = true }) { Text("계정") }
    },
        footer = { BottomTabs("home", {}, onOpenHistory) }) {
        if (onSignOut == null) Hint("로그인 없이 체험 중 · 기록은 이 기기에만 저장됩니다.")
        if (!compact) Hint("차근차근, 오늘도 함께해요.")
        if (sessionRefreshPending) Hint("계정 연결을 복구 중입니다. 기록은 이 기기에 저장됩니다.")
        Panel(color = AccentSurface) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("손목 펴기", style = MaterialTheme.typography.titleLarge, color = Ink)
                DemoNotice()
            }
            WristIllustration(wristSide, attempt = true, modifier = Modifier.height(if (compact) 96.dp else 184.dp))
            Text(guideDescription, style = MaterialTheme.typography.bodyMedium, color = Muted)
            PrimaryAction("운동 준비하기", onStartExercise)
        }
        TextButton(onConfigure, modifier = Modifier.align(Alignment.Start)) { Text("안내 시간 설정") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("최근 운동", style = MaterialTheme.typography.titleMedium)
            TextButton(onOpenHistory) { Text("전체 보기 ›") }
        }
        val latest = sessions.firstOrNull()
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (latest == null) {
                Text("아직 운동 기록이 없어요", fontWeight = FontWeight.SemiBold)
            } else {
                LabelValue(latest.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", formatTime(latest.durationSeconds))
                Hint(formatDate(latest.createdAt))
                if (latest.pain != null || latest.fatigue != null) Hint(listOfNotNull(latest.pain?.let { "통증 · $it" }, latest.fatigue?.let { "피로 · $it" }).joinToString("  /  "))
            }
        }
    }
    if (showAccount && onSignOut != null) AlertDialog(onDismissRequest = { showAccount = false }, title = { Text("내 계정") },
        text = { Text(email ?: "로그인한 계정") },
        confirmButton = { TextButton(onSignOut) { Text("로그아웃") } },
        dismissButton = { TextButton({ showAccount = false }) { Text("닫기") } })
}


