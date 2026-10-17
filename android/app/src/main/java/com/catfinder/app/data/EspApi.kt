package com.catfinder.app.data

import com.catfinder.app.drive.Wheels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/** The robot answered, but with an error. */
class ApiException(val status: Int, message: String) : Exception(message)

/** Nothing answered. */
class OfflineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Client for the ESP's HTTP interface. Requests go to [Endpoints.base], the video
 * to [Endpoints.stream]. Responses are read to the end and the connection is not
 * disconnected, so HttpURLConnection keeps it alive for the next /drive.
 */
class EspApi(endpoints: Endpoints) {
    private val base = endpoints.base
    private val streamUrl = endpoints.stream

    /** Returns the ESP's millis() at receipt, or null for firmware that does not send it. */
    suspend fun drive(w: Wheels, seq: Long): Long? =
        request("GET", "/drive?l=${w.left}&r=${w.right}&seq=$seq", connectTimeout = 2_000, readTimeout = 1_500)
            .removePrefix("ok").trim().toLongOrNull()

    /** Sets the stream quality level (0 = best); returns the level the ESP applied. */
    suspend fun setVideoLevel(level: Int): Int =
        JSONObject(request("GET", "/video?level=$level", connectTimeout = 2_000, readTimeout = 2_000)).getInt("level")

    suspend fun status(): JSONObject = JSONObject(request("GET", "/status"))

    suspend fun log(): String = request("GET", "/log")

    suspend fun driveConfig(): JSONObject = JSONObject(request("GET", "/drive_cfg"))

    /** Form POST of any subset of the /drive_cfg keys; returns the stored result. */
    suspend fun saveDriveConfig(fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return JSONObject(request("POST", "/drive_cfg", body.toByteArray()))
    }

    /** Opens the MJPEG stream. The caller reads and disconnects it. */
    fun openStream(): HttpURLConnection {
        val c = open(streamUrl)
        c.connectTimeout = 5_000
        c.readTimeout = 5_000
        val status = try {
            c.responseCode
        } catch (e: IOException) {
            c.disconnect()
            throw offline(e)
        }
        if (status >= 400) {
            val text = runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: ""
            c.disconnect()
            throw error(status, text)
        }
        return c
    }

    private suspend fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
        connectTimeout: Int = 5_000,
        readTimeout: Int = 5_000,
    ): String = withContext(Dispatchers.IO) {
        val c = open(base + path)
        try {
            c.requestMethod = method
            c.connectTimeout = connectTimeout
            c.readTimeout = readTimeout
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val text = (if (status < 400) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (status >= 400) throw error(status, text)
            text
        } catch (e: IOException) {
            throw offline(e)
        }
    }

    private fun open(url: String): HttpURLConnection = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: IOException) {
        throw OfflineException("Invalid address", e)
    }

    companion object {
        fun error(status: Int, text: String = ""): ApiException {
            // A proxy in front of the robot may answer with an HTML error page; that is no message.
            val body = text.trim().take(120).takeUnless { it.startsWith("<") } ?: ""
            return ApiException(status, body.ifBlank { "CatFinder error $status" })
        }

        fun offline(e: IOException): OfflineException =
            if (e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) != true) {
                // Connected, but no answer in time.
                OfflineException("CatFinder answers too slowly", e)
            } else {
                OfflineException("CatFinder not reachable. Same WiFi as the robot? Away from home: is Tailscale on?", e)
            }
    }
}
