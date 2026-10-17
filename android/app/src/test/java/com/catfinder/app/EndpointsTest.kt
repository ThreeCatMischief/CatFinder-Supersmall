package com.catfinder.app

import com.catfinder.app.data.EndpointRules
import com.catfinder.app.data.Endpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointsTest {
    private fun ep(address: String) = EndpointRules.from(address)

    @Test fun bareIpGetsHttpAndStreamPort() {
        assertEquals(
            Endpoints("http://192.168.1.50", "http://192.168.1.50:81/stream"),
            ep("192.168.1.50"),
        )
    }

    @Test fun hostnameIsTrimmedAndTrailingSlashDropped() {
        assertEquals(
            Endpoints("http://catfinder.local", "http://catfinder.local:81/stream"),
            ep(" catfinder.local/ "),
        )
    }

    @Test fun httpKeepsPortInBaseButNotInStream() {
        assertEquals(
            Endpoints("http://10.0.0.5:8080", "http://10.0.0.5:81/stream"),
            ep("http://10.0.0.5:8080"),
        )
    }

    @Test fun bareHostWithPortKeepsPortInBase() {
        assertEquals(
            Endpoints("http://10.0.0.5:8080", "http://10.0.0.5:81/stream"),
            ep("10.0.0.5:8080"),
        )
    }

    @Test fun httpsServesStreamOnTheSameOrigin() {
        assertEquals(
            Endpoints("https://robot.example.net:8443", "https://robot.example.net:8443/stream"),
            ep("https://robot.example.net:8443/"),
        )
    }

    @Test fun httpsWithoutPort() {
        assertEquals(
            Endpoints("https://robot.example.net", "https://robot.example.net/stream"),
            ep("https://robot.example.net"),
        )
    }

    @Test fun httpAddressIsUsedAsGiven() {
        assertEquals(
            Endpoints("http://catfinder.local", "http://catfinder.local:81/stream"),
            ep("http://catfinder.local"),
        )
    }

    @Test fun severalTrailingSlashesAreDropped() {
        assertEquals("http://192.168.1.50", ep("192.168.1.50///")?.base)
    }

    @Test fun blankIsNull() {
        assertNull(ep(""))
        assertNull(ep("   "))
        assertNull(ep("/"))
    }

    @Test fun unparseableIsNull() {
        assertNull(ep("not an address"))
        assertNull(ep("http://"))
        assertNull(ep("ftp://192.168.1.50"))
        assertNull(ep("192.168.1.50:abc"))
    }
}
