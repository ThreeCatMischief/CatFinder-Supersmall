package com.catfinder.app.data

import java.net.URI

/** [base] serves /status, /drive and the rest; [stream] serves the MJPEG video. */
data class Endpoints(val base: String, val stream: String)

/**
 * Turns what the user typed into the two URLs the app needs.
 *
 * - `192.168.1.50`, `catfinder.local`: plain HTTP, stream on port 81.
 * - `http://host[:port]`: base as given, stream on port 81 of the same host.
 * - `https://host[:port]`: a reverse proxy that serves everything on one origin,
 *   so the stream is `/stream` below the base.
 *
 * IPv6 literals are not supported.
 */
object EndpointRules {
    private const val STREAM_PORT = 81
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun from(address: String): Endpoints? {
        val raw = address.trim()
        val prefix = SCHEME.find(raw)?.value
        val rest = raw.substring(prefix?.length ?: 0).trimEnd('/').trim()
        if (rest.isEmpty()) return null
        val base = (prefix ?: "http://") + rest
        val uri = try {
            URI(base)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        if (host.contains(':')) return null
        return when (scheme) {
            "http" -> Endpoints(base, "http://$host:$STREAM_PORT/stream")
            "https" -> Endpoints(base, "$base/stream")
            else -> null
        }
    }
}
