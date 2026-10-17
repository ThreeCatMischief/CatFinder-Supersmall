package com.catfinder.app

import com.catfinder.app.data.MjpegReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class MjpegReaderTest {
    // Same framing as streamHandler in src/Web.cpp.
    private fun part(out: ByteArrayOutputStream, jpeg: ByteArray) {
        out.write("\r\n--catfinderframe\r\n".toByteArray())
        out.write("Content-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray())
        out.write(jpeg)
    }

    @Test fun readsConsecutiveFramesIncludingCrLfInPayload() {
        val a = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 13, 10, 13, 10, 0xFF.toByte(), 0xD9.toByte())
        val b = ByteArray(100_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        part(out, a)
        part(out, b)
        val r = MjpegReader(ByteArrayInputStream(out.toByteArray()))
        assertArrayEquals(a, r.nextFrame())
        assertArrayEquals(b, r.nextFrame())
        assertNull(r.nextFrame())
    }

    @Test fun truncatedFrameIsEndOfStream() {
        val out = ByteArrayOutputStream()
        out.write("--catfinderframe\r\nContent-Length: 10\r\n\r\nabc".toByteArray())
        assertNull(MjpegReader(ByteArrayInputStream(out.toByteArray())).nextFrame())
    }

    @Test(expected = IOException::class) fun badLengthThrows() {
        MjpegReader(ByteArrayInputStream("Content-Length: x\r\n\r\n".toByteArray())).nextFrame()
    }

    @Test fun readsTimestampHeader() {
        val out = ByteArrayOutputStream()
        out.write("\r\n--catfinderframe\r\nContent-Type: image/jpeg\r\nContent-Length: 3\r\nX-Timestamp: 123456\r\n\r\nabc".toByteArray())
        part(out, byteArrayOf(1, 2))
        val r = MjpegReader(ByteArrayInputStream(out.toByteArray()))
        assertArrayEquals("abc".toByteArray(), r.nextFrame())
        assertEquals(123456L, r.timestamp)
        r.nextFrame()
        assertNull(r.timestamp)
    }
}
