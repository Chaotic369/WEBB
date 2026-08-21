package com.custom.wallpaper

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_settings", Context.MODE_PRIVATE)

    var currentType: String
        get() = prefs.getString("currentType", "web") ?: "web"
        set(value) = prefs.edit().putString("currentType", value).apply()

    var currentUri: String
        get() = prefs.getString("currentUri", "") ?: ""
        set(value) = prefs.edit().putString("currentUri", value).apply()

    var zoomLevel: Float
        get() = prefs.getFloat("zoomLevel", 1.0f)
        set(value) = prefs.edit().putFloat("zoomLevel", value).apply()

    var scaleMode: String
        get() = prefs.getString("scaleMode", "Center Crop") ?: "Center Crop"
        set(value) = prefs.edit().putString("scaleMode", value).apply()

    var fpsLimit: String
        get() = prefs.getString("fpsLimit", "Default") ?: "Default"
        set(value) = prefs.edit().putString("fpsLimit", value).apply()

    var kbdOverlay: Boolean
        get() = prefs.getBoolean("kbdOverlay", false)
        set(value) = prefs.edit().putBoolean("kbdOverlay", value).apply()

    var pauseWhenNotUse: Boolean
        get() = prefs.getBoolean("pauseWhenNotUse", true)
        set(value) = prefs.edit().putBoolean("pauseWhenNotUse", value).apply()

    var batterySaver: Boolean
        get() = prefs.getBoolean("batterySaver", true)
        set(value) = prefs.edit().putBoolean("batterySaver", value).apply()

    var batteryThreshold: Int
        get() = prefs.getInt("batteryThreshold", 20)
        set(value) = prefs.edit().putInt("batteryThreshold", value).apply()

    var gyroEnabled: Boolean
        get() = prefs.getBoolean("gyroEnabled", false)
        set(value) = prefs.edit().putBoolean("gyroEnabled", value).apply()

    var touchPassThrough: Boolean
        get() = prefs.getBoolean("touchPassThrough", false)
        set(value) = prefs.edit().putBoolean("touchPassThrough", value).apply()

    var btnSize: Int
        get() = prefs.getInt("btnSize", 30)
        set(value) = prefs.edit().putInt("btnSize", value).apply()

    // Persistent state for the movable interaction button
    var btnX: Float
        get() = prefs.getFloat("btnX", 50f)
        set(value) = prefs.edit().putFloat("btnX", value).apply()

    var btnY: Float
        get() = prefs.getFloat("btnY", 50f)
        set(value) = prefs.edit().putFloat("btnY", value).apply()

    var interactionUnlocked: Boolean
        get() = prefs.getBoolean("interactionUnlocked", false)
        set(value) = prefs.edit().putBoolean("interactionUnlocked", value).apply()
}
