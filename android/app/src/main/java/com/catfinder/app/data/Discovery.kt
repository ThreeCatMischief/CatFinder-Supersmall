package com.catfinder.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the robot on the local network through mDNS: the firmware announces
 * `_catfinder._tcp` on port 80. NsdManager needs no permission beyond INTERNET.
 */
object Discovery {
    private const val SERVICE_TYPE = "_catfinder._tcp."
    const val TIMEOUT_MS = 8_000L

    /** The IPv4 address of the first robot that answers within [timeoutMs], or null. */
    @Suppress("DEPRECATION") // resolveService is deprecated on API 34; it is the only call on API 26-33.
    suspend fun find(context: Context, timeoutMs: Long = TIMEOUT_MS): String? {
        val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        return withTimeoutOrNull(timeoutMs) {
            val result = CompletableDeferred<String?>()
            // Before API 34 only one resolve may run at a time; try the next service if one fails.
            val resolving = AtomicBoolean(false)

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val ip = ipv4(serviceInfo)
                    if (ip != null) result.complete(ip) else resolving.set(false)
                }
            }

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (result.isCompleted || !resolving.compareAndSet(false, true)) return
                    try {
                        nsd.resolveService(serviceInfo, resolveListener)
                    } catch (e: RuntimeException) {
                        resolving.set(false)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    result.complete(null)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            try {
                try {
                    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                } catch (e: RuntimeException) {
                    return@withTimeoutOrNull null
                }
                result.await()
            } finally {
                // Throws if discovery never started; nothing to stop then.
                runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            }
        }
    }

    @Suppress("DEPRECATION") // NsdServiceInfo.host is deprecated on API 34; hostAddresses replaces it.
    private fun ipv4(info: NsdServiceInfo): String? {
        val addresses: List<InetAddress> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) info.hostAddresses
            else listOfNotNull(info.host)
        return addresses.firstOrNull { it is Inet4Address }?.hostAddress
    }
}
