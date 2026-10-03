package com.refit.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(SupabaseInternal::class)
class AuthRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearStorage() {
        context.getSharedPreferences("auth-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun client(): SupabaseClient = createSupabaseClient("https://test.supabase.co", "public-test-key") {
        httpEngine = MockEngine { throw IOException("offline") }
        install(Auth) {
            autoSetupPlatform = false
            alwaysAutoRefresh = false
            autoLoadFromStorage = true
            autoSaveToStorage = true
            codeVerifierCache = MemoryCodeVerifierCache()
            sessionManager = SettingsSessionManager(SharedPreferencesSettings(
                context.getSharedPreferences("auth-test", Context.MODE_PRIVATE)
            ))
        }
    }

    private fun session(id: String) = UserSession(
        accessToken = "access-test", refreshToken = "refresh-test", expiresIn = 3600,
        tokenType = "bearer", user = UserInfo(aud = "authenticated", id = id, email = "$id@example.com")
    )

    @Test fun restoresOwnerAfterClientRecreationFromPersistedStorage() = runBlocking {
        val first = client()
        first.auth.awaitInitialization()
        first.auth.importSession(session("owner-a"), autoRefresh = false)
        first.close()
        val reopened = client()
        try {
            reopened.auth.awaitInitialization()
            val state = AuthRepository(reopened).authState.first()
            assertEquals(AuthState.Authenticated("owner-a", "owner-a@example.com"), state)
        } finally { reopened.close() }
    }

    @Test fun offlineLogoutClearsMemoryAndPersistedSession() = runBlocking {
        val first = client()
        first.auth.awaitInitialization()
        first.auth.importSession(session("owner-a"), autoRefresh = false)
        try {
            try { AuthRepository(first).signOut() } catch (_: IOException) { }
            assertTrue(first.auth.sessionStatus.value is SessionStatus.NotAuthenticated)
            assertNull(first.auth.sessionManager.loadSessionOrNull())
        } finally { first.close() }
        val reopened = client()
        try {
            reopened.auth.awaitInitialization()
            assertEquals(AuthState.Unauthenticated, AuthRepository(reopened).authState.first())
        } finally { reopened.close() }
    }

    @Test fun signOutDoesNotRestorePriorOwnerWhenAnotherOwnerLogsIn() = runBlocking {
        val c = client()
        c.auth.awaitInitialization()
        try {
            c.auth.importSession(session("owner-a"), autoRefresh = false)
            try { AuthRepository(c).signOut() } catch (_: IOException) { }
            c.auth.importSession(session("owner-b"), autoRefresh = false)
        } finally { c.close() }
        val reopened = client()
        try {
            reopened.auth.awaitInitialization()
            assertEquals(AuthState.Authenticated("owner-b", "owner-b@example.com"), AuthRepository(reopened).authState.first())
        } finally { reopened.close() }
    }

    @Test fun temporaryRefreshFailureKeepsPersistedOwnerForLocalRecords() = runBlocking {
        val c = client()
        c.auth.awaitInitialization()
        try {
            c.auth.importSession(session("owner-a"), autoRefresh = false)
            c.auth.setSessionStatus(SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline"))))
            val state = AuthRepository(c).authState.first()
            assertTrue(state is AuthState.Authenticated)
            assertEquals("owner-a", (state as AuthState.Authenticated).userId)
        } finally { c.close() }
    }

    @Test fun clearedSessionRequiresLoginEvenAfterPreviousOwnerWasPersisted() = runBlocking {
        val c = client()
        c.auth.awaitInitialization()
        try {
            c.auth.importSession(session("owner-a"), autoRefresh = false)
            c.auth.clearSession()
            assertEquals(AuthState.Unauthenticated, AuthRepository(c).authState.first())
        } finally { c.close() }
    }
}
