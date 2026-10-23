package com.autoprivatedns.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.autoprivatedns.core.ForegroundAppFilter
import com.autoprivatedns.core.PrivateDnsController
import com.autoprivatedns.data.DnsPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppDetectorService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: DnsPreferencesRepository

    private var currentForegroundPackage: String? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned off -> disabling Private DNS")
                    PrivateDnsController.disablePrivateDns(this@AppDetectorService)
                    repository.setLastActiveTriggerPackage(null)
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Device unlocked -> re-evaluating foreground package: $currentForegroundPackage")
                    evaluatePackage(currentForegroundPackage)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AppDetectorService connected")
        repository = DnsPreferencesRepository.getInstance(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            repository.config.collectLatest { config ->
                if (!config.isMasterEnabled) {
                    PrivateDnsController.disablePrivateDns(this@AppDetectorService)
                    repository.setLastActiveTriggerPackage(null)
                } else {
                    evaluatePackage(currentForegroundPackage)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        evaluatePackage(packageName)
    }

    private fun evaluatePackage(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val config = repository.config.value
        val imePackages = getEnabledImePackages()

        val action = ForegroundAppFilter.evaluateTransition(
            newPackageName = packageName,
            selectedPackages = config.selectedPackages,
            enabledInputMethodPackages = imePackages,
            isMasterEnabled = config.isMasterEnabled
        )

        when (action) {
            ForegroundAppFilter.DnsAction.ENABLE_DNS -> {
                currentForegroundPackage = packageName
                val ok = PrivateDnsController.enablePrivateDns(this, config.dnsHostname)
                if (ok) {
                    repository.setLastActiveTriggerPackage(packageName)
                }
            }
            ForegroundAppFilter.DnsAction.DISABLE_DNS -> {
                currentForegroundPackage = packageName
                PrivateDnsController.disablePrivateDns(this)
                repository.setLastActiveTriggerPackage(null)
            }
            ForegroundAppFilter.DnsAction.IGNORE -> {
                // Keep currentForegroundPackage and current DNS state intact
            }
        }
    }

    private fun getEnabledImePackages(): Set<String> {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AppDetectorService interrupted")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppDetectorService"

        /**
         * Checks whether AppDetectorService is currently enabled in Android's Accessibility Settings.
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, AppDetectorService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponent }
        }
    }
}
