package com.refit.app.auth

import android.content.Context
import com.refit.app.security.DeviceCipher
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class SessionStorageException : IllegalStateException("Could not update local login storage")

class EncryptedSessionManager(context: Context, supabaseUrl: String) : SessionManager {
    private val encrypted = context.getSharedPreferences("refit-secure-auth", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
    private val legacyKey = "sb-" + supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"
    private val cipher = DeviceCipher("refit.auth.v1", "refit:auth-session:v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveSession(session: UserSession) = locked {
        write(session)
        removeLegacy()
    }

    override suspend fun loadSession(): UserSession = locked {
        // This marker and encrypted-session deletion are one durable commit.
        // A kill before legacy cleanup must never restore a signed-out account.
        if (encrypted.getBoolean("signed_out", false)) {
            removeLegacy()
            throw NoSessionFoundException()
        }
        encrypted.getString("session", null)?.let {
            val session = json.decodeFromString(UserSession.serializer(), cipher.decrypt(it))
            removeLegacy() // Retry cleanup if migration was interrupted after its save.
            return@locked session
        }
        val old = legacy.getString(legacyKey, null) ?: legacy.getString("session", null)
            ?: throw NoSessionFoundException()
        val session = json.decodeFromString(UserSession.serializer(), old)
        write(session)
        removeLegacy()
        session
    }

    override suspend fun deleteSession() = locked {
        requireCommit(encrypted.edit().remove("session").putBoolean("signed_out", true).commit())
        removeLegacy()
    }

    private fun write(session: UserSession) {
        val value = cipher.encrypt(json.encodeToString(UserSession.serializer(), session))
        requireCommit(encrypted.edit().putString("session", value).putBoolean("signed_out", false).commit())
    }

    private fun removeLegacy() {
        requireCommit(legacy.edit().remove(legacyKey).remove("session").commit())
    }

    private fun requireCommit(succeeded: Boolean) { if (!succeeded) throw SessionStorageException() }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        storageLock.withLock { block() }
    }

    private companion object { val storageLock = Mutex() }
}
