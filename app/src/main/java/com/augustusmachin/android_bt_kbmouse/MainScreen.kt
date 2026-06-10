package com.augustusmachin.android_bt_kbmouse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice
    // Proactively request notifications on 33+
    val context = LocalContext.current
    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
            DebugLog.log("Main", "POST_NOTIFICATIONS granted=$granted")
        }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val sp = context.getSharedPreferences("perm", Context.MODE_PRIVATE)
            if (!sp.getBoolean("notif_asked", false)) {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    sp.edit().putBoolean("notif_asked", true).apply()
                }
            }
        }
    }
    // Battery-optimization exemption is now offered on demand from the Settings screen
    // (Background reliability), instead of as a prompt on launch.
    // read settings from SettingsViewModel to avoid duplicate collectors
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsState(
        initial = com.augustusmachin.android_bt_kbmouse.Settings(),
    )
    Scaffold(
        topBar = {
            Column {
                // Move the navigation bar to the top so it's accessible even when the IME is visible.
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    Screen.values().forEach { screen ->
                        val isEnabled =
                            connected != null || screen == Screen.Pairing ||
                                screen == Screen.Settings || settings.debugLogging || settings.offlinePreview
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = screen.icon), contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            // Always enabled so onClick fires; disabled appearance applied via colors.
                            enabled = true,
                            colors =
                                if (!isEnabled) {
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        indicatorColor = Color.Transparent,
                                    )
                                } else {
                                    NavigationBarItemDefaults.colors()
                                },
                            onClick = {
                                if (!isEnabled) {
                                    scope.launch { snackbarHostState.showSnackbar("Connect a device first") }
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }

                // Compact status row — hidden on Keyboard screen to save space.
                val statusNavEntry by navController.currentBackStackEntryAsState()
                if (statusNavEntry?.destination?.route != Screen.Keyboard.route) {
                    CenterAlignedTopAppBar(
                        title = {
                            val chip =
                                if (settings.debugLogging) {
                                    val t =
                                        when (settings.logLevel) {
                                            1 -> "Info"
                                            2 -> "Error"
                                            else -> "All"
                                        }
                                    " • Log:" + t
                                } else {
                                    ""
                                }
                            // Compact status: indicator + device name; omit the large app title to save space.
                            val connectedName = connected?.name
                            val statusText =
                                if (connectedName != null) "Connected to $connectedName" else "Disconnected"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.semantics {
                                        this[SemanticsProperties.ContentDescription] = listOf(statusText)
                                    },
                            ) {
                                val indicatorColor =
                                    if (connectedName != null) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.34f,
                                        )
                                    }
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = connectedName ?: "Disconnected", style = MaterialTheme.typography.bodySmall)
                                if (settings.debugLogging && chip.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(chip, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        actions = {
                            // Scan action
                            IconButton(
                                onClick = { StoreProvider.dispatch(Action.StartDiscovery) },
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bluetooth),
                                    contentDescription = "Scan",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Settings action
                            IconButton(
                                onClick = { navController.navigate(Screen.Settings.route) },
                                modifier = Modifier.semantics { this[SemanticsProperties.Role] = Role.Button },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        val message = appState.connection.message
        val navBack by navController.currentBackStackEntryAsState()
        val route = navBack?.destination?.route
        LaunchedEffect(connected, route, settings.offlinePreview, settings.debugLogging) {
            val keyboardAccessible = connected != null || settings.debugLogging || settings.offlinePreview
            if (!keyboardAccessible && (route == Screen.Keyboard.route || route == Screen.Mouse.route)) {
                snackbarHostState.showSnackbar("Disconnected")
                navController.navigate(Screen.Pairing.route) { launchSingleTop = true }
            }
        }
        if (message != null) {
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message!!)
                StoreProvider.dispatch(Action.UpdateMessage(null))
            }
        }
        NavHost(navController, startDestination = Screen.Pairing.route, Modifier) {
            composable(Screen.Pairing.route) { PairingScreen(contentPadding = innerPadding) }
            composable(Screen.Keyboard.route) { KeyboardScreen(contentPadding = innerPadding) }
            composable(Screen.Mouse.route) { MouseScreen(contentPadding = innerPadding) }
            composable(Screen.Settings.route) {
                SettingsScreen(contentPadding = innerPadding, onOpenLogs = { navController.navigate("logs") })
            }
            composable("logs") { LogsScreen(contentPadding = innerPadding) }
        }
    }
}
