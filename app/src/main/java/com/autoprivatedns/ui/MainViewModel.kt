package com.autoprivatedns.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoprivatedns.core.PrivateDnsController
import com.autoprivatedns.core.SystemPrivateDnsStatus
import com.autoprivatedns.data.DnsConfig
import com.autoprivatedns.data.DnsPreferencesRepository
import com.autoprivatedns.service.AppDetectorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable?
)

data class PermissionAndSystemState(
    val hasWriteSecureSettings: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val systemDnsStatus: SystemPrivateDnsStatus = SystemPrivateDnsStatus(
        mode = PrivateDnsController.MODE_OFF,
        specifier = ""
    )
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsPreferencesRepository.getInstance(application)
    val config: StateFlow<DnsConfig> = repository.config

    private val _allInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _systemState = MutableStateFlow(PermissionAndSystemState())
    val systemState: StateFlow<PermissionAndSystemState> = _systemState.asStateFlow()

    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        _allInstalledApps,
        _searchQuery,
        _showSystemApps,
        repository.config
    ) { apps, query, includeSystem, dnsConfig ->
        val selected = dnsConfig.selectedPackages
        val normalizedQuery = query.trim().lowercase()

        apps.asSequence()
            .filter { app ->
                // Always show apps that are already selected even if system apps filter is off
                includeSystem || !app.isSystemApp || app.packageName in selected
            }
            .filter { app ->
                if (normalizedQuery.isEmpty()) true
                else app.appName.lowercase().contains(normalizedQuery) ||
                    app.packageName.lowercase().contains(normalizedQuery)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.packageName in selected }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSystemState()
        loadInstalledApps()
    }

    fun refreshSystemState() {
        val ctx = getApplication<Application>()
        _systemState.value = PermissionAndSystemState(
            hasWriteSecureSettings = PrivateDnsController.hasWriteSecureSettingsPermission(ctx),
            isAccessibilityEnabled = AppDetectorService.isServiceEnabled(ctx),
            systemDnsStatus = PrivateDnsController.getCurrentStatus(ctx)
        )
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val pm = ctx.packageManager
                val ownPackage = ctx.packageName

                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        mainIntent,
                        PackageManager.ResolveInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }

                resolveInfos
                    .mapNotNull { it.activityInfo?.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != ownPackage }
                    .map { appInfo ->
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                        val icon = try {
                            pm.getApplicationIcon(appInfo.packageName)
                        } catch (_: Exception) {
                            null
                        }
                        InstalledAppInfo(
                            appName = label,
                            packageName = appInfo.packageName,
                            isSystemApp = isSystem,
                            icon = icon
                        )
                    }
            }
            _allInstalledApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onToggleShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun onToggleAppSelection(packageName: String) {
        repository.togglePackageSelection(packageName)
    }

    fun onMasterEnabledChange(enabled: Boolean) {
        repository.setMasterEnabled(enabled)
        if (!enabled) {
            PrivateDnsController.disablePrivateDns(getApplication())
        }
        refreshSystemState()
    }

    fun onSaveDnsHostname(hostname: String) {
        repository.setDnsHostname(hostname)
        refreshSystemState()
    }

    fun testEnableDnsNow() {
        val ctx = getApplication<Application>()
        PrivateDnsController.enablePrivateDns(ctx, repository.config.value.dnsHostname)
        refreshSystemState()
    }

    fun testDisableDnsNow() {
        val ctx = getApplication<Application>()
        PrivateDnsController.disablePrivateDns(ctx)
        refreshSystemState()
    }

    fun tryGrantWithRoot(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) {
                PrivateDnsController.tryGrantPermissionWithRoot(getApplication())
            }
            refreshSystemState()
            onResult(granted)
        }
    }
}
