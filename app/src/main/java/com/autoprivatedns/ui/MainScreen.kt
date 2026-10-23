package com.autoprivatedns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoprivatedns.core.PrivateDnsController
import com.autoprivatedns.data.DnsConfig
import com.autoprivatedns.data.DnsPreferencesRepository

private val PRESET_DNS_PROVIDERS = listOf(
    "AdGuard" to "dns.adguard-dns.com",
    "Cloudflare" to "one.one.one.one",
    "Quad9" to "dns.quad9.net",
    "Google" to "dns.google"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val systemState by viewModel.systemState.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Auto Private DNS")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshSystemState()
                        viewModel.loadInstalledApps()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Setup Checklist Card (shown prominently if either permission or accessibility is missing)
            if (!systemState.hasWriteSecureSettings || !systemState.isAccessibilityEnabled) {
                item {
                    SetupRequirementsCard(
                        context = context,
                        systemState = systemState,
                        onTryRootGrant = {
                            viewModel.tryGrantWithRoot { granted ->
                                val msg = if (granted) {
                                    "WRITE_SECURE_SETTINGS granted via Root!"
                                } else {
                                    "Root not available. Please run the copied ADB command."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // 2. Private DNS Configuration & Live Status Card
            item {
                DnsConfigurationCard(
                    config = config,
                    systemState = systemState,
                    onMasterToggle = viewModel::onMasterEnabledChange,
                    onSaveHostname = { newHost ->
                        viewModel.onSaveDnsHostname(newHost)
                        Toast.makeText(context, "Saved Private DNS hostname", Toast.LENGTH_SHORT).show()
                    },
                    onTestEnable = viewModel::testEnableDnsNow,
                    onTestDisable = viewModel::testDisableDnsNow
                )
            }

            // 3. App Selection Header & Search Controls
            item {
                AppSelectionHeader(
                    selectedCount = config.selectedPackages.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    showSystemApps = showSystemApps,
                    onToggleShowSystemApps = viewModel::onToggleShowSystemApps
                )
            }

            // 4. Installed Apps List
            if (isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = apps,
                    key = { it.packageName }
                ) { app ->
                    val isSelected = app.packageName in config.selectedPackages
                    AppItemRow(
                        app = app,
                        isSelected = isSelected,
                        onToggle = { viewModel.onToggleAppSelection(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRequirementsCard(
    context: Context,
    systemState: PermissionAndSystemState,
    onTryRootGrant: () -> Unit
) {
    val adbCommand = remember(context) { PrivateDnsController.getAdbGrantCommand(context) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "One-Time Setup Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Step 1: WRITE_SECURE_SETTINGS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (systemState.hasWriteSecureSettings) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = if (systemState.hasWriteSecureSettings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. Secure Settings Permission",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (systemState.hasWriteSecureSettings) {
                            "Granted! App can toggle Private DNS."
                        } else {
                            "Run this ADB command once from a PC or Shizuku:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!systemState.hasWriteSecureSettings) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = adbCommand,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
                            Toast.makeText(context, "ADB command copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy ADB Command")
                    }

                    OutlinedButton(onClick = onTryRootGrant) {
                        Text("Grant via Root")
                    }
                }
            }

            HorizontalDivider()

            // Step 2: Accessibility Service
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (systemState.isAccessibilityEnabled) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Settings
                    },
                    contentDescription = null,
                    tint = if (systemState.isAccessibilityEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2. App Open/Close Detection Service",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (systemState.isAccessibilityEnabled) {
                            "Active! Detecting when selected apps open and close."
                        } else {
                            "Enable 'Auto Private DNS App Detector' in Accessibility Settings."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!systemState.isAccessibilityEnabled) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Accessibility Settings")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsConfigurationCard(
    config: DnsConfig,
    systemState: PermissionAndSystemState,
    onMasterToggle: (Boolean) -> Unit,
    onSaveHostname: (String) -> Unit,
    onTestEnable: () -> Unit,
    onTestDisable: () -> Unit
) {
    var hostnameInput by remember(config.dnsHostname) { mutableStateOf(config.dnsHostname) }
    val isValid = DnsPreferencesRepository.isValidHostname(hostnameInput)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Private DNS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = buildString {
                        append("System DNS: ")
                        when (systemState.systemDnsStatus.mode) {
                            PrivateDnsController.MODE_HOSTNAME ->
                                append("ON (${systemState.systemDnsStatus.specifier})")
                            PrivateDnsController.MODE_OPPORTUNISTIC ->
                                append("Automatic")
                            else ->
                                append("OFF")
                        }
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (systemState.systemDnsStatus.isHostnameActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = config.isMasterEnabled,
                    onCheckedChange = onMasterToggle
                )
            }

            OutlinedTextField(
                value = hostnameInput,
                onValueChange = { hostnameInput = it },
                label = { Text("Private DNS Provider Hostname") },
                placeholder = { Text("e.g. dns.adguard-dns.com") },
                singleLine = true,
                isError = hostnameInput.isNotBlank() && !isValid,
                supportingText = {
                    if (hostnameInput.isNotBlank() && !isValid) {
                        Text("Enter a valid DNS hostname (without https:// or paths)")
                    } else {
                        Text("Turns ON when a selected app opens, and OFF when it closes")
                    }
                },
                trailingIcon = {
                    if (hostnameInput != config.dnsHostname && isValid) {
                        Button(
                            onClick = { onSaveHostname(hostnameInput) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Save")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Quick presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PRESET_DNS_PROVIDERS.forEach { (label, host) ->
                    AssistChip(
                        onClick = {
                            hostnameInput = host
                            onSaveHostname(host)
                        },
                        label = { Text(label) }
                    )
                }
            }

            if (systemState.hasWriteSecureSettings) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onTestEnable,
                        enabled = isValid,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Turn ON")
                    }
                    OutlinedButton(
                        onClick = onTestDisable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Turn OFF")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionHeader(
    selectedCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSystemApps: Boolean,
    onToggleShowSystemApps: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target Apps ($selectedCount selected)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilterChip(
                selected = showSystemApps,
                onClick = { onToggleShowSystemApps(!showSystemApps) },
                label = { Text("System Apps") }
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppItemRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(app.packageName, app.icon) {
                app.icon?.toBitmapSafely(96)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(42.dp)
                )
            } else {
                Box(modifier = Modifier.size(42.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

private fun Drawable.toBitmapSafely(sizePx: Int): Bitmap? {
    return try {
        val width = if (intrinsicWidth > 0) intrinsicWidth else sizePx
        val height = if (intrinsicHeight > 0) intrinsicHeight else sizePx
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (_: Exception) {
        null
    }
}
