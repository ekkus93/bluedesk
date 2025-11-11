package com.augustusmachin.android_bt_kbmouse

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.TextField
import com.augustusmachin.android_bt_kbmouse.DebugLog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateListOf
import com.augustusmachin.android_bt_kbmouse.store.StoreProvider
import com.augustusmachin.android_bt_kbmouse.store.Action

@Composable
fun ExtendedKeysScreen(autoShowKeyboard: Boolean = false) {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val rows = listOf(
        listOf("ESC","TAB","CAPS","ENTER"),
        listOf("F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"),
        listOf("PRTSC","PAUSE","INS"),
        listOf("HOME","END","PGUP","PGDN","DEL"),
        listOf("\u2190","\u2191","\u2193","\u2192")
    )
    // Use top-level helper so mapping logic can be unit tested
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // Small system keyboard helper: a visible TextField. We no longer expose a manual
    // Show/Hide button (was confusing/reliability hazard). Auto-show is handled by
    // the `autoShowKeyboard` flag below.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var previewText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val view = LocalView.current

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(
            value = previewText,
            onValueChange = { previewText = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("System keyboard input") }
        )
    }

        // If requested, automatically focus the preview TextField and show the IME when this screen
        // enters composition (useful so the system keyboard is visible whenever the Keyboard tab is open).
        if (autoShowKeyboard) {
            val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboardController?.show()
                // Also request via IMM for compatibility
                try { imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) } catch (_: Throwable) {}
            }
        }
    // Local preview console collects preview actions when not connected so developers can
    // visually verify key presses without a Bluetooth host.
    val previewHistory = remember { mutableStateListOf<String>() }
        // Toggle buttons row: scroll lock, shift, alt, gui/windows
        val ks = appState.keyboard
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Allow modifier toggles in preview mode as well – middleware will only emit HID when a sender
            // is installed (i.e. when connected). In preview mode we surface a brief message.
            Button(onClick = { StoreProvider.dispatch(Action.ToggleScrollLock) }, modifier = Modifier.weight(1f)) {
                Text(if (appState.connection.scrollLock) "Scrl ⬤" else "Scrl")
            }
            Button(onClick = {
                StoreProvider.dispatch(Action.ToggleShift)
                if (!connected) StoreProvider.dispatch(Action.UpdateMessage("Preview: Shift toggled"))
            }, modifier = Modifier.weight(1f)) {
                Text(if (ks.shift) "Shift ⬤" else "Shift")
            }
            Button(onClick = {
                StoreProvider.dispatch(Action.ToggleAlt)
                if (!connected) StoreProvider.dispatch(Action.UpdateMessage("Preview: Alt toggled"))
            }, modifier = Modifier.weight(1f)) {
                Text(if (ks.alt) "Alt ⬤" else "Alt")
            }
            Button(onClick = {
                StoreProvider.dispatch(Action.ToggleGui)
                if (!connected) StoreProvider.dispatch(Action.UpdateMessage("Preview: Meta toggled"))
            }, modifier = Modifier.weight(1f)) {
                Text(if (ks.gui) "Meta ⬤" else "Meta")
            }
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { label ->
                    val code = labelToHid(label)
                    Button(
                        onClick = {
                            if (code != null) {
                                if (connected) {
                                    StoreProvider.dispatch(Action.SendKey(code))
                                } else {
                                    // Preview: show a brief message and log the HID code so developers can verify
                                    val hex = String.format("0x%02X", code.toInt() and 0xFF)
                                    val msg = "Preview: $label -> $hex"
                                    StoreProvider.dispatch(Action.UpdateMessage(msg))
                                    DebugLog.log("ExtendedKeys", msg)
                                    // append to local preview console (limit size)
                                    previewHistory.add(0, "${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} $msg")
                                    if (previewHistory.size > 200) previewHistory.removeAt(previewHistory.lastIndex)
                                }
                            }
                        },
                        enabled = code != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
        Text("Extended keys")

        // Preview console (only visible/useful when disconnected or in debug mode)
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 220.dp)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Preview console", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { previewHistory.clear() }, enabled = previewHistory.isNotEmpty()) { Text("Clear") }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(previewHistory) { line ->
                        Text(line, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}
