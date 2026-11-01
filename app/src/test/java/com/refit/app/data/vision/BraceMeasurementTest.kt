package com.refit.app.data.vision

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetSocketAddress

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
        var received = byteArrayOf()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/measure_brace") { exchange ->
            received = exchange.requestBody.readBytes()
            val bytes = response().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = BraceMeasurementClient(allowHttp = true).measure("http://127.0.0.1:${server.address.port}",
                MeasurementPhoto("top.jpg", "image/jpeg") { ByteArrayInputStream(rawTop) },
                MeasurementPhoto("side.png", "image/png") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
            assertEquals(11, result.dimensions.size)
            assertTrue(received.toList().windowed(rawTop.size).any { it == rawTop.toList() })
            val text = received.toString(Charsets.ISO_8859_1)
            assertTrue(text.contains("name=\"top_image\""))
            assertTrue(text.contains("name=\"side_image\""))
            assertTrue(text.contains("name=\"return_report\""))
            assertFalse(text.contains("card_top"))
            assertFalse(text.contains("wrist_top"))
        } finally { server.stop(0) }
    }

    @Test fun failedServerDoesNotReturnAnExampleMeasurement() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/measure_brace") { exchange ->
            exchange.sendResponseHeaders(503, -1); exchange.close()
        }
        server.start()
        try {
            val photo = MeasurementPhoto("top.jpg", "image/jpeg") { ByteArrayInputStream(byteArrayOf(1)) }
            try {
                BraceMeasurementClient(allowHttp = true).measure("http://127.0.0.1:${server.address.port}", photo, photo)
                fail("Server failure became success")
            } catch (_: IOException) { }
        } finally { server.stop(0) }
    }
}
