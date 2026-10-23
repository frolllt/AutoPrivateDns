package com.autoprivatedns

import com.autoprivatedns.core.ForegroundAppFilter
import com.autoprivatedns.core.ForegroundAppFilter.DnsAction
import com.autoprivatedns.data.DnsPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsAndFilterTest {

    @Test
    fun `sanitizeHostname strips protocols, ports, and paths`() {
        assertEquals(
            "dns.adguard-dns.com",
            DnsPreferencesRepository.sanitizeHostname("  tls://dns.adguard-dns.com/dns-query  ")
        )
        assertEquals(
            "abc123.dns.nextdns.io",
            DnsPreferencesRepository.sanitizeHostname("https://abc123.dns.nextdns.io:853/path")
        )
    }

    @Test
    fun `isValidHostname validates RFC 1123 hostnames`() {
        assertTrue(DnsPreferencesRepository.isValidHostname("dns.adguard-dns.com"))
        assertTrue(DnsPreferencesRepository.isValidHostname("one.one.one.one"))
        assertTrue(DnsPreferencesRepository.isValidHostname("1dot1dot1dot1.cloudflare-dns.com"))

        assertFalse(DnsPreferencesRepository.isValidHostname(""))
        assertFalse(DnsPreferencesRepository.isValidHostname("localhost"))
        assertFalse(DnsPreferencesRepository.isValidHostname("-invalid.com"))
        assertFalse(DnsPreferencesRepository.isValidHostname("invalid-.com"))
    }

    @Test
    fun `evaluateTransition enables DNS when selected app opens and ignores keyboard and system overlays`() {
        val selectedApps = setOf("com.android.chrome", "com.spotify.music")
        val imePackages = setOf("com.google.android.inputmethod.latin")

        // 1. Opening a selected app enables Private DNS
        assertEquals(
            DnsAction.ENABLE_DNS,
            ForegroundAppFilter.evaluateTransition(
                newPackageName = "com.android.chrome",
                selectedPackages = selectedApps,
                enabledInputMethodPackages = imePackages,
                isMasterEnabled = true
            )
        )

        // 2. Opening the keyboard while inside Chrome is ignored (keeps Private DNS ON)
        assertEquals(
            DnsAction.IGNORE,
            ForegroundAppFilter.evaluateTransition(
                newPackageName = "com.google.android.inputmethod.latin",
                selectedPackages = selectedApps,
                enabledInputMethodPackages = imePackages,
                isMasterEnabled = true
            )
        )

        // 3. System UI overlay (volume slider / notification shade) is ignored
        assertEquals(
            DnsAction.IGNORE,
            ForegroundAppFilter.evaluateTransition(
                newPackageName = "com.android.systemui",
                selectedPackages = selectedApps,
                enabledInputMethodPackages = imePackages,
                isMasterEnabled = true
            )
        )

        // 4. Going back to the Home launcher or switching to an unselected app disables Private DNS
        assertEquals(
            DnsAction.DISABLE_DNS,
            ForegroundAppFilter.evaluateTransition(
                newPackageName = "com.google.android.apps.nexuslauncher",
                selectedPackages = selectedApps,
                enabledInputMethodPackages = imePackages,
                isMasterEnabled = true
            )
        )

        // 5. When master toggle is off, always disable
        assertEquals(
            DnsAction.DISABLE_DNS,
            ForegroundAppFilter.evaluateTransition(
                newPackageName = "com.android.chrome",
                selectedPackages = selectedApps,
                enabledInputMethodPackages = imePackages,
                isMasterEnabled = false
            )
        )
    }
}
