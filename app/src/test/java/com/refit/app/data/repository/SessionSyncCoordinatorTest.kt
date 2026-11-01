package com.refit.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.refit.app.data.local.ReFitDatabase
import com.refit.app.data.local.RehabSessionEntity
import com.refit.app.data.model.SyncStatus
import com.refit.app.data.remote.RehabSessionRemoteDataSource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionSyncCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val other = "22222222-2222-2222-2222-222222222222"
    private val remoteId = "33333333-3333-3333-3333-333333333333"
    private val headers = headersOf("Content-Type", "application/json")

    private fun record(user: String = owner, status: SyncStatus = SyncStatus.LOCAL_ONLY) = RehabSessionEntity(
        userId = user, createdAt = 1700000000000, updatedAt = 1700000000000,
        exerciseName = "손목 폄 운동", durationSeconds = 5, sampleCount = 100,
        rms = 2f, mav = 1f, peakActivation = 4f, syncStatus = status
    )
    private fun body(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonArray.single().jsonObject
    private fun database() = Room.inMemoryDatabaseBuilder(context, ReFitDatabase::class.java).build()
    private suspend fun client(engine: MockEngine): SupabaseClient = createSupabaseClient("https://test.supabase.co", "public-test-key") {
        httpEngine = engine
        install(Auth) {
            enableLifecycleCallbacks = false
            alwaysAutoRefresh = false
            autoSaveToStorage = false
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
        }
        install(Postgrest)
    }.also {
        it.auth.awaitInitialization()
        it.auth.importSession(UserSession(
            accessToken = "owner-access", refreshToken = "owner-refresh", expiresIn = 3600,
            tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner)
        ), autoRefresh = false)
    }

    @Test(timeout = 30000) fun symptomEditDuringUploadStaysPendingAndIsIncludedOnRetry() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val id = dao.insert(record())
        var requests = 0
        val c = client(MockEngine { request ->
            requests++
            if (requests == 1) dao.updatePain(id, owner, "많이 아파요", "불편했어요")
            else assertEquals("많이 아파요", body(request)["pain_response"]!!.jsonPrimitive.content)
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertFalse(sync.syncSession(owner, id))
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(id, owner)!!.syncStatus)
            assertTrue(sync.syncSession(owner, id))
            dao.updateFatigue(id, owner, "많이 힘듦")
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(id, owner)!!.syncStatus)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun rawUploadFailureKeepsEncryptedCapturePendingAndRetriesSameSession() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val id = dao.insert(record().copy(dataMode = "demo", rawEmg = com.refit.app.data.model.RawEmgCapture(
            50.0, "emg", "arbitrary", listOf(1f, 2f), 2, false)))
        var signalRequests = 0
        val paths = mutableListOf<String>()
        val c = client(MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("rehab_session_signals")) {
                signalRequests++
                assertEquals("session_id", request.url.parameters["on_conflict"])
                assertEquals(remoteId, body(request)["session_id"]!!.jsonPrimitive.content)
                if (signalRequests == 1) throw IOException("raw upload interrupted")
                respond("", HttpStatusCode.Created, headers)
            } else respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertFalse(sync.syncSession(owner, id))
            assertEquals(SyncStatus.FAILED, dao.getById(id, owner)!!.syncStatus)
            assertEquals(listOf(1f, 2f), dao.getById(id, owner)!!.rawEmg!!.samples)
            assertTrue(sync.syncSession(owner, id))
            assertEquals(listOf("/rest/v1/rehab_sessions", "/rest/v1/rehab_session_signals",
                "/rest/v1/rehab_sessions", "/rest/v1/rehab_session_signals"), paths)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun uploadsSummaryThenMarksSyncedAndSkipsConfirmedRow() = runBlocking {
        val db = database()
        val dao = db.rehabSessionDao()
        val saved = record()
        val id = dao.insert(saved)
        var requests = 0
        val c = client(MockEngine { request ->
            requests++
            assertEquals(SyncStatus.SYNCING, dao.getById(id, owner)!!.syncStatus)
            assertEquals("/rest/v1/rehab_sessions", request.url.encodedPath)
            assertEquals("user_id,client_local_id", request.url.parameters["on_conflict"])
            assertEquals("id", request.url.parameters["select"])
            assertTrue(request.headers["Prefer"]!!.contains("resolution=merge-duplicates"))
            assertTrue(request.headers["Prefer"]!!.contains("return=representation"))
            assertEquals("Bearer owner-access", request.headers["Authorization"])
            val payload = body(request)
            assertTrue(payload.keys.containsAll(setOf("user_id", "client_local_id", "created_at", "exercise_type", "duration_seconds", "sample_count", "rms", "mav", "peak_activation", "updated_at")))
            assertEquals(saved.userId, payload["user_id"]!!.jsonPrimitive.content)
            assertEquals(saved.clientLocalId, payload["client_local_id"]!!.jsonPrimitive.content)
            assertEquals(4f, payload["peak_activation"]!!.jsonPrimitive.float, 0f)
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.Created, headers)
        })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertTrue(sync.syncSession(owner, id))
            val row = dao.getById(id, owner)!!
            assertEquals(SyncStatus.SYNCED, row.syncStatus)
            assertEquals(remoteId, row.remoteId)
            assertEquals(saved.updatedAt, row.updatedAt)
            assertTrue(sync.syncSession(owner, id))
            assertEquals(1, requests)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun lostAcknowledgementRetainsLocalDataAndRetriesIdenticalSummary() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val id = dao.insert(record())
        val payloads = mutableListOf<JsonObject>()
        val c = client(MockEngine { request ->
            payloads += body(request)
            if (payloads.size == 1) throw IOException("response lost after server commit")
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertFalse(sync.syncSession(owner, id))
            assertEquals(SyncStatus.FAILED, dao.getById(id, owner)!!.syncStatus)
            assertEquals(100, dao.getById(id, owner)!!.sampleCount)
            sync.syncPending(owner)
            assertEquals(SyncStatus.SYNCED, dao.getById(id, owner)!!.syncStatus)
            assertEquals(payloads[0], payloads[1])
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun interruptedSyncingRowsAreEligibleAfterRestart() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val id = dao.insert(record(status = SyncStatus.SYNCING))
        val c = client(MockEngine { respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers) })
        try {
            SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c)).syncPending(owner)
            assertEquals(SyncStatus.SYNCED, dao.getById(id, owner)!!.syncStatus)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun cancelledUploadPropagatesCancellationAndLeavesRetryableRecord() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val id = dao.insert(record())
        val started = CompletableDeferred<Unit>()
        var block = true
        val c = client(MockEngine {
            if (block) { started.complete(Unit); awaitCancellation() }
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            val job = async { sync.syncSession(owner, id) }
            started.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(SyncStatus.FAILED, dao.getById(id, owner)!!.syncStatus)
            block = false
            sync.syncPending(owner)
            assertEquals(SyncStatus.SYNCED, dao.getById(id, owner)!!.syncStatus)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun foreignOwnerAndLoggedOutRequestsNeverReachHttp() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val foreign = dao.insert(record(other)); val own = dao.insert(record())
        var requests = 0
        val c = client(MockEngine { requests++; respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers) })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertFalse(sync.syncSession(owner, foreign))
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(foreign, other)!!.syncStatus)
            c.auth.clearSession()
            sync.syncPending(owner)
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(own, owner)!!.syncStatus)
            assertEquals(0, requests)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun overlappingCoordinatorsUploadOneRowOnce() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao(); val id = dao.insert(record())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var requests = 0
        val c = client(MockEngine {
            requests++; started.complete(Unit); release.await()
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            val first = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            val second = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            val a = async { first.syncSession(owner, id) }; started.await()
            val b = async { second.syncSession(owner, id) }
            delay(100)
            release.complete(Unit)
            assertTrue(a.await()); assertTrue(b.await())
            assertEquals(1, requests)
        } finally { release.complete(Unit); c.close(); db.close() }
    }

    @Test(timeout = 30000) fun oneServerFailureDoesNotBlockRemainingOwnedRows() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        val first = dao.insert(record()); val second = dao.insert(record()); val foreign = dao.insert(record(other))
        var requests = 0
        val c = client(MockEngine {
            requests++
            if (requests == 1) respond("""{"message":"unavailable"}""", HttpStatusCode.ServiceUnavailable, headers)
            else respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c)).syncPending(owner)
            assertEquals(SyncStatus.FAILED, dao.getById(first, owner)!!.syncStatus)
            assertEquals(SyncStatus.SYNCED, dao.getById(second, owner)!!.syncStatus)
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(foreign, other)!!.syncStatus)
            assertEquals(2, requests)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun logoutDuringBatchPreventsNextRowUpload() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao()
        dao.insert(record()); val second = dao.insert(record()); var requests = 0
        lateinit var c: SupabaseClient
        c = client(MockEngine {
            requests++; c.auth.clearSession()
            respond("""[{"id":"$remoteId"}]""", HttpStatusCode.OK, headers)
        })
        try {
            SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c)).syncPending(owner)
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(second, owner)!!.syncStatus)
            assertEquals(1, requests)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun cancellationIsRethrownRatherThanReportedAsCloudFailure() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao(); val id = dao.insert(record())
        val c = client(MockEngine { throw CancellationException("request cancelled") })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            try { sync.syncSession(owner, id); fail("Cancellation must reach caller") }
            catch (_: CancellationException) { }
            assertEquals(SyncStatus.FAILED, dao.getById(id, owner)!!.syncStatus)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun malformedAcknowledgementNeverMarksRowSynced() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao(); val id = dao.insert(record())
        val c = client(MockEngine { respond("[{\"id\":\"\"}]", HttpStatusCode.OK, headers) })
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(c))
            assertFalse(sync.syncSession(owner, id))
            assertEquals(SyncStatus.FAILED, dao.getById(id, owner)!!.syncStatus)
        } finally { c.close(); db.close() }
    }

    @Test(timeout = 30000) fun missingConfigurationLeavesRecordLocal() = runBlocking {
        val db = database(); val dao = db.rehabSessionDao(); val id = dao.insert(record())
        try {
            val sync = SessionSyncCoordinator(RehabSessionRepository(dao), RehabSessionRemoteDataSource(null))
            assertFalse(sync.syncSession(owner, id))
            sync.syncPending(owner)
            assertEquals(SyncStatus.LOCAL_ONLY, dao.getById(id, owner)!!.syncStatus)
        } finally { db.close() }
    }
}

