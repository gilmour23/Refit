package com.refit.app.data.remote

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ExercisePlanTest {
    private fun plan(id: String = "p", owner: String = "owner", from: String = "2026-10-01",
                     to: String? = null, weekdays: List<Int> = listOf(7), created: String = "2026-10-01T00:00:00Z",
                     attempts: Int = 3) = ExercisePlanDto(id, "center", owner, "clinician",
        "wrist_extension", from, to, weekdays, attempts, 4, 6, 1, "안내", created)

    @Test fun selectsNewestCurrentPlanForOwnerAndWeekday() {
        val plans = listOf(plan("old"), plan("new", from = "2026-11-01"),
            plan("foreign", owner = "other", from = "2026-11-01"),
            plan("future", from = "2026-11-02"), plan("expired", to = "2026-10-31"),
            plan("wrongday", weekdays = listOf(1)))
        assertEquals("new", selectActiveWristPlan(plans, "owner", LocalDate.parse("2026-11-01"))!!.id)
    }

    @Test fun unsupportedOrMalformedPlansCannotBeApplied() {
        assertNull(selectActiveWristPlan(listOf(plan(attempts = 31)), "owner", LocalDate.parse("2026-11-01")))
        assertNull(selectActiveWristPlan(listOf(plan(from = "invalid")), "owner", LocalDate.parse("2026-11-01")))
        assertNull(selectActiveWristPlan(listOf(plan().copy(exerciseType = "unknown")), "owner", LocalDate.parse("2026-11-01")))
    }
}
