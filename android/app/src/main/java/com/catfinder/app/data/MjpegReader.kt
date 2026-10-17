package com.catfinder.app.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Splits a multipart/x-mixed-replace MJPEG body into JPEG frames. Relies on the
 * Content-Length header the ESP writes per part; boundary and other header
 * lines are skipped.
 */
class MjpegReader(input: InputStream) {
    private val input = BufferedInputStream(input, 64 * 1024)

    /** X-Timestamp of the last frame (ESP ms since capture), null if the firmware sends none. */
    var timestamp: Long? = null
        private set

    /** The next frame, or null at end of stream. */
    fun nextFrame(): ByteArray? {
        var length = -1
        timestamp = null
        while (true) {
            val line = readLine() ?: return null
            if (line.isEmpty()) {
                if (length >= 0) break else continue
            }
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                length = line.substring(colon + 1).trim().toIntOrNull()
                    ?.takeIf { it in 1..MAX_FRAME }
                    ?: throw IOException("bad Content-Length: $line")
            } else if (colon > 0 && line.substring(0, colon).trim().equals("X-Timestamp", ignoreCase = true)) {
                timestamp = line.substring(colon + 1).trim().toLongOrNull()
            }
        }
        val frame = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(frame, off, length - off)
            if (n < 0) return null
            off += n
        }
        return frame
    }

    private fun readLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.trimEnd('\r').toString()
            sb.append(c.toChar())
            if (sb.length > 1024) throw IOException("header line too long")
        }
    }

    companion object {
        const val MAX_FRAME = 4 * 1024 * 1024
    }
}
