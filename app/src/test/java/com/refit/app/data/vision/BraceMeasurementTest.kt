package com.refit.app.data.vision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ServerSocket
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

class BraceMeasurementTest {
    private fun response(dim3: String = "57.2", ok: Boolean = true): String = """
        {"ok":$ok,"session_id":"test-01","message":"카드를 찾지 못했어요",
        "dims_mm":{"1":53.8,"2":33.2,"3":$dim3,"4":38.8,"5":93.1,"6":43.4,"7":37.4,"8":93.5,"9":64.4,"10":34.9,"11":25.8},
        "warnings":["촬영 각도를 확인하세요"],"reference":{"top":"card","side":"card"}}
    """.trimIndent()

    @Test fun completeAutomaticResultKeepsAllDimensionsAndWarnings() {
        val result = BraceMeasurement.parse(response())
        assertEquals("test-01", result.sessionId)
        assertEquals(11, result.dimensions.size)
        assertEquals(53.8, result.dimensions.first().valueMm, 0.001)
        assertEquals(25.8, result.dimensions.last().valueMm, 0.001)
        assertEquals(listOf("촬영 각도를 확인하세요"), result.warnings)
    }

    @Test fun missingInvalidOrFailedMeasurementsCannotBecomeComplete() {
        for (value in listOf("null", "0", "-2", "\"NaN\"")) {
            try { BraceMeasurement.parse(response(dim3 = value)); fail("Accepted dimension $value") }
            catch (_: IOException) { }
        }
        try { BraceMeasurement.parse(response(ok = false)); fail("Accepted failed recognition") }
        catch (e: IOException) { assertTrue(e.message!!.contains("카드")) }
    }

    @Test fun uploadPreservesOriginalBytesAndRequestsAutomaticMeasurement() = runBlocking {
        val rawTop = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 1, 17, 0xFF.toByte())
        val server = TestHttpServer(200, response())
        try {
            val result = BraceMeasurementClient(allowHttp = true).measure("http://127.0.0.1:${server.port}",
                MeasurementPhoto("top.jpg", "image/jpeg") { ByteArrayInputStream(rawTop) },
                MeasurementPhoto("side.png", "image/png") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
            assertEquals(11, result.dimensions.size)
            val received = server.received
            assertTrue(received.toList().windowed(rawTop.size).any { it == rawTop.toList() })
            val text = received.toString(Charsets.ISO_8859_1)
            assertTrue(text.contains("name=\"top_image\""))
            assertTrue(text.contains("name=\"side_image\""))
            assertTrue(text.contains("name=\"return_report\""))
            assertFalse(text.contains("card_top"))
            assertFalse(text.contains("wrist_top"))
        } finally { server.close() }
    }

    @Test fun authenticatedMeasurementSendsKeyOnlyAsAuthorizationHeader() = runBlocking {
        val server = TestHttpServer(200, response())
        val token = "C".repeat(48)
        try {
            val photo = MeasurementPhoto("top.jpg", "image/jpeg") { ByteArrayInputStream(byteArrayOf(1)) }
            BraceMeasurementClient(allowHttp = true, requireToken = true).measure("http://127.0.0.1:${server.port}", photo, photo, token)
            assertTrue(server.requestHeaders.any { it.equals("Authorization: Bearer $token", true) })
            assertFalse(server.received.toString(Charsets.UTF_8).contains(token))
        } finally { server.close() }
    }

    @Test fun failedServerDoesNotReturnAnExampleMeasurement() = runBlocking {
        val server = TestHttpServer(503, "")
        try {
            val photo = MeasurementPhoto("top.jpg", "image/jpeg") { ByteArrayInputStream(byteArrayOf(1)) }
            try {
                BraceMeasurementClient(allowHttp = true).measure("http://127.0.0.1:${server.port}", photo, photo)
                fail("Server failure became success")
            } catch (_: IOException) { }
        } finally { server.close() }
    }
}

/** Real loopback HTTP boundary; no Android/JDK http-server module is required. */
internal class TestHttpServer(private val status: Int, private val response: String) : AutoCloseable {
    private val socket = ServerSocket(0)
    val port: Int get() = socket.localPort
    @Volatile var received = byteArrayOf()
    @Volatile var requestHeaders = emptyList<String>()
    private val worker = Thread {
        socket.accept().use { client ->
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream())
            fun line(): String {
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val b = input.read()
                    if (b < 0 || b == 10) break
                    if (b != 13) bytes.write(b)
                }
                return bytes.toString("ISO-8859-1")
            }
            val headers = mutableListOf<String>()
            while (true) { val header = line(); if (header.isEmpty()) break; headers += header }
            requestHeaders = headers.toList()
            val body = ByteArrayOutputStream()
            if (headers.any { it.equals("Transfer-Encoding: chunked", true) }) {
                while (true) {
                    val length = line().substringBefore(';').toInt(16)
                    if (length == 0) { line(); break }
                    repeat(length) { body.write(input.read()) }; line()
                }
            } else {
                val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                repeat(length) { body.write(input.read()) }
            }
            received = body.toByteArray()
            val data = response.toByteArray()
            client.getOutputStream().use { out ->
                out.write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(data); out.flush()
            }
        }
    }.apply { isDaemon = true; start() }
    override fun close() { socket.close(); worker.join(1000) }
}

