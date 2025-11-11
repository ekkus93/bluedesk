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
fun ExtendedKeysScreen() {
    val appState by StoreProvider.asStateFlow().collectAsState()
    val connected = appState.connection.connectedDevice != null
    val keyFontSize = LocalKeyFontSize.current
    val rows = listOf(
        listOf("ESC","TAB","ENTER"),
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

    // NOTE: The system keyboard TextField and modifier toggle row are now hosted in the parent
    // (KeyboardScreen / MainActivity) so that they remain fixed when switching between tabs.
    // Local preview console collects preview actions when not connected so developers can
    // visually verify key presses without a Bluetooth host.
    val previewHistory = remember { mutableStateListOf<String>() }
    // Modifier toggles removed from here; they are provided by the parent container.
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
                        ResponsiveText(
                            text = label,
                            minSize = keyFontSize,
                            maxSize = keyFontSize
                        )
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
