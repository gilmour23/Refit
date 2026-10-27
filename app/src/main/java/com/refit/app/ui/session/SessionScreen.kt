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
    val accessibilityScroll = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f
    val interrupted = state.signalStatus == SignalStatus.INTERRUPTED
    val waiting = state.dataMode == "sensor" && state.signalStatus == SignalStatus.WAITING
    val prompt = if (preparing) when(step) {
        0 -> "어느 손목을 운동하나요?"
        1 -> "팔을 받쳐주세요"
        else -> "착용 상태를 확인하세요"
    } else when {
        interrupted -> "기기 연결을\n확인해주세요"
        state.paused -> if (state.restHeld) "더 쉬고 있어요" else "잠시 멈췄어요"
        waiting -> "기기 신호를\n기다리고 있어요"
        state.phase == GuidePhase.READY -> "팔을 받치고 준비하세요"
        state.phase == GuidePhase.REST -> "편하게 쉬세요"
        else -> "손목을 펴보세요"
    }
    val spoken = if (preparing) when(step) {
        0 -> "운동할 손목을 선택하세요."
        1 -> "배운 자세대로 팔을 편하게 받쳐주세요."
        else -> "보조기와 센서를 배운 위치에 착용했는지 확인하세요. 아프거나 조이면 시작하지 마세요."
    } else if (interrupted) "신호가 끊겼어요. 기기 연결을 확인해주세요." else guidanceCue(state)
    LaunchedEffect(spoken) { onInstruction(spoken) }
    BackHandler(enabled = preparing) { if (step > 0) step-- else onBack() }
    Page(if (preparing) "운동 준비" else "손목 펴기", { if (preparing && step > 0) step-- else onBack() },
        compact = true, scrollable = preparing || accessibilityScroll,
        topAction = {
            TextButton({ onVoice(!voice) }, enabled = voiceStatus != VoiceStatus.UNAVAILABLE) {
                Text(when(voiceStatus) { VoiceStatus.LOADING -> "음성 준비 중"; VoiceStatus.UNAVAILABLE -> "음성 없음"; else -> if (voice) "음성 켜짐" else "음성 꺼짐" }, fontSize = 15.sp)
            }
        }, footer = {
            when {
                state.isSaving -> PrimaryAction("기록 저장 중…", {}, false)
                state.running -> {
                    when {
                        state.isConnecting -> PrimaryAction("연결 중…", {}, false)
                        state.paused && (!state.connected || interrupted) -> PrimaryAction("다시 연결", onRetryConnection)
                        state.paused -> PrimaryAction("이어서 하기", onPause)
                        else -> PrimaryAction("잠시 멈추기", onPause)
                    }
                    SecondaryAction("운동 마치기", onStop)
                }
                state.isConnecting -> PrimaryAction("연결 확인 중…", {}, false)
                step < 2 -> PrimaryAction("다음", { step++ }, side != null)
                else -> PrimaryAction(if (state.dataMode == "demo") "데모 안내 시작" else "연결하고 시작", onStart, side != null)
            }
        }) {
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (preparing) {
            Spacer(Modifier.height(12.dp))
            Hint(if (step == 0) "운동할 손목" else "$step / 2 · ${if (step == 1) "자세" else "착용"}")
            Text(prompt, style = MaterialTheme.typography.headlineLarge)
            when(step) {
                0 -> {
                    WristIllustration(side, attempt = true, modifier = Modifier.height(220.dp))
                    ChoiceRow(listOf("왼쪽 손목", "오른쪽 손목"), side?.let(::wristLabel), {
                        val selected = if (it == "왼쪽 손목") "left" else "right"
                        chosenSide = selected; onSide(selected)
                    })
                }
                1 -> {
                    WristIllustration(side, modifier = Modifier.height(260.dp))
                    Text("팔은 받침 위에", style = MaterialTheme.typography.titleLarge)
                    Hint("어깨의 힘을 편하게 빼주세요.")
                    TextButton({ step = 0 }) { Text("${wristLabel(side)} · 바꾸기") }
                }
                else -> {
                    Spacer(Modifier.height(16.dp))
                    Text("1. 보조기 확인", style = MaterialTheme.typography.titleLarge)
                    Hint("배운 위치에 맞게 착용해주세요.")
                    HorizontalDivider()
                    Text("2. 센서 확인", style = MaterialTheme.typography.titleLarge)
                    Hint("들뜨거나 조이지 않는지 보세요.")
                    Hint("아프거나 조이면 시작하지 마세요.")
                    if (state.dataMode == "demo") Panel(color = AccentSurface) {
                        DemoNotice()
                        Hint("센서 연결 없이 안내를 체험합니다.")
                    }
                    Text(state.guideTiming.description, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onSettings) { Text("안내 설정") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.dataMode == "demo") DemoNotice()
                Text("${state.guideTiming.progress(state.elapsedSeconds).attemptNumber} / ${state.guideTiming.attempts}회 안내",
                    style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
                Text(formatTime(state.elapsedSeconds), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().then(if (accessibilityScroll) Modifier else Modifier.weight(1f))) {
                val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f
                val illustrationHeight = (maxHeight - 312.dp).coerceIn(0.dp, 184.dp)
                val compact = illustrationHeight < 72.dp || largeText
                Column(Modifier.fillMaxWidth().background(if (state.phase == GuidePhase.REST) CalmSurface else AccentSurface,
                    RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(if (largeText) 6.dp else 12.dp)) {
                    Text(prompt, style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (!compact) WristIllustration(side, attempt = !state.paused && !interrupted && state.phase == GuidePhase.ATTEMPT,
                        modifier = Modifier.height(illustrationHeight))
                    ActivationFeedback(state, compact = largeText)
                    if (!compact) Text(when {
                        state.paused -> "준비되면 이어서 해주세요."
                        state.phase == GuidePhase.REST -> "팔을 받친 채 힘을 빼주세요."
                        else -> "손목을 위로 펴려고 시도하세요."
                    })
                    if (!state.paused && !interrupted && !waiting) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${state.phaseSecondsRemaining}", fontSize = (if (largeText) 28 else 56).sp,
                            fontWeight = FontWeight.Medium, color = if (state.phase == GuidePhase.REST) Calm else Ink)
                        Text(if (state.phase == GuidePhase.READY) "초 뒤 시작" else "초", color = Muted)
                    }
                    if (!largeText) LinearProgressIndicator(progress = {
                        val duration = when(state.phase) { GuidePhase.READY -> 3; GuidePhase.REST -> state.guideTiming.restSeconds; else -> state.guideTiming.attemptSeconds }
                        (1f - state.phaseSecondsRemaining.toFloat() / duration).coerceIn(0f,1f)
                    }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Accent, trackColor = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onRepeat, enabled = voice && voiceStatus == VoiceStatus.AVAILABLE) { Text("안내 다시 듣기", fontSize = 14.sp) }
                if (state.phase == GuidePhase.REST && !state.paused) TextButton(onMoreRest) { Text("더 쉬기", fontSize = 14.sp) }
            }
        }
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
            Hint("배운 운동 방법에 맞춰 사용하세요.")
            TimingInput("시도", attempt, { attempt = it }, 60, "초", "attempt-time")
            TimingInput("휴식", rest, { rest = it }, 60, "초", "rest-time")
            TimingInput("반복", count, { count = it }, 30, "회", "attempt-count")
            if (!valid) Text("각 항목에 범위 안의 정수를 입력하세요.",color=MaterialTheme.colorScheme.error)
        }
    }
}


@Composable
private fun TimingInput(label: String, value: String, onValue: (String) -> Unit, max: Int, unit: String, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        TextButton({ onValue(((value.toIntOrNull() ?: 1) - 1).coerceIn(1, max).toString()) },
            modifier = Modifier.semantics { contentDescription = "$label 줄이기" }) { Text("−") }
        OutlinedTextField(value, onValue, Modifier.width(82.dp).then(Modifier.semantics { testTag = tag; contentDescription = "$label 시간" }),
            singleLine = true, suffix = { Text(unit) }, textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        TextButton({ onValue(((value.toIntOrNull() ?: 1) + 1).coerceIn(1, max).toString()) },
            modifier = Modifier.semantics { contentDescription = "$label 늘리기" }) { Text("+") }
    }
    HorizontalDivider()
}
