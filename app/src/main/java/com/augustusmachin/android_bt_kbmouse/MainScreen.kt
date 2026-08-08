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
import androidx.compose.foundation.layout.PaddingValues
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
import com.augustusmachin.android_bt_kbmouse.store.hasConnectedHost
import com.augustusmachin.android_bt_kbmouse.store.isInputUsable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SDK_INT_TIRAMISU = 33

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appState by StoreProvider.asStateFlow().collectAsState()
    val hostConnected = appState.hasConnectedHost()
    val inputUsable = appState.isInputUsable()
    val connectedName = appState.connection.connectedDeviceLabel
    val readyState = appState.backend.runtime as? BackendRuntimeState.Ready
    val canDiscover = readyState?.capabilities?.discovery == true

    val context = LocalContext.current
    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            context.getSharedPreferences("perm", Context.MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
            DebugLog.log("Main", "POST_NOTIFICATIONS granted=$granted")
        }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= SDK_INT_TIRAMISU) {
            StartupState.permissionFlowResolved.first { it }
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

    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsState(initial = Settings())
    val onShowMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
    Scaffold(
        topBar = {
            MainTopBar(
                navController = navController,
                hostConnected = hostConnected,
                inputUsable = inputUsable,
                connectedName = connectedName,
                canDiscover = canDiscover,
                settings = settings,
                onShowMessage = onShowMessage,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        val message = appState.connection.message
        val navBack by navController.currentBackStackEntryAsState()
        val route = navBack?.destination?.route
        LaunchedEffect(inputUsable, route, settings.offlinePreview, settings.debugLogging) {
            val keyboardAccessible = inputUsable || settings.debugLogging || settings.offlinePreview
            if (!keyboardAccessible && (route == Screen.Keyboard.route || route == Screen.Mouse.route)) {
                snackbarHostState.showSnackbar(
                    if (hostConnected) "Input backend is not ready" else "Disconnected",
                )
                navController.navigate(Screen.Pairing.route) { launchSingleTop = true }
            }
        }
        if (message != null) {
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message!!)
                StoreProvider.dispatch(Action.UpdateMessage(null))
            }
        }
        MainNavHost(navController, innerPadding)
    }
}

@Composable
private fun MainTopBar(
    navController: androidx.navigation.NavHostController,
    hostConnected: Boolean,
    inputUsable: Boolean,
    connectedName: String?,
    canDiscover: Boolean,
    settings: Settings,
    onShowMessage: (String) -> Unit,
) {
    Column {
        MainNavigationBar(navController, hostConnected, inputUsable, settings, onShowMessage)
        val statusNavEntry by navController.currentBackStackEntryAsState()
        if (statusNavEntry?.destination?.route != Screen.Keyboard.route) {
            StatusTopBar(hostConnected, inputUsable, connectedName, canDiscover, settings, navController)
        }
    }
}

@Composable
private fun MainNavigationBar(
    navController: androidx.navigation.NavHostController,
    hostConnected: Boolean,
    inputUsable: Boolean,
    settings: Settings,
    onShowMessage: (String) -> Unit,
) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        Screen.values().forEach { screen ->
            val isEnabled =
                inputUsable || screen == Screen.Pairing || screen == Screen.Settings ||
                    settings.debugLogging || settings.offlinePreview
            NavigationBarItem(
                icon = { Icon(painterResource(id = screen.icon), contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
                        onShowMessage(
                            if (hostConnected) {
                                "Bluetooth host is connected, but the input backend is not ready"
                            } else {
                                "Connect a device first"
                            },
                        )
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
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StatusTopBar(
    hostConnected: Boolean,
    inputUsable: Boolean,
    connectedName: String?,
    canDiscover: Boolean,
    settings: Settings,
    navController: androidx.navigation.NavHostController,
) {
    CenterAlignedTopAppBar(
        title = { StatusTitle(hostConnected, inputUsable, connectedName, settings) },
        actions = { StatusActions(navController, canDiscover) },
    )
}

@Composable
private fun StatusTitle(
    hostConnected: Boolean,
    inputUsable: Boolean,
    connectedName: String?,
    settings: Settings,
) {
    val chip =
        if (settings.debugLogging) {
            val t =
                when (settings.logLevel) {
                    1 -> "Info"
                    2 -> "Error"
                    else -> "All"
                }
            " • Log:$t"
        } else {
            ""
        }
    val deviceLabel = connectedName ?: "Bluetooth host"
    val statusText =
        when {
            hostConnected && inputUsable -> "Connected to $deviceLabel"
            hostConnected -> "$deviceLabel — input unavailable"
            else -> "Disconnected"
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { this[SemanticsProperties.ContentDescription] = listOf(statusText) },
    ) {
        val indicatorColor =
            when {
                hostConnected && inputUsable -> MaterialTheme.colorScheme.tertiary
                hostConnected -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
            }
        Box(Modifier.size(8.dp).clip(CircleShape).background(indicatorColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = statusText, style = MaterialTheme.typography.bodySmall)
        if (settings.debugLogging && chip.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(chip, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusActions(
    navController: androidx.navigation.NavHostController,
    canDiscover: Boolean,
) {
    if (canDiscover) {
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
    }
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
}

@Composable
private fun MainNavHost(
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(navController, startDestination = Screen.Pairing.route, Modifier) {
        composable(Screen.Pairing.route) { BackendAwarePairingScreen(contentPadding = innerPadding) }
        composable(Screen.Keyboard.route) { KeyboardScreen(contentPadding = innerPadding) }
        composable(Screen.Mouse.route) { MouseScreen(contentPadding = innerPadding) }
        composable(Screen.Settings.route) {
            SettingsScreen(contentPadding = innerPadding, onOpenLogs = { navController.navigate("logs") })
        }
        composable("logs") { LogsScreen(contentPadding = innerPadding) }
    }
}
