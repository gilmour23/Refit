package com.refit.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PatientCenterRemoteDataSourceTest {
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val headers = headersOf("Content-Type", "application/json")
    private suspend fun client(engine: MockEngine): SupabaseClient =
        createSupabaseClient("https://test.supabase.co", "public-test-key") {
            httpEngine = engine
            install(Auth) {
                enableLifecycleCallbacks = false; alwaysAutoRefresh = false; autoSaveToStorage = false
                sessionManager = MemorySessionManager(); codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Postgrest)
        }.also {
            it.auth.awaitInitialization()
            it.auth.importSession(UserSession(accessToken = "owner-access", refreshToken = "owner-refresh",
                expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner)), autoRefresh = false)
        }

    @Test fun inviteRequiresExplicitConsentAndCurrentAccountBeforeAnyRequest() = runBlocking {
        var calls = 0
        val c = client(MockEngine { calls++; error("No request permitted") })
        try {
            val remote = PatientCenterRemoteDataSource(c)
            assertTrue(runCatching { remote.acceptInvite(owner, "code", false) }.isFailure)
            assertTrue(runCatching { remote.acceptInvite("other", "code", true) }.isFailure)
            assertEquals(0, calls)
        } finally { c.close() }
    }

    @Test fun inviteSendsOnlyOpaqueCodeAndUsesSignedInPatient() = runBlocking {
        val c = client(MockEngine { request ->
            assertEquals("/rest/v1/rpc/accept_center_invite", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("p_code"), body.keys)
            assertEquals("opaque-code", body["p_code"]!!.jsonPrimitive.content)
            respond("""{"center_id":"center","patient_id":"$owner","assigned_therapist_id":null,"joined_at":"2026-11-01T00:00:00Z"}""",
                HttpStatusCode.OK, headers)
        })
        try { assertEquals(owner, PatientCenterRemoteDataSource(c).acceptInvite(owner, " opaque-code ", true).patientId) }
        finally { c.close() }
    }

    @Test fun revokedCenterHistoryNeverBecomesAnActivePlan() = runBlocking {
        var calls = 0
        val c = client(MockEngine { request ->
            calls++
            if (request.url.encodedPath.endsWith("profiles")) {
                assertEquals("eq.$owner", request.url.parameters["id"])
                respond("[]", HttpStatusCode.OK, headers)
            } else {
            assertEquals("/rest/v1/center_patients", request.url.encodedPath)
            assertEquals("eq.$owner", request.url.parameters["patient_id"])
            respond("""[{"center_id":"revoked","patient_id":"$owner","assigned_therapist_id":null,"joined_at":"2026-10-01T00:00:00Z","revoked_at":"2026-11-01T00:00:00Z"}]""",
                HttpStatusCode.OK, headers)
            }
        })
        try {
            val snapshot = PatientCenterRemoteDataSource(c).read(owner, LocalDate.parse("2026-11-01"))
            assertNull(snapshot.activePlan)
            assertEquals(2, calls)
        } finally { c.close() }
    }
    @Test fun displayNamePatchPreservesOmittedDemographicsAndOwnerFilter() = runBlocking {
        var writes = 0
        val c = client(MockEngine { request ->
            assertEquals("/rest/v1/profiles", request.url.encodedPath)
            assertEquals("eq.$owner", request.url.parameters["id"])
            if (request.method == HttpMethod.Get) {
                respond("""[{"id":"$owner"}]""", HttpStatusCode.OK, headers)
            } else {
                writes++
                assertEquals(HttpMethod.Patch, request.method)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(setOf("display_name"), body.keys)
                assertEquals("김민수", body["display_name"]!!.jsonPrimitive.content)
                respond("", HttpStatusCode.NoContent, headers)
            }
        })
        try {
            assertEquals("김민수", PatientCenterRemoteDataSource(c).saveDisplayName(owner, "  김민수  "))
            assertEquals(1, writes)
        } finally { c.close() }
    }

    @Test fun missingProfileInsertContainsOnlyOwnerAndExplicitDisplayName() = runBlocking {
        var writes = 0
        val c = client(MockEngine { request ->
            if (request.method == HttpMethod.Get) respond("[]", HttpStatusCode.OK, headers)
            else {
                writes++
                assertEquals(HttpMethod.Post, request.method)
                val element = Json.parseToJsonElement((request.body as TextContent).text)
                val body = if (element is JsonArray) element.single().jsonObject else element.jsonObject
                assertEquals(setOf("id", "display_name"), body.keys)
                assertEquals(owner, body["id"]!!.jsonPrimitive.content)
                assertEquals("김민수", body["display_name"]!!.jsonPrimitive.content)
                respond("", HttpStatusCode.Created, headers)
            }
        })
        try {
            PatientCenterRemoteDataSource(c).saveDisplayName(owner, "김민수")
            assertEquals(1, writes)
        } finally { c.close() }
    }

}
