package com.refit.app.ui.center

import androidx.lifecycle.ViewModelStore
import com.refit.app.data.remote.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientCenterViewModelTest {
    @Test fun explicitRevokeWaitsForRefreshInsteadOfBeingDiscarded() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        var reads = 0
        var revoked = false
        val gateway = object : PatientCenterGateway {
            override suspend fun read(owner: String, today: LocalDate): PatientCenterSnapshot {
                reads++
                if (reads == 1) release.await()
                return PatientCenterSnapshot(listOf(CenterLinkDto("center", owner, null,
                    "2026-11-01T00:00:00Z", if (revoked) "2026-11-01T01:00:00Z" else null)), emptyList(), null)
            }
            override suspend fun acceptInvite(owner: String, code: String, consent: Boolean): CenterLinkDto = error("unused")
            override suspend fun revoke(owner: String, centerId: String) {
                assertEquals("owner", owner)
                assertEquals("center", centerId)
                revoked = true
            }
        }
        val store = ViewModelStore()
        try {
            val vm = PatientCenterViewModel("owner", gateway); store.put("center", vm)
            vm.refresh()
            assertTrue(vm.state.value.loading)
            vm.revoke("center")
            assertFalse(revoked)
            release.complete(Unit)
            runCurrent()
            assertTrue(revoked)
            assertEquals(2, reads)
            assertNotNull(vm.state.value.snapshot!!.links.single().revokedAt)
            assertFalse(vm.state.value.loading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
