package com.refit.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.SyncStatus
import com.refit.app.ui.components.*
import com.refit.app.ui.session.wristLabel
import com.refit.app.ui.result.SignalDetails

@Composable
fun HistoryScreen(sessions: List<RehabSessionEntity>, onBack: () -> Unit, isSyncing: Boolean,
                  syncMessage: String?, onRetrySync: () -> Unit, cloudSyncEnabled: Boolean = true,
                  onOpenFitting: () -> Unit = {}) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        HistoryDetailScreen(selected) { selectedId = null }
        return
    }
    BackHandler(onBack = onBack)
    val pending = sessions.count { it.syncStatus != SyncStatus.SYNCED }
    Page("운동 기록", footer = { BottomTabs("history", onBack, {}, onOpenFitting) }) {
        if (!cloudSyncEnabled) Hint("이 기기에 저장된 데모 기록입니다.")
        if (sessions.isEmpty()) Panel {
            Text("아직 운동 기록이 없어요", style = MaterialTheme.typography.titleMedium)
            PrimaryAction("홈으로 돌아가기", onBack)
        }
        if (cloudSyncEnabled && pending > 0) Panel {
            Text("동기화 대기 ${pending}개", fontWeight = FontWeight.SemiBold)
            SecondaryAction(if (isSyncing) "동기화 중…" else "다시 동기화", onRetrySync, !isSyncing)
            syncMessage?.let { Hint(it) }
        }
        sessions.groupBy { formatDate(it.createdAt, "M월 d일 EEEE") }.forEach { (date, rows) ->
            Text(date, style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEachIndexed { index, session ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable { selectedId = session.id }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = AccentSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), modifier = Modifier.size(64.dp)) {
                            com.refit.app.ui.session.WristIllustration(session.wristSide, true, Modifier.size(64.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(session.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(formatTime(session.durationSeconds), fontWeight = FontWeight.SemiBold)
                            Text("  ›", color = Muted)
                        }
                        Hint(listOfNotNull(formatDate(session.createdAt, "HH:mm"), session.completedAttempts?.let { "안내 $it / ${session.plannedAttempts}회" }, if (session.dataMode == "demo") "데모" else null).joinToString(" · "))
                        val symptoms = listOfNotNull(session.pain?.let { "통증 · $it" }, session.fatigue?.let { "피로 · $it" })
                        if (symptoms.isNotEmpty()) Hint(symptoms.joinToString("  /  "))
                        else session.comfort?.let { Hint("운동 후 · $it") }
                        }
                    }
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
        com.refit.app.ui.session.WristIllustration(session.wristSide, true, Modifier.height(184.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(session.wristSide?.let(::wristLabel) ?: "손목 펴기 시도", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat("진행 시간", formatTime(session.durationSeconds), Modifier.weight(1f))
                session.completedAttempts?.let { SummaryStat("진행한 안내", "$it / ${session.plannedAttempts}회", Modifier.weight(1f)) }
            }
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


