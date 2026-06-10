package com.augustusmachin.android_bt_kbmouse

/** Top-level navigation destinations shown in the bottom/top navigation bar. */
enum class Screen(val route: String, val title: String, val icon: Int) {
    Pairing("pairing", "Pairing", R.drawable.ic_bluetooth),
    Keyboard("keyboard", "Keyboard", R.drawable.ic_keyboard),
    Mouse("mouse", "Mouse", R.drawable.ic_mouse),
    Settings("settings", "Settings", R.drawable.ic_settings)
}
