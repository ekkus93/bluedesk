package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

private const val KEY_FONT_SCALE = 4200
private const val KEY_FONT_MIN_SP = 10f
private const val KEY_FONT_MAX_SP = 16f
val LocalKeyFontSize = staticCompositionLocalOf { 16.sp }

// Shared styling for the Extended/Function/Navigation key grids: active/inactive
// modifier-button colors plus the responsive key font size. Bundled to keep the
// per-column composable parameter lists small.
internal data class KeyGridStyle(
    val activeColors: androidx.compose.material3.ButtonColors,
    val inactiveColors: androidx.compose.material3.ButtonColors,
    val keyFontSize: androidx.compose.ui.unit.TextUnit,
)

@androidx.compose.runtime.Composable
internal fun rememberKeyGridStyle(): KeyGridStyle =
    KeyGridStyle(
        activeColors =
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            ),
        inactiveColors =
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            ),
        keyFontSize = LocalKeyFontSize.current,
    )

// Shared modifier-toggle button used by all three key grids. Bolded label, active
// when [active], dispatches the toggle [action] and shows a preview message offline.
@androidx.compose.runtime.Composable
internal fun KeyModifierButton(
    label: String,
    active: Boolean,
    action: com.augustusmachin.android_bt_kbmouse.store.Action,
    connected: Boolean,
    style: KeyGridStyle,
) {
    androidx.compose.material3.Button(
        onClick = {
            StoreProvider.dispatch(Action.TrackPreviewKey(label))
            StoreProvider.dispatch(action)
            if (!connected) {
                StoreProvider.dispatch(Action.UpdateMessage("Preview: $label toggled"))
            }
        },
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        colors = if (active) style.activeColors else style.inactiveColors,
    ) {
        ResponsiveText(
            text = label,
            minSize = 8.sp,
            maxSize = style.keyFontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

// Shared key button used by all three key grids: enabled only when the label maps
// to a HID code, dispatches via [onClick].
@androidx.compose.runtime.Composable
internal fun KeyCellButton(
    label: String,
    style: KeyGridStyle,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = labelToHid(label) != null,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
    ) {
        ResponsiveText(text = label, minSize = style.keyFontSize, maxSize = style.keyFontSize)
    }
}

@Composable
fun KeyboardScreen(contentPadding: PaddingValues = PaddingValues()) {
    // Replace the tiny demo with the full Extended Keys UI by default.
    // The ExtendedKeysScreen already handles modifier toggles and connected-state gating.
    val tabs = listOf("Extended", "Function", "Navigation")
    var selectedTab by remember { mutableStateOf(0) }
    val appState by StoreProvider.asStateFlow().collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var previewText by remember { mutableStateOf("") }
    val previewKeys = appState.ui.previewKeys
    val previewSuffix = remember(previewKeys) { buildPreviewSuffix(previewKeys) }
    val previewTransformation =
        remember(previewSuffix) {
            if (previewSuffix.isEmpty()) VisualTransformation.None else PreviewSuffixVisualTransformation(previewSuffix)
        }
    val connected = appState.connection.connectedDevice != null
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val keyFontSize =
        remember(screenWidthDp) { (KEY_FONT_SCALE / screenWidthDp).coerceIn(KEY_FONT_MIN_SP, KEY_FONT_MAX_SP).sp }

    CompositionLocalProvider(LocalKeyFontSize provides keyFontSize) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            KeyboardTabRow(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onShowKeyboard = { keyboardController?.show() },
            )

            HiddenImeField(
                previewText = previewText,
                onPreviewTextChange = { previewText = it },
                connected = connected,
                focusRequester = focusRequester,
                previewTransformation = previewTransformation,
            )
            // Auto-focus the hidden field and show IME whenever the Keyboard screen is visible
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }

            // Content area: render the selected tab's screen
            when (selectedTab) {
                0 -> ExtendedKeysScreen()
                1 -> FunctionKeysScreen()
                2 -> NavigationKeysScreen()
            }
        }
    }
}

private typealias PreviewKeyEntry = com.augustusmachin.android_bt_kbmouse.store.PreviewKeyEntry

// Builds the visual preview suffix appended to the hidden IME field, inserting
// separator spaces only between multi-character/decorated tokens.
private fun buildPreviewSuffix(previewKeys: List<PreviewKeyEntry>): String {
    if (previewKeys.isEmpty()) return ""
    return buildString {
        previewKeys.forEachIndexed { index, entry ->
            val previous = previewKeys.getOrNull(index - 1)
            if (isNotEmpty() && needsPreviewSeparator(entry, previous)) append(' ')
            append(if (entry.decorate) "[${entry.label}]" else entry.label)
        }
    }
}

// True when a space must separate [previous] from [entry] (one is multi-char or
// decorated, and neither is a literal space).
private fun needsPreviewSeparator(
    entry: PreviewKeyEntry,
    previous: PreviewKeyEntry?,
): Boolean {
    val currentIsSpace = !entry.decorate && entry.label == " "
    val previousIsSpace = previous != null && !previous.decorate && previous.label == " "
    if (currentIsSpace || previousIsSpace) return false
    val currentRequiresSpace = entry.decorate || entry.label.length > 1
    val previousRequiresSpace = previous != null && (previous.decorate || previous.label.length > 1)
    return currentRequiresSpace || previousRequiresSpace
}

@Composable
private fun KeyboardTabRow(
    tabs: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onShowKeyboard: () -> Unit,
) {
    // TabRow with keyboard icon (tap to re-show IME if dismissed)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedTab == idx, onClick = { onSelectTab(idx) }, text = { Text(title) })
            }
        }
        IconButton(onClick = onShowKeyboard) {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard),
                contentDescription = "Show system keyboard",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenImeField(
    previewText: String,
    onPreviewTextChange: (String) -> Unit,
    connected: Boolean,
    focusRequester: FocusRequester,
    previewTransformation: VisualTransformation,
) {
    // Invisible 1dp-tall text field — always present so the IME has an attach point.
    // graphicsLayer alpha=0 hides it visually while keeping it focusable.
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .graphicsLayer { alpha = 0f },
    ) {
        TextField(
            value = previewText,
            onValueChange = { newValue ->
                if (newValue.isNotEmpty()) {
                    newValue.forEach { char ->
                        val (label, decorate, mapping) =
                            when (char) {
                                ' ' -> Triple(" ", false, charToHid(char))
                                '\n' -> Triple("ENTER", false, charToHid(char))
                                '\t' -> Triple("TAB", false, charToHid(char))
                                else -> Triple(char.toString(), false, charToHid(char))
                            }
                        StoreProvider.dispatch(Action.TrackPreviewKey(label, decorate = decorate))
                        if (connected && mapping != null) {
                            StoreProvider.dispatch(Action.SendKey(mapping.first, mapping.second))
                        } else {
                            StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                        }
                    }
                }
                onPreviewTextChange("")
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                            native.action == android.view.KeyEvent.ACTION_DOWN
                        ) {
                            StoreProvider.dispatch(Action.TrackPreviewKey("DEL"))
                            if (connected) {
                                StoreProvider.dispatch(Action.SendKey(0x2A.toByte(), 0))
                            } else {
                                StoreProvider.dispatch(Action.ReleaseLockedModifiers)
                            }
                            true
                        } else {
                            false
                        }
                    },
            visualTransformation = previewTransformation,
            placeholder = { Text("System keyboard input") },
        )
    }
}

private class PreviewSuffixVisualTransformation(
    private val suffix: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (suffix.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder()
        builder.append(text)
        builder.append(suffix)
        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset

                override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(text.length)
            }
        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
