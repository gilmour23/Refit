package com.refit.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.SyncStatus
import com.refit.app.ui.components.*
import com.refit.app.ui.session.wristLabel
import com.refit.app.ui.result.SignalDetails

@Composable
fun HistoryScreen(sessions: List<RehabSessionEntity>, onBack: () -> Unit, isSyncing: Boolean,
                  syncMessage: String?, onRetrySync: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        HistoryDetailScreen(selected) { selectedId = null }
        return
    }
    BackHandler(onBack = onBack)
    val pending = sessions.count { it.syncStatus != SyncStatus.SYNCED }
    Page("운동 기록", footer = { BottomTabs("history", onBack, {}) }) {
        if (sessions.isEmpty()) Panel {
            Text("아직 운동 기록이 없어요", style = MaterialTheme.typography.titleMedium)
            PrimaryAction("홈으로 돌아가기", onBack)
        }
        if (pending > 0) Panel {
            Text("동기화 대기 ${pending}개", fontWeight = FontWeight.SemiBold)
            SecondaryAction(if (isSyncing) "동기화 중…" else "다시 동기화", onRetrySync, !isSyncing)
            syncMessage?.let { Hint(it) }
        }
        sessions.groupBy { formatDate(it.createdAt, "M월 d일 EEEE") }.forEach { (date, rows) ->
            Text(date, style = MaterialTheme.typography.titleMedium)
            rows.forEach { session ->
                Panel(Modifier.clickable { selectedId = session.id }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(session.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", fontWeight = FontWeight.SemiBold)
                        Text("›", color = Muted)
                    }
                    Hint("${formatDate(session.createdAt, "HH:mm")} · ${formatTime(session.durationSeconds)}")
                    if (session.completedAttempts != null) LabelValue("진행한 안내", "${session.completedAttempts} / ${session.plannedAttempts}회")
                    if (session.pain != null || session.fatigue != null) {
                        session.pain?.let { Hint("통증 · $it") }
                        session.fatigue?.let { Hint("피로 · $it") }
                    } else session.comfort?.let { Text("운동 후 · $it") }
                    Hint(when (session.dataMode) { "demo" -> "데모 기록"; "sensor" -> "센서 측정 기록"; else -> "이전 기록 · 측정 환경 미확인" })
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(session: RehabSessionEntity, onBack: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    Page("운동 상세", onBack, footer = { SecondaryAction("기록으로 돌아가기", onBack) }) {
        Hint(formatDate(session.createdAt, "yyyy년 M월 d일 · HH:mm"))
        if (session.dataMode == "demo") DemoNotice()
        Panel {
            Text(session.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", style = MaterialTheme.typography.titleLarge)
            LabelValue("진행 시간", formatTime(session.durationSeconds))
            session.completedAttempts?.let { LabelValue("진행한 안내", "$it / ${session.plannedAttempts}회") }
            session.attemptSeconds?.let { LabelValue("시도 / 휴식", "${it}초 / ${session.restSeconds}초") }
            if (session.pain != null || session.fatigue != null) {
                LabelValue("통증", session.pain ?: "입력하지 않음")
                LabelValue("피로", session.fatigue ?: "입력하지 않음")
            } else LabelValue("운동 후 상태", session.comfort ?: "입력하지 않음")
            if (session.endedEarly == true) Hint("중간에 마쳤어요.")
        }
        TextButton({ details = !details }) { Text(if (details) "측정 상세 접기 −" else "측정 상세 보기 +") }
        if (details) SignalDetails(session.rms, session.mav, session.peakActivation, session.sampleCount, session.dataMode)
    }
}
