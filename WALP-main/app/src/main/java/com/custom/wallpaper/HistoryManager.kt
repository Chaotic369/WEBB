package com.custom.wallpaper

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(val id: Long, val type: String, val uri: String)

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history_list", "[]")
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addHistory(item: HistoryItem) {
        val list = getHistory().toMutableList()
        // Prevent exact duplicates
        if (list.none { it.uri == item.uri }) {
            list.add(0, item)
            prefs.edit().putString("history_list", gson.toJson(list)).apply()
        }
    }

    fun removeHistory(id: Long) {
        val list = getHistory().toMutableList()
        list.removeAll { it.id == id }
        prefs.edit().putString("history_list", gson.toJson(list)).apply()
    }

    fun clearAll() {
        prefs.edit().remove("history_list").apply()
    }
}
