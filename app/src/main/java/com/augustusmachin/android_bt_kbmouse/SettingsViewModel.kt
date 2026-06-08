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

    private val _isLoaded = MutableStateFlow(false)
    /** True after the first persisted-settings emission from DataStore. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            SettingsManager.flow(getApplication()).collect { s ->
                _settings.value = s
                _isLoaded.value = true
                // Phase 7: respect the user's debug-logging setting instead of forcing on.
                DebugLog.setEnabled(s.debugLogging)
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
