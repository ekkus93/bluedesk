package com.augustusmachin.android_bt_kbmouse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that centralizes collection of persisted settings and performs any app-wide side-effects
 * (such as setting DebugLog) so Activities/Composables don't need to use GlobalScope or lifecycle
 * collectors directly.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    init {
        // Collect persisted settings and apply global side-effects (DebugLog level) here.
        viewModelScope.launch {
            SettingsManager.flow(getApplication()).collect { s ->
                _settings.value = s
                // Keep debug logging enabled during troubleshooting and set level from persisted flag
                DebugLog.setEnabled(true)
                val lvl = when (s.logLevel) {
                    1 -> DebugLog.Level.INFO
                    2 -> DebugLog.Level.ERROR
                    else -> DebugLog.Level.ALL
                }
                DebugLog.setLevel(lvl)
            }
        }
    }
}
