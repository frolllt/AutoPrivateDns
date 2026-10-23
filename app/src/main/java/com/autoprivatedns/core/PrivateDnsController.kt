package com.autoprivatedns.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.autoprivatedns.data.DnsPreferencesRepository

data class SystemPrivateDnsStatus(
    val mode: String,
    val specifier: String
) {
    val isHostnameActive: Boolean
        get() = mode == PrivateDnsController.MODE_HOSTNAME && specifier.isNotBlank()

    val isOff: Boolean
        get() = mode == PrivateDnsController.MODE_OFF
}

object PrivateDnsController {
    private const val TAG = "PrivateDnsController"

    const val SETTING_PRIVATE_DNS_MODE = "private_dns_mode"
    const val SETTING_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    /**
     * Checks whether the app currently holds android.permission.WRITE_SECURE_SETTINGS.
     */
    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads the current system-wide Private DNS mode and hostname from Settings.Global.
     */
    fun getCurrentStatus(context: Context): SystemPrivateDnsStatus {
        return try {
            val resolver = context.contentResolver
            val mode = Settings.Global.getString(resolver, SETTING_PRIVATE_DNS_MODE) ?: MODE_OFF
            val specifier = Settings.Global.getString(resolver, SETTING_PRIVATE_DNS_SPECIFIER) ?: ""
            SystemPrivateDnsStatus(mode = mode, specifier = specifier)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Private DNS settings", e)
            SystemPrivateDnsStatus(mode = MODE_OFF, specifier = "")
        }
    }

    /**
     * Enables Private DNS with the specified hostname in Settings.Global.
     * Avoids redundant writes if the system is already set to the same hostname.
     */
    fun enablePrivateDns(context: Context, rawHostname: String): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "Cannot enable Private DNS: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        val hostname = DnsPreferencesRepository.sanitizeHostname(rawHostname)
        if (!DnsPreferencesRepository.isValidHostname(hostname)) {
            Log.w(TAG, "Cannot enable Private DNS: invalid hostname '$hostname'")
            return false
        }

        return try {
            val resolver = context.contentResolver
            val current = getCurrentStatus(context)
            if (current.mode != MODE_HOSTNAME || current.specifier != hostname) {
                Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_SPECIFIER, hostname)
                Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_MODE, MODE_HOSTNAME)
                Log.i(TAG, "Private DNS enabled -> $hostname")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException writing Private DNS settings", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error enabling Private DNS", e)
            false
        }
    }

    /**
     * Turns Private DNS completely OFF ("off") in Settings.Global.
     * Avoids redundant writes if Private DNS is already off.
     */
    fun disablePrivateDns(context: Context): Boolean {
        if (!hasWriteSecureSettingsPermission(context)) {
            Log.w(TAG, "Cannot disable Private DNS: WRITE_SECURE_SETTINGS not granted")
            return false
        }

        return try {
            val resolver = context.contentResolver
            val current = getCurrentStatus(context)
            if (current.mode != MODE_OFF) {
                Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_MODE, MODE_OFF)
                Log.i(TAG, "Private DNS disabled (off)")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disabling Private DNS", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error disabling Private DNS", e)
            false
        }
    }

    /**
     * Returns the exact ADB command the user can run once on a PC (or wireless ADB)
     * to grant WRITE_SECURE_SETTINGS to this app.
     */
    fun getAdbGrantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
    }

    /**
     * Optional helper for rooted devices to self-grant WRITE_SECURE_SETTINGS in one tap.
     */
    fun tryGrantPermissionWithRoot(context: Context): Boolean {
        return try {
            val cmd = "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()
            exitCode == 0 && hasWriteSecureSettingsPermission(context)
        } catch (e: Exception) {
            Log.d(TAG, "Root grant not available: ${e.message}")
            false
        }
    }
}
