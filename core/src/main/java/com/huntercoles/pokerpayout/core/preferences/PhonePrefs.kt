package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * PP-137: the one SharedPreferences file about this phone rather than the night (`phone_prefs`):
 * the music playlist's picked songs and where one was paused, which are loans from this phone's
 * file picker and can't play on another phone. Android's own backup and phone-to-phone transfer
 * leave this file out (res/xml/data_extraction_rules.xml and backup_rules.xml), and so does the
 * in-app backup (core/backup/BackupCatalog.PHONE_FILES), so a new or restored phone starts it empty.
 *
 * Android's backup rules can only leave out whole files, so a key that belongs here lives here,
 * under the name it always had; [moveOnce] brings it over from the file it was saved in before.
 */
object PhonePrefs {
    const val FILE = "phone_prefs"

    fun open(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Moves [keys] from [from] (where an older version saved them) to [to], keeping each key's name,
     * type and value; a no-op once they have moved. If [to] already has a key, its value stays
     * (it's this phone's own) and the old copy goes. The new copies are on disk before the old ones
     * are removed, so a move cut short loses nothing and finishes on the next start.
     */
    fun moveOnce(from: SharedPreferences, to: SharedPreferences, keys: Set<String>) {
        val old = from.all.filterKeys { it in keys }
        if (old.isEmpty()) return
        val copy = to.edit()
        old.filterKeys { !to.contains(it) }.forEach { (key, value) -> copy.put(key, value) }
        copy.commit()
        val remove = from.edit()
        old.keys.forEach { remove.remove(it) }
        remove.commit()
    }

    @Suppress("UNCHECKED_CAST") // SharedPreferences holds a set of strings only
    private fun SharedPreferences.Editor.put(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }
}
