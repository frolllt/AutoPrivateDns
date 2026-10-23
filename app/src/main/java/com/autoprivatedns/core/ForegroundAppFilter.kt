package com.autoprivatedns.core

/**
 * Pure decision logic for interpreting foreground window package transitions.
 *
 * When a user is inside a selected app and opens the soft keyboard, adjusts volume,
 * or gets a system permission prompt, Android emits TYPE_WINDOW_STATE_CHANGED for
 * those transient system/IME packages. Ignoring them prevents Private DNS from
 * falsely turning off while the user is still inside their chosen app.
 */
object ForegroundAppFilter {

    private val IGNORED_SYSTEM_OVERLAY_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.app.cocktailbarservice",
        "com.miui.securitycenter"
    )

    enum class DnsAction {
        /** Turn Private DNS ON for the selected foreground app */
        ENABLE_DNS,
        /** Turn Private DNS OFF because the user left the selected app */
        DISABLE_DNS,
        /** Ignore transient window change (e.g. keyboard, volume panel, notification shade) */
        IGNORE
    }

    fun evaluateTransition(
        newPackageName: String?,
        selectedPackages: Set<String>,
        enabledInputMethodPackages: Set<String>,
        isMasterEnabled: Boolean
    ): DnsAction {
        if (!isMasterEnabled) {
            return DnsAction.DISABLE_DNS
        }

        val pkg = newPackageName?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return DnsAction.IGNORE
        }

        // If the user explicitly selected a package, always honor it first
        if (pkg in selectedPackages) {
            return DnsAction.ENABLE_DNS
        }

        // Ignore keyboards (IMEs) and transient system overlays so they don't
        // interrupt Private DNS while the user is still using a selected app
        if (pkg in enabledInputMethodPackages || pkg in IGNORED_SYSTEM_OVERLAY_PACKAGES) {
            return DnsAction.IGNORE
        }

        // Any other real app or home screen launcher means the user closed/left the selected app
        return DnsAction.DISABLE_DNS
    }
}
