package com.refit.app.ui.session

import org.junit.Assert.*
import org.junit.Test

class GuideTimingTest {
    @Test fun onlyFullAttemptIntervalsAreCountedAndFinalRestEndsGuidance() {
        val timing = GuideTiming(4, 6, 3)
        assertEquals(GuideProgress(GuidePhase.ATTEMPT, 0, 1, 1), timing.progress(3))
        assertEquals(GuideProgress(GuidePhase.REST, 1, 6, 1), timing.progress(4))
        assertEquals(GuideProgress(GuidePhase.ATTEMPT, 1, 4, 2), timing.progress(10))
        assertEquals(GuideProgress(GuidePhase.REST, 3, 6, 3), timing.progress(24))
        assertEquals(GuideProgress(GuidePhase.FINISHED, 3, 0, 3), timing.progress(30))
    }
    @Test fun invalidTimingCannotCreateAnUnfinishableGuidance() {
        listOf(Triple(0,6,3), Triple(4,0,3), Triple(4,6,0), Triple(61,6,3)).forEach { (a,r,n) ->
            assertThrows(IllegalArgumentException::class.java) { GuideTiming(a,r,n) }
        }
    }
}
