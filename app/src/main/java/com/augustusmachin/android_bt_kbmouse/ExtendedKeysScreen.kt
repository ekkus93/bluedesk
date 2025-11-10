package com.augustusmachin.android_bt_kbmouse

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExtendedKeysScreen(viewModel: PairingViewModel) {
    val connected by viewModel.connectedDevice.collectAsState()
    val rows = listOf(
        listOf("ESC","TAB","CAPS","ENTER"),
        listOf("F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"),
        listOf("HOME","END","PGUP","PGDN","DEL"),
        listOf("\u2190","\u2191","\u2193","\u2192")
    )
    fun map(label: String): Byte? = when(label) {
        "ESC" -> 0x29.toByte()
        "TAB" -> 0x2B.toByte()
        "CAPS" -> 0x39.toByte()
        "ENTER" -> 0x28.toByte()
        "HOME" -> 0x4A.toByte()
        "END" -> 0x4D.toByte()
        "PGUP" -> 0x4B.toByte()
        "PGDN" -> 0x4E.toByte()
        "DEL" -> 0x4C.toByte()
        "\u2190" -> 0x50.toByte()
        "\u2192" -> 0x4F.toByte()
        "\u2191" -> 0x52.toByte()
        "\u2193" -> 0x51.toByte()
        else -> if (label.startsWith("F")) {
            val n = label.removePrefix("F").toIntOrNull()
            if (n != null && n in 1..12) (0x39 + n).toByte() else null
        } else null
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { label ->
                    val code = map(label)
                    Button(onClick = { if (code != null) viewModel.sendKey(code) }, enabled = connected != null && code != null, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                }
            }
        }
        Text("Extended keys")
    }
}
