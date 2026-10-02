package dev.mwalab.ui.settings

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** UI preferences only. No wallet, authorization, session, or fault authority is stored here. */
class UiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mwa_lab_ui", Context.MODE_PRIVATE)
    var onboardingSeen: Boolean
        get() = prefs.getBoolean("onboarding_seen_v1", false)
        set(value) { prefs.edit().putBoolean("onboarding_seen_v1", value).apply() }
    var themeMode: ThemeMode
        get() = ThemeMode.fromStored(prefs.getString("theme_mode", null))
        set(value) { prefs.edit().putString("theme_mode", value.name).apply() }
}
