package com.refit.app.data.remote

import com.refit.app.ui.session.GuideTiming
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class ExercisePlanDto(
    val id: String,
    @SerialName("center_id") val centerId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_to") val effectiveTo: String? = null,
    val weekdays: List<Int>,
    @SerialName("planned_attempts") val plannedAttempts: Int,
    @SerialName("attempt_seconds") val attemptSeconds: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
    @SerialName("daily_goal_sessions") val dailyGoalSessions: Int,
    val reason: String,
    @SerialName("created_at") val createdAt: String
) {
    fun timingOrNull(): GuideTiming? = runCatching { GuideTiming(attemptSeconds, restSeconds, plannedAttempts) }.getOrNull()
    fun isActiveFor(userId: String, today: LocalDate): Boolean = runCatching {
        patientId == userId && exerciseType == "wrist_extension" && timingOrNull() != null &&
            dailyGoalSessions in 1..100 && today.dayOfWeek.value in weekdays &&
            !today.isBefore(LocalDate.parse(effectiveFrom)) &&
            (effectiveTo == null || !today.isAfter(LocalDate.parse(effectiveTo)))
    }.getOrDefault(false)
}

fun selectActiveWristPlan(plans: List<ExercisePlanDto>, owner: String, today: LocalDate): ExercisePlanDto? =
    plans.filter { it.isActiveFor(owner, today) }
        .maxWithOrNull(compareBy<ExercisePlanDto> { it.effectiveFrom }.thenBy { it.createdAt })
