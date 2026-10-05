package com.refit.app.ui.session

enum class SignalStatus { WAITING, RECEIVING, INTERRUPTED, DEMO }
enum class VoiceStatus { LOADING, AVAILABLE, UNAVAILABLE }

fun wristLabel(side: String?): String = when (side) {
    "left" -> "왼쪽 손목"
    "right" -> "오른쪽 손목"
    else -> "손목 미선택"
}

fun guidanceCue(state: SessionUiState): String = when {
    state.paused -> "편하게 쉬세요."
    state.dataMode == "sensor" && state.signalStatus == SignalStatus.WAITING -> "기기 신호를 기다리고 있어요."
    state.phase == GuidePhase.READY -> "팔을 받친 채 준비하세요."
    state.phase == GuidePhase.REST -> "편하게 쉬세요."
    else -> "손목을 위로 펴려고 시도하세요."
}
