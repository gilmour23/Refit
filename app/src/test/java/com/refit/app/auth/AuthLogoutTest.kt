package com.refit.app.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.refit.app.ui.auth.AuthViewModel
import com.refit.app.ui.session.SessionViewModel
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.model.EmgSample
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import com.refit.app.data.repository.RehabSessionRepository
import com.refit.app.data.repository.SessionSyncCoordinator
import com.refit.app.data.source.EmgConnectionState
import com.refit.app.data.source.EmgDataSource
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
class AuthLogoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clearStorage() {
        context.getSharedPreferences("logout-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun client(engine: MockEngine) = createSupabaseClient("https://test.supabase.co", "public-test-key") {
        httpEngine = engine
        install(Auth) {
            enableLifecycleCallbacks = false
            alwaysAutoRefresh = false
            autoLoadFromStorage = true
            autoSaveToStorage = true
            codeVerifierCache = MemoryCodeVerifierCache()
            sessionManager = SettingsSessionManager(SharedPreferencesSettings(
                context.getSharedPreferences("logout-test", Context.MODE_PRIVATE)
            ))
        }
    }

    // Keep the HTTP handler inline, so a virtual
    // logout deadline cannot run ahead of a request on a real IO thread.
    private fun engine(handler: MockRequestHandler) = MockEngine(MockEngineConfig().apply {
        dispatcher = Dispatchers.Unconfined
        addHandler(handler)
    })

    @Test(timeout = 30000) fun delayedLogoutImmediatelyDisposesAccountAndPreventsReopening() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val entered = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val c = client(engine { request ->
            requests.incrementAndGet()
            if (request.url.encodedPath.endsWith("/logout")) {
                assertEquals("local", request.url.parameters["scope"])
                assertEquals("Bearer access-owner-a", request.headers[HttpHeaders.Authorization])
                entered.complete(Unit)
                reply.await()
                respond("", HttpStatusCode.NoContent)
            } else {
                respond("""{"access_token":"access-owner-b","refresh_token":"refresh-owner-b",
                    "expires_in":3600,"token_type":"bearer",
                    "user":{"aud":"authenticated","id":"owner-b","email":"b@example.com"}}""",
                    HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        })
        val root = ViewModelStore()
        try {
            c.auth.awaitInitialization()
            c.auth.importSession(session(), autoRefresh = false)
            val vm = AuthViewModel(AuthRepository(c))
            root.put("auth", vm)
            vm.authState.first { it is AuthState.Authenticated }
            val application = ApplicationProvider.getApplicationContext<Application>()
            val records = RehabSessionRepository(ReFitDatabase.getInstance(application).rehabSessionDao())
            val source = TrackingSource()
            val exercise = SessionViewModel(application, "owner-a", source, repository = records,
                syncCoordinator = SessionSyncCoordinator(records, RehabSessionRemoteDataSource(null)))
            vm.accountViewModelStoreOwner.viewModelStore.put("exercise", exercise)
            exercise.configureWristSide("left"); exercise.startSession()
            assertTrue(source.connected)

            vm.signOut()
            assertEquals(AuthState.Unauthenticated, vm.authState.value)
            assertNull(vm.accountViewModelStoreOwner.viewModelStore.get("exercise"))
            assertFalse(source.connected)
            assertFalse(exercise.uiState.value.running)
            assertNull(exercise.uiState.value.result)
            assertTrue(vm.uiState.value.isSubmitting)
            entered.await()
            assertNull(c.auth.sessionManager.loadSessionOrNull())
            assertNull(c.auth.currentSessionOrNull())
            val reopened = client(engine { error("No session should be restored") })
            try {
                reopened.auth.awaitInitialization()
                assertEquals(AuthState.Unauthenticated, AuthRepository(reopened).authState.first())
            } finally { reopened.close() }

            // SDK focus/restoration emissions must not unlock the old account.
            c.auth.setSessionStatus(SessionStatus.Initializing)
            c.auth.setSessionStatus(SessionStatus.Authenticated(session()))
            assertEquals(AuthState.Unauthenticated, vm.authState.value)
            vm.signOut()
            vm.signIn("b@example.com", "password-b")
            vm.signUp("b@example.com", "password-b")
            assertEquals(1, requests.get())
            reply.complete(Unit)
            vm.uiState.first { !it.isSubmitting }
            assertEquals(AuthState.Unauthenticated, vm.authState.value)

            vm.signIn("b@example.com", "password-b")
            vm.authState.first { (it as? AuthState.Authenticated)?.userId == "owner-b" }
            assertEquals("owner-b", c.auth.sessionManager.loadSessionOrNull()?.user?.id)
        } finally {
            reply.complete(Unit)
            root.clear(); c.close(); Dispatchers.resetMain()
        }
    }

    @Test(timeout = 30000) fun cancelledRemoteLogoutCannotRestoreDeletedCredentials() = runTest {
        val entered = CompletableDeferred<Unit>()
        val c = client(engine {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            respond("", HttpStatusCode.NoContent)
        })
        try {
            c.auth.awaitInitialization()
            c.auth.importSession(session(), autoRefresh = false)
            val logout = launch { AuthRepository(c).signOut() }
            entered.await()
            assertNull(c.auth.sessionManager.loadSessionOrNull())
            logout.cancelAndJoin()
            assertNull(c.auth.currentSessionOrNull())
            assertNull(c.auth.sessionManager.loadSessionOrNull())
        } finally { c.close() }
    }

    @Test(timeout = 30000) fun stalledServerDoesNotBlockLoginIndefinitely() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val entered = CompletableDeferred<Unit>()
        val c = client(engine {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            respond("", HttpStatusCode.NoContent)
        })
        val root = ViewModelStore()
        try {
            c.auth.awaitInitialization()
            c.auth.importSession(session(), autoRefresh = false)
            val vm = AuthViewModel(AuthRepository(c))
            root.put("auth", vm)
            vm.authState.first { it is AuthState.Authenticated }
            vm.signOut()
            entered.await()
            assertTrue(vm.uiState.value.isSubmitting)
            kotlinx.coroutines.withTimeout(15000) { vm.uiState.first { !it.isSubmitting } }
            assertEquals(AuthState.Unauthenticated, vm.authState.value)
            assertNull(c.auth.sessionManager.loadSessionOrNull())
            assertNotNull(vm.uiState.value.message)
        } finally { root.clear(); c.close(); Dispatchers.resetMain() }
    }

    private fun session() = UserSession("access-owner-a", "refresh-owner-a", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = "owner-a"))
    private class TrackingSource : EmgDataSource {
        override val connectionState = MutableStateFlow<EmgConnectionState>(EmgConnectionState.Disconnected)
        var connected = false
        override suspend fun connect() { connected = true }
        override suspend fun disconnect() { connected = false }
        override fun observeEmg() = emptyFlow<EmgSample>()
    }
}
