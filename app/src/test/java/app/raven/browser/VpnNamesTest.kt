package app.raven.browser

import app.raven.browser.engine.RavenVpn
import org.junit.Assert.assertEquals
import org.junit.Test

/** The country Raven reads from a Proton VPN file's name. */
class VpnNamesTest {
    @Test fun protonNames() {
        assertEquals("NL", RavenVpn.countryOf("phone-NL-87"))
        assertEquals("US", RavenVpn.countryOf("wg-US-FREE-28"))
        assertEquals("US", RavenVpn.countryOf("US-NY#12"))
        // Secure Core: through Switzerland, out in the Netherlands.
        assertEquals("NL", RavenVpn.countryOf("CH-NL#1"))
        assertEquals("GB", RavenVpn.countryOf("raven-UK-12"))
        assertEquals("JP", RavenVpn.countryOf("Raven JP 3"))
        assertEquals(null, RavenVpn.countryOf("my laptop"))
    }

    /** A site's own country is kept for the site, whatever part of it you're on. */
    @Test fun sites() {
        assertEquals("youtube.com", RavenVpn.siteOf("m.youtube.com"))
        assertEquals("youtube.com", RavenVpn.siteOf("www.youtube.com"))
        assertEquals("youtube.com", RavenVpn.siteOf("youtube.com"))
        assertEquals("bbc.co.uk", RavenVpn.siteOf("www.bbc.co.uk"))
        assertEquals("example.com.au", RavenVpn.siteOf("shop.example.com.au"))
        assertEquals("netflix.com", RavenVpn.siteOf("WWW.Netflix.com"))
    }
}
