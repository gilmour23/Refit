package com.refit.app.ui.session

/** Timing for UI guidance, not a prescribed exercise dose or a movement detector. */
data class GuideTiming(val attemptSeconds: Int = 4, val restSeconds: Int = 6, val attempts: Int = 3) {
    init { require(attemptSeconds in 1..60 && restSeconds in 1..60 && attempts in 1..30) }
    val totalSeconds: Long get() = (attemptSeconds + restSeconds).toLong() * attempts
    val description: String get() = "시도 ${attemptSeconds}초 · 휴식 ${restSeconds}초 · ${attempts}회"
    fun progress(elapsedSeconds: Long): GuideProgress {
        val elapsed = elapsedSeconds.coerceIn(0, totalSeconds)
        val cycle = attemptSeconds + restSeconds
        val completed = ((elapsed + restSeconds) / cycle).toInt().coerceAtMost(attempts)
        if (elapsed == totalSeconds) return GuideProgress(GuidePhase.FINISHED, completed, 0, attempts)
        val position = (elapsed % cycle).toInt()
        return GuideProgress(if (position < attemptSeconds) GuidePhase.ATTEMPT else GuidePhase.REST,
            completed, if (position < attemptSeconds) attemptSeconds - position else cycle - position,
            (elapsed / cycle).toInt() + 1)
    }
}

enum class GuidePhase { READY, ATTEMPT, REST, FINISHED }
data class GuideProgress(val phase: GuidePhase, val completedAttempts: Int, val remainingSeconds: Int, val attemptNumber: Int)
