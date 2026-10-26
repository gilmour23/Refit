package com.refit.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.refit.app.ui.components.*
import com.refit.app.ui.result.ResultScreen

@Composable
fun SessionScreen(viewModel: SessionViewModel, onBackToHome: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEnd by rememberSaveable { mutableStateOf(false) }
    var wasPaused by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var instruction by remember { mutableStateOf("") }
    var repeatToken by remember { mutableIntStateOf(0) }
    val voiceStatus = SessionVoice(instruction, state.voiceEnabled && !showEnd && state.result == null, repeatToken)
    val requestEnd = { wasPaused = state.paused; viewModel.setPaused(true); showEnd = true }
    val back = {
        if (state.running) requestEnd()
        else if (!state.isSaving && !state.isConnecting) { viewModel.resetSession(); onBackToHome() }
    }
    BackHandler { back() }
    DisposableEffect(viewModel) { onDispose { viewModel.setPaused(true) } }
    if (showSettings) {
        GuideSettingsScreen(state.guideTiming, { showSettings = false }, { a, r, n ->
            if (viewModel.configureGuidance(a, r, n)) showSettings = false
        })
        return
    }
    val result = state.result
    if (result != null) {
        ResultScreen(result, { viewModel.resetSession(); onBackToHome() }, viewModel::recordSymptoms, state.message)
    } else {
        SessionContent(state, state.voiceEnabled, viewModel::setVoiceEnabled, viewModel::startSession,
            { viewModel.setPaused(!state.paused) }, requestEnd, { showSettings = true }, back,
            onSide = viewModel::configureWristSide, onMoreRest = viewModel::extendRest,
            onRetryConnection = viewModel::retryConnection, voiceStatus = voiceStatus,
            onRepeat = { repeatToken++ }, onInstruction = { instruction = it })
    }
    if (showEnd && state.running) AlertDialog(
        onDismissRequest = { showEnd = false; viewModel.setPaused(wasPaused) },
        title = { Text("여기서 마칠까요?") },
        confirmButton = { TextButton({ showEnd = false; viewModel.stopSession() }) { Text("기록하고 마치기") } },
        dismissButton = { TextButton({ showEnd = false; viewModel.setPaused(wasPaused) }) { Text("계속하기") } })
}

