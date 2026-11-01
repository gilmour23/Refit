package com.refit.app.ui.center

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.refit.app.data.remote.ExercisePlanDto
import com.refit.app.ui.components.*

@Composable
fun PatientCenterScreen(
    state: PatientCenterUiState,
    appliedPlanId: String?,
    canApply: Boolean,
    onRefresh: () -> Unit,
    onAccept: (String, Boolean) -> Unit,
    onRevoke: (String) -> Unit,
    onApply: (ExercisePlanDto) -> Unit,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit = {}
) {
    var code by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    LaunchedEffect(state.snapshot?.displayName, state.snapshot != null) {
        state.snapshot?.let { displayName = it.displayName.orEmpty() }
    }
    var revokeId by remember { mutableStateOf<String?>(null) }
    Page("재활센터 연결과 계획", onBack = onBack) {
        if (state.loading) CircularProgressIndicator()
        state.message?.let { Hint(it) }
        Panel {
            Text("센터에서 확인할 이름", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(displayName, { displayName = it.take(80) }, label = { Text("표시 이름 (선택)") },
                enabled = !state.loading, singleLine = true)
            PrimaryAction("이름 저장", { onSaveName(displayName) }, enabled = !state.loading && state.snapshot != null &&
                displayName.trim() != state.snapshot.displayName.orEmpty())
            Hint("이름은 선택 사항입니다. 입력하지 않아도 센터 연결에 동의할 수 있어요.")
        }
        TextButton(onRefresh, enabled = !state.loading) { Text("센터와 계획 새로 확인") }
        state.snapshot?.let { snapshot ->
            val links = snapshot.links.filter { it.revokedAt == null }
            if (links.isEmpty()) Hint("연결된 재활센터가 없어요.")
            links.forEach { link ->
                Panel {
                    LabelValue("연결된 센터", snapshot.centers.find { it.id == link.centerId }?.name ?: link.centerId)
                    Hint("연결일 ${link.joinedAt.take(10)}")
                    TextButton({ revokeId = link.centerId }, enabled = !state.loading) { Text("센터 연결 해제") }
                }
            }
            snapshot.activePlan?.let { plan ->
                Panel(color = AccentSurface) {
                    Text("치료사가 보낸 손목 운동 계획", style = MaterialTheme.typography.titleMedium)
                    LabelValue("출처", snapshot.centers.find { it.id == plan.centerId }?.name ?: plan.centerId)
                    LabelValue("적용 기간", "${plan.effectiveFrom} ~ ${plan.effectiveTo ?: "종료일 미정"}")
                    LabelValue("작성일", plan.createdAt.take(10))
                    Text(plan.timingOrNull()!!.description)
                    Text("하루 안내 목표 ${plan.dailyGoalSessions}회")
                    if (plan.reason.isNotBlank()) Hint(plan.reason)
                    PrimaryAction(if (appliedPlanId == plan.id) "이 계획 적용 중" else "이 계획을 안내에 적용",
                        { onApply(plan) }, enabled = canApply && !state.loading && appliedPlanId != plan.id)
                    Hint("시도 구간 수는 손목이 움직인 횟수를 뜻하지 않습니다.")
                }
            } ?: Hint("오늘 적용할 손목 운동 계획이 없어요.")
        }
        Text("센터 초대코드", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(code, { code = it.take(256); consent = false }, label = { Text("치료사에게 받은 초대코드") },
            enabled = !state.loading, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consent, { consent = it }, enabled = !state.loading)
            Text("초대를 받은 재활센터와 내 운동 기록·운동 후 응답·기록된 신호를 공유하고 계획을 받는 데 동의합니다.",
                modifier = Modifier.weight(1f))
        }
        PrimaryAction("동의하고 센터 연결", {
            onAccept(code, consent)
            code = ""
            consent = false
        }, enabled = consent && code.isNotBlank() && !state.loading)
        Hint("센터 연결은 동의 후에만 진행됩니다. 연결을 해제하면 해당 센터의 기록 조회와 계획 적용이 중단됩니다.")
    }
    revokeId?.let { id ->
        AlertDialog(onDismissRequest = { revokeId = null }, title = { Text("센터 연결을 해제할까요?") },
            text = { Text("센터의 기록 조회 권한과 이 센터 계획의 적용을 중단합니다. 내 운동 기록은 유지됩니다.") },
            confirmButton = { TextButton({ onRevoke(id); revokeId = null }, enabled = !state.loading) { Text("연결 해제") } },
            dismissButton = { TextButton({ revokeId = null }) { Text("취소") } })
    }
}
