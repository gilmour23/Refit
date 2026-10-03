package com.refit.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.refit.app.ui.auth.AuthViewModel
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
class AuthAccountScopeTest {
    @Test(timeout = 30000) fun preservesSameOwnerDuringReloadButClearsOnLogoutAndOwnerChange() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val c = createSupabaseClient("https://test.supabase.co", "public-test-key") {
            httpEngine = MockEngine { error("No network expected") }
            install(Auth) {
                enableLifecycleCallbacks = false
                alwaysAutoRefresh = false
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        val root = ViewModelStore()
        try {
            c.auth.awaitInitialization()
            val vm = AuthViewModel(AuthRepository(c))
            root.put("auth", vm)
            c.auth.importSession(session("owner-a"), autoRefresh = false)
            vm.authState.first { it is AuthState.Authenticated }
            val exercise = TrackingViewModel()
            vm.accountViewModelStoreOwner.viewModelStore.put("exercise", exercise)
            c.auth.setSessionStatus(SessionStatus.Initializing)
            assertTrue(vm.authState.value is AuthState.Authenticated)
            assertFalse(exercise.cleared)
            c.auth.importSession(session("owner-a"), autoRefresh = false)
            assertSame(exercise, vm.accountViewModelStoreOwner.viewModelStore.get("exercise"))
            c.auth.clearSession()
            vm.authState.first { it == AuthState.Unauthenticated }
            assertTrue(exercise.cleared)
            assertNull(vm.accountViewModelStoreOwner.viewModelStore.get("exercise"))
            c.auth.importSession(session("owner-b"), autoRefresh = false)
            vm.authState.first { (it as? AuthState.Authenticated)?.userId == "owner-b" }
            val nextExercise = TrackingViewModel()
            vm.accountViewModelStoreOwner.viewModelStore.put("exercise", nextExercise)
            c.auth.importSession(session("owner-c"), autoRefresh = false)
            vm.authState.first { (it as? AuthState.Authenticated)?.userId == "owner-c" }
            assertTrue(nextExercise.cleared)
            val lastExercise = TrackingViewModel()
            vm.accountViewModelStoreOwner.viewModelStore.put("exercise", lastExercise)
            root.clear()
            assertTrue(lastExercise.cleared)
        } finally { root.clear(); c.close(); Dispatchers.resetMain() }
    }

    private fun session(id: String) = UserSession("access-test", "refresh-test", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = id))
    private class TrackingViewModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }
}