@Composable
fun SessionContent(state: SessionUiState, voice: Boolean, onVoice: (Boolean) -> Unit,
    onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit,
    onSide: (String) -> Unit = {}, onMoreRest: () -> Unit = {}, onRetryConnection: () -> Unit = {},
    voiceStatus: VoiceStatus = VoiceStatus.AVAILABLE, onRepeat: () -> Unit = {}, onInstruction: (String) -> Unit = {}) {
    var chosenSide by rememberSaveable { mutableStateOf(state.wristSide) }
    var step by rememberSaveable { mutableIntStateOf(if (state.wristSide == null) 0 else 1) }
    LaunchedEffect(state.wristSide) { if (state.wristSide != null) chosenSide = state.wristSide }
    val side = state.wristSide ?: chosenSide
    val preparing = !state.running
    val title = if (preparing) "운동 준비" else wristLabel(side)
    val prompt = if (preparing) when(step) {
        0 -> "어느 손목을 운동하나요?"
        1 -> "팔을 받쳐주세요"
        else -> "착용 상태를 확인하세요"
    } else when {
        state.signalStatus == SignalStatus.INTERRUPTED -> "기기 연결을\n확인해주세요"
        state.paused -> if (state.restHeld) "더 쉬고 있어요" else "잠시 쉬어가요"
        state.dataMode == "sensor" && state.signalStatus == SignalStatus.WAITING -> "기기 신호를\n기다리고 있어요"
        state.phase == GuidePhase.READY -> "팔을 받치고\n준비하세요"
        state.phase == GuidePhase.REST -> "편하게 쉬세요"
        else -> "손목을 펴려고\n시도하세요"
    }
    val spoken = if (preparing) when(step) {
        0 -> "운동할 손목을 선택하세요."
        1 -> "배운 자세대로 팔을 편하게 받쳐주세요."
        else -> "보조기와 센서를 배운 위치에 착용했는지 확인하세요. 아프거나 조이면 시작하지 마세요."
    } else if (state.signalStatus == SignalStatus.INTERRUPTED) "신호가 끊겼어요. 기기 연결을 확인해주세요." else guidanceCue(state)
    LaunchedEffect(spoken) { onInstruction(spoken) }
    BackHandler(enabled = preparing) { if (step > 0) step-- else onBack() }
    Page(title, { if (preparing && step > 0) step-- else onBack() },
        compact = state.running, scrollable = !state.running,
        topAction = {
            TextButton({ onVoice(!voice) }, enabled = voiceStatus != VoiceStatus.UNAVAILABLE) {
                Text(when(voiceStatus) { VoiceStatus.LOADING -> "음성 준비 중"; VoiceStatus.UNAVAILABLE -> "음성 없음"; else -> if (voice) "음성 켜짐" else "음성 꺼짐" }, fontSize = 15.sp)
            }
        }, footer = {
            when {
                state.isSaving -> PrimaryAction("기록 저장 중…", {}, false)
                state.running -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        when {
                            state.isConnecting -> PrimaryAction("연결 중…", {}, false)
                            state.paused && (!state.connected || state.signalStatus == SignalStatus.INTERRUPTED) -> PrimaryAction("다시 연결", onRetryConnection)
                            state.paused -> PrimaryAction("이어서 하기", onPause)
                            state.phase == GuidePhase.REST -> PrimaryAction("더 쉬기", onMoreRest)
                            else -> PrimaryAction("잠시 멈추기", onPause)
                        }
                    }
                    Column(Modifier.weight(1f)) { SecondaryAction("운동 마치기", onStop) }
                }
                state.isConnecting -> PrimaryAction("연결 확인 중…", {}, false)
                step < 2 -> PrimaryAction("다음", { step++ }, side != null)
                else -> PrimaryAction(if (state.dataMode == "demo") "데모 안내 시작" else "연결하고 시작", onStart, side != null)
            }
        }) {
        if (state.dataMode == "demo" && preparing) DemoNotice()
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (preparing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(4.dp).background(if (index <= step) Accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                }
            }
            Hint("${step + 1} / 3 · 준비")
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(prompt, style = MaterialTheme.typography.headlineMedium)
                when(step) {
                    0 -> ChoiceRow(listOf("왼쪽 손목", "오른쪽 손목"), side?.let(::wristLabel), {
                        val selected = if (it == "왼쪽 손목") "left" else "right"
                        chosenSide = selected; onSide(selected)
                    })
                    1 -> {
                        Surface(color = AccentSurface, shape = RoundedCornerShape(16.dp)) {
                            WristIllustration(side, modifier = Modifier.height(210.dp).padding(12.dp))
                        }
                        Text("배운 자세대로 팔을 편하게 놓으세요.")
                        TextButton({ step = 0 }) { Text("${wristLabel(side)} · 바꾸기") }
                    }
                    else -> {
                        Text("보조기와 센서가 배운 위치에 있나요?", style = MaterialTheme.typography.titleMedium)
                        Hint("아프거나 조이면 시작하지 마세요.")
                        HorizontalDivider()
                        Text(state.guideTiming.description, fontWeight = FontWeight.SemiBold)
                        TextButton(onSettings) { Text("안내 설정") }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    state.paused -> "일시정지"
                    state.phase == GuidePhase.READY -> "준비"
                    state.phase == GuidePhase.REST -> "휴식"
                    else -> "시도"
                }, color = if (state.phase == GuidePhase.REST || state.paused) Calm else Accent, style = MaterialTheme.typography.titleMedium)
                if (state.dataMode == "demo") DemoNotice()
            }
            if (state.dataMode != "demo") Hint(when {
                state.signalStatus == SignalStatus.INTERRUPTED -> "신호가 끊겨 안내를 멈췄어요"
                state.signalStatus == SignalStatus.RECEIVING -> "연결됨 · 신호 수신 중"
                else -> "연결됨 · 신호 기다리는 중"
            })
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(if (state.phase == GuidePhase.REST || state.paused) CalmSurface else AccentSurface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    if (maxHeight >= 48.dp) WristIllustration(side, attempt = !state.paused && state.phase == GuidePhase.ATTEMPT,
                        modifier = Modifier.heightIn(max = 210.dp).fillMaxHeight().padding(8.dp))
                }
                if (!state.paused && (state.dataMode == "demo" || state.signalStatus == SignalStatus.RECEIVING)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.phaseSecondsRemaining}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (state.phase == GuidePhase.REST) Calm else Accent)
                        Text(when(state.phase) { GuidePhase.READY -> "초 뒤 시작"; GuidePhase.REST -> "초 뒤 다음 시도"; else -> "초 뒤 휴식" }, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            if (state.phase != GuidePhase.READY) LabelValue("${state.guideTiming.progress(state.elapsedSeconds).attemptNumber} / ${state.guideTiming.attempts}번째 안내", formatTime(state.elapsedSeconds))
        }
        TextButton(onRepeat, enabled = voice && voiceStatus == VoiceStatus.AVAILABLE) { Text("안내 다시 듣기") }
    }
}

@Composable
fun GuideSettingsScreen(timing: GuideTiming, onDismiss: () -> Unit, onSave: (Int, Int, Int) -> Unit) {
    var attempt by remember { mutableStateOf(timing.attemptSeconds.toString()) }
    var rest by remember { mutableStateOf(timing.restSeconds.toString()) }
    var count by remember { mutableStateOf(timing.attempts.toString()) }
    val a=attempt.toIntOrNull(); val r=rest.toIntOrNull(); val n=count.toIntOrNull()
    val valid=a != null && a in 1..60 && r != null && r in 1..60 && n != null && n in 1..30
    BackHandler(onBack = onDismiss)
    Page("안내 시간 설정", onDismiss, footer = { PrimaryAction("적용", { onSave(a!!,r!!,n!!) },valid) }) {
        DemoNotice()
        Panel {
            Hint("운동 처방이 아닌 체험용 설정입니다.")
            OutlinedTextField(attempt,{attempt=it},label={Text("시도 시간 · 1~60초")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(rest,{rest=it},label={Text("휴식 시간 · 1~60초")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            OutlinedTextField(count,{count=it},label={Text("반복 안내 · 1~30회")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            if (!valid) Text("각 항목에 범위 안의 정수를 입력하세요.",color=MaterialTheme.colorScheme.error)
        }
    }
}

