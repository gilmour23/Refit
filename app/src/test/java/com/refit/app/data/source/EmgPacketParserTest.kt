package com.refit.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmgPacketParserTest {

    @Test
    fun int16LittleEndianParserPreservesSignedSamplesAndScale() {
        val parser = Int16LittleEndianEmgPacketParser(scale = 0.5f)

        val samples = parser.parse(
            receivedAtMillis = 1_000L,
            payload = byteArrayOf(
                0xFE.toByte(), 0xFF.toByte(),
                0x04, 0x00
            )
        )

        assertEquals(listOf(-1f, 2f), samples.map { it.value })
    }

    @Test
    fun int16LittleEndianParserRejectsOddByteCount() {
        val parser = Int16LittleEndianEmgPacketParser()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                receivedAtMillis = 1_000L,
                payload = byteArrayOf(0x01)
            )
        }
    }
}
