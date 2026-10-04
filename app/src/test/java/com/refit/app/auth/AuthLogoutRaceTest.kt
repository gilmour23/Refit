package com.refit.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(SupabaseInternal::class)
class AuthLogoutRaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clearStorage() {
        context.getSharedPreferences("logout-race", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun storage() = SettingsSessionManager(SharedPreferencesSettings(
        context.getSharedPreferences("logout-race", Context.MODE_PRIVATE)))
    private fun client(engine: MockEngine, manager: SessionManager = storage()) =
        createSupabaseClient("https://test.supabase.co", "public-test-key") {
            httpEngine = engine
            install(Auth) {
                enableLifecycleCallbacks = false
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                autoSaveToStorage = true
                sessionManager = manager
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }

    @Test(timeout = 30000) fun repositoriesSharingClientCannotEraseNewLoginWithOldLogout() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val c = client(MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/logout")) {
                    entered.complete(Unit)
                    reply.await()
                    respond("", HttpStatusCode.NoContent)
                } else {
                    respond("""{"access_token":"access-b","refresh_token":"refresh-b",
                        "expires_in":3600,"token_type":"bearer",
                        "user":{"aud":"authenticated","id":"owner-b","email":"b@example.com"}}""",
                        HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
            }
        }))
        try {
            c.auth.awaitInitialization()
            c.auth.importSession(session(), autoRefresh = false)
            val logout = launch { AuthRepository(c).signOut() }
            entered.await()
            val login = async { AuthRepository(c).signIn("b@example.com", "password-b") }
            // A second root's login must wait for the first root's cleanup.
            assertNull(withTimeoutOrNull(500) { login.await() })
            reply.complete(Unit)
            logout.join()
            login.await()
            assertEquals("owner-b", c.auth.currentUserOrNull()?.id)
            assertEquals("owner-b", c.auth.sessionManager.loadSessionOrNull()?.user?.id)
        } finally { reply.complete(Unit); c.close() }
    }

    @Test(timeout = 30000) fun cancellationDuringPersistedTokenLookupStillDeletesSession() = runBlocking {
        val delegate = storage()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blockLookup = false
        val manager = object : SessionManager {
            override suspend fun saveSession(session: UserSession) = delegate.saveSession(session)
            override suspend fun deleteSession() = delegate.deleteSession()
            override suspend fun loadSession(): UserSession {
                if (blockLookup) { entered.complete(Unit); release.await() }
                return delegate.loadSession()
            }
        }
        val c = client(MockEngine { error("Cancelled logout must not make HTTP requests") }, manager)
        try {
            c.auth.awaitInitialization()
            c.auth.importSession(session(), autoRefresh = false)
            c.auth.setSessionStatus(SessionStatus.Initializing)
            blockLookup = true
            val logout = launch { AuthRepository(c).signOut() }
            entered.await()
            logout.cancelAndJoin()
            assertNull(delegate.loadSessionOrNull())
            assertNull(c.auth.currentSessionOrNull())
        } finally { release.complete(Unit); c.close() }
    }

    private fun session() = UserSession("access-a", "refresh-a", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = "owner-a"))
}
