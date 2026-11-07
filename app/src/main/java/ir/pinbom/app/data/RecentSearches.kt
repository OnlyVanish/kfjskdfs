package ir.pinbom.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object RecentSearches {
    private const val PREFS = "pinbom_searches"
    fun load(context: Context): List<String> = runCatching {
        Json.decodeFromString<List<String>>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("recent", "[]") ?: "[]")
            .filter { it.isNotBlank() }.distinct().take(8)
    }.getOrDefault(emptyList())
    fun save(context: Context, values: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("recent", Json.encodeToString(values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8))).apply()
    }
    fun clear(context: Context) = save(context, emptyList())
}
