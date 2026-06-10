package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.navigation.NavHostController
import com.augustusmachin.android_bt_kbmouse.store.Action
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider

private const val KEY_FONT_SCALE = 4200
val LocalKeyFontSize = staticCompositionLocalOf { 16.sp }

@Composable
fun KeyboardScreen(navController: NavHostController, contentPadding: PaddingValues = PaddingValues()) {
    // Replace the tiny demo with the full Extended Keys UI by default.
    // The ExtendedKeysScreen already handles modifier toggles and connected-state gating.
    val tabs = listOf("Extended", "Function", "Navigation")
    var selectedTab by remember { mutableStateOf(0) }
    val appState by StoreProvider.asStateFlow().collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var previewText by remember { mutableStateOf("") }
    val previewKeys = appState.ui.previewKeys
    val previewSuffix = remember(previewKeys) {
        if (previewKeys.isEmpty()) {
            ""
        } else buildString {
            previewKeys.forEachIndexed { index, entry ->
                val decoratedText = if (entry.decorate) "[${entry.label}]" else entry.label
                val previous = previewKeys.getOrNull(index - 1)
                val currentRequiresSpace = entry.decorate || entry.label.length > 1
                val previousRequiresSpace = previous?.decorate == true || ((previous?.label?.length ?: 0) > 1)
                val currentIsSpace = !entry.decorate && entry.label == " "
                val previousIsSpace = previous?.let { !it.decorate && it.label == " " } == true
                val needsSeparator = when {
                    isEmpty() -> false
                    currentIsSpace || previousIsSpace -> false
                    currentRequiresSpace || previousRequiresSpace -> true
                    else -> false
                }
                if (needsSeparator) append(' ')
                append(decoratedText)
            }
        }
    }
    val previewTransformation = remember(previewSuffix) {
        if (previewSuffix.isEmpty()) VisualTransformation.None else PreviewSuffixVisualTransformation(previewSuffix)
    }
    val connected = appState.connection.connectedDevice != null
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val keyFontSize = remember(screenWidthDp) { (KEY_FONT_SCALE / screenWidthDp).coerceIn(10f, 16f).sp }

    CompositionLocalProvider(LocalKeyFontSize provides keyFontSize) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        // TabRow with keyboard icon (tap to re-show IME if dismissed)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f)) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
                }
            }
            IconButton(onClick = { keyboardController?.show() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard),
                    contentDescription = "Show system keyboard",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Invisible 1dp-tall text field — always present so the IME has an attach point.
        // graphicsLayer alpha=0 hides it visually while keeping it focusable.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .graphicsLayer { alpha = 0f }
        ) {
            TextField(
                value = previewText,
                onValueChange = { newValue ->
                    if (newValue.isNotEmpty()) {
                        newValue.forEach { char ->
                            val (label, decorate, mapping) = when (char) {
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
                    previewText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_DEL && native.action == android.view.KeyEvent.ACTION_DOWN) {
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
                placeholder = { Text("System keyboard input") }
            )
        }
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

private class PreviewSuffixVisualTransformation(
    private val suffix: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (suffix.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder()
        builder.append(text)
        builder.append(suffix)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(text.length)
        }
        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
