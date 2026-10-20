package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.getSharedPrefs
import org.json.JSONArray
import org.json.JSONObject
import org.fossify.messages.models.SenderGroup

/**
 * Exports every app preference (including sender groups) into a single JSON file and
 * imports it back, so settings can be moved to another phone together with the APK.
 *
 * Sender groups are stored with device specific thread IDs, which are meaningless on
 * another phone, so they are dropped on import. Groups are re-matched by address when
 * conversations are reloaded afterwards.
 */
object SettingsBackup {
    private const val FORMAT = "fossify_messages_settings"
    private const val VERSION = 1

    fun exportToJson(context: Context): String {
        val preferences = JSONObject()
        for ((key, value) in context.getSharedPrefs().all) {
            when (value) {
                null -> Unit
                is String -> preferences.put(key, entry("string", value))
                is Int -> preferences.put(key, entry("int", value))
                is Long -> preferences.put(key, entry("long", value))
                is Float -> preferences.put(key, entry("float", value))
                is Boolean -> preferences.put(key, entry("boolean", value))
                is Set<*> -> {
                    val array = JSONArray()
                    for (item in value) {
                        if (item is String) {
                            array.put(item)
                        }
                    }
                    preferences.put(key, entry("string_set", array))
                }
                else -> Unit
            }
        }

        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exported_at", System.currentTimeMillis())
            put("preferences", preferences)
        }.toString(4)
    }

    fun importFromJson(context: Context, json: String) {
        val root = JSONObject(json)
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("Not an IN Messages settings file")
        }
        val version = root.optInt("version", 1)
        if (version > VERSION) {
            throw IllegalArgumentException("Settings file was created by a newer app version")
        }
        val preferences = root.optJSONObject("preferences")
            ?: throw IllegalArgumentException("Settings file has no preferences")

        val editor = context.getSharedPrefs().edit().clear()
        for (key in preferences.keys()) {
            val item = preferences.optJSONObject(key) ?: continue
            val rawValue = item.opt("value") ?: continue
            when (item.optString("type")) {
                "string" -> editor.putString(key, portableValue(key, rawValue.toString()))
                "int" -> editor.putInt(key, rawValue.toString().toInt())
                "long" -> editor.putLong(key, rawValue.toString().toLong())
                "float" -> editor.putFloat(key, rawValue.toString().toFloat())
                "boolean" -> editor.putBoolean(key, rawValue.toString().toBoolean())
                "string_set" -> {
                    val array = item.optJSONArray("value") ?: JSONArray()
                    val set = HashSet<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.getString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }

        if (!editor.commit()) {
            throw IllegalStateException("Could not save imported settings")
        }
    }

    private fun entry(type: String, value: Any) =
        JSONObject().put("type", type).put("value", value)

    private fun portableValue(key: String, value: String): String {
        if (key != SENDER_GROUPS) {
            return value
        }
        return try {
            SenderGroup.toJson(
                SenderGroup.fromJson(value).map { it.copy(threadIds = emptyList()) }
            )
        } catch (_: Exception) {
            value
        }
    }
}
