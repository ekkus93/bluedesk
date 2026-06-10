package com.augustusmachin.android_bt_kbmouse

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LogsScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val lines by DebugLog.lines.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(0) } // 0=All,1=Info,2=Error
    val filtered = remember(lines, filter) {
        when (filter) {
            2 -> lines.filter { it.contains(" E [") }
            1 -> lines.filter { !it.contains(" E [") }
            else -> lines
        }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).navigationBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { DebugLog.clear() }, modifier = Modifier.padding(end = 8.dp)) { Text("Clear") }
            Button(onClick = {
                val meta = "App: ${context.packageName}\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\nFilter: " + when(filter){0->"All";1->"Info";else->"Error"} + "\n\n"
                val body = meta + filtered.joinToString("\n")
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_SUBJECT, "Android BT KB/Mouse Logs")
                intent.putExtra(Intent.EXTRA_TEXT, body)
                context.startActivity(Intent.createChooser(intent, "Share logs"))
            }) { Text("Share") }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { filter = 0 }, modifier = Modifier.padding(end = 4.dp)) { Text("All") }
            Button(onClick = { filter = 1 }, modifier = Modifier.padding(end = 4.dp)) { Text("Info") }
            Button(onClick = { filter = 2 }) { Text("Error") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { line -> Text(line, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
