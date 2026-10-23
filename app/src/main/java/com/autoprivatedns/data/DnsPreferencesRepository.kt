package com.autoprivatedns.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DnsConfig(
    val isMasterEnabled: Boolean = true,
    val dnsHostname: String = DEFAULT_DNS_HOSTNAME,
    val selectedPackages: Set<String> = emptySet(),
    val lastActiveTriggerPackage: String? = null
) {
    companion object {
        const val DEFAULT_DNS_HOSTNAME = "dns.adguard-dns.com"
    }
}

class DnsPreferencesRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<DnsConfig> = _config.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = loadConfig()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadConfig(): DnsConfig {
        val enabled = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        val hostname = prefs.getString(KEY_DNS_HOSTNAME, DnsConfig.DEFAULT_DNS_HOSTNAME)
            ?: DnsConfig.DEFAULT_DNS_HOSTNAME
        val packages = prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val lastPkg = prefs.getString(KEY_LAST_TRIGGER_PACKAGE, null)
        return DnsConfig(
            isMasterEnabled = enabled,
            dnsHostname = hostname,
            selectedPackages = packages,
            lastActiveTriggerPackage = lastPkg
        )
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _config.value = _config.value.copy(isMasterEnabled = enabled)
    }

    fun setDnsHostname(rawHostname: String) {
        val sanitized = sanitizeHostname(rawHostname)
        prefs.edit().putString(KEY_DNS_HOSTNAME, sanitized).apply()
        _config.value = _config.value.copy(dnsHostname = sanitized)
    }

    fun togglePackageSelection(packageName: String) {
        val current = _config.value.selectedPackages.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, current).apply()
        _config.value = _config.value.copy(selectedPackages = current.toSet())
    }

    fun setLastActiveTriggerPackage(packageName: String?) {
        prefs.edit().putString(KEY_LAST_TRIGGER_PACKAGE, packageName).apply()
        _config.value = _config.value.copy(lastActiveTriggerPackage = packageName)
    }

    companion object {
        private const val PREFS_NAME = "auto_private_dns_prefs"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_DNS_HOSTNAME = "dns_hostname"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_LAST_TRIGGER_PACKAGE = "last_trigger_package"

        @Volatile
        private var INSTANCE: DnsPreferencesRepository? = null

        fun getInstance(context: Context): DnsPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DnsPreferencesRepository(context).also { INSTANCE = it }
            }
        }

        /**
         * Strips protocol prefixes (tls://, https://, quic://), ports, and paths
         * so Android's Settings.Global private_dns_specifier receives a pure hostname.
         */
        fun sanitizeHostname(input: String): String {
            return input.trim()
                .lowercase()
                .removePrefix("tls://")
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("quic://")
                .substringBefore("/")
                .substringBefore(":")
                .trim()
        }

        /**
         * Validates that a hostname conforms to RFC 1123 domain format expected by Android Private DNS.
         */
        fun isValidHostname(hostname: String): Boolean {
            val cleaned = sanitizeHostname(hostname)
            if (cleaned.isEmpty() || cleaned.length > 253 || !cleaned.contains(".")) {
                return false
            }
            val labelRegex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
            return cleaned.split(".").all { label -> label.matches(labelRegex) }
        }
    }
}
