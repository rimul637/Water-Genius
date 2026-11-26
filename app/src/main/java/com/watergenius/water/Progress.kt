package com.watergenius.water

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.waterProgress by preferencesDataStore("water_genius")

internal data class Progress(
    val best: Map<Int, Int> = emptyMap(),
    val music: Boolean = true,
    val effects: Boolean = true
) {
    val next: Int get() = (1..50).firstOrNull { it !in best } ?: 50
    val total: Int get() = best.values.sum()
    fun unlocked(level: Int) = level in 1..next
}

internal class ProgressStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.waterProgress)
    private val music = booleanPreferencesKey("music")
    private val effects = booleanPreferencesKey("effects")
    private val levels = (1..50).associateWith { intPreferencesKey("level_$it") }

    val progress = store.data.map { saved ->
        Progress(
            best = levels.mapNotNull { (level, key) -> saved[key]?.takeIf { it in 1..3 }?.let { level to it } }.toMap(),
            music = saved[music] ?: true,
            effects = saved[effects] ?: true
        )
    }

    suspend fun complete(level: Int, stars: Int) {
        val key = levels.getValue(level)
        store.edit { saved ->
            saved[key] = maxOf(saved[key] ?: 0, stars)
        }
    }

    suspend fun toggleMusic() { store.edit { it[music] = !(it[music] ?: true) } }
    suspend fun toggleEffects() { store.edit { it[effects] = !(it[effects] ?: true) } }
}
