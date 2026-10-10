package com.focal.android.settings

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.focalDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "focal_device")

enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object FocalDevicePreferences {

    private val KEY_DEVICE_NAME = stringPreferencesKey("device_display_name")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    @Volatile
    var cachedDisplayName: String? = null

    fun defaultDeviceName(): String = Build.MODEL.ifBlank { "Focal Phone" }

    fun deviceNameFlow(context: Context): Flow<String> =
        context.focalDeviceDataStore.data.map { prefs ->
            prefs[KEY_DEVICE_NAME]?.trim()?.takeIf { it.isNotEmpty() } ?: defaultDeviceName()
        }

    fun themeModeFlow(context: Context): Flow<AppThemeMode> =
        context.focalDeviceDataStore.data.map { prefs ->
            AppThemeMode.fromStorage(prefs[KEY_THEME_MODE])
        }

    suspend fun setDeviceName(context: Context, name: String) {
        val trimmed = name.trim().ifBlank { defaultDeviceName() }
        cachedDisplayName = trimmed
        context.focalDeviceDataStore.edit { it[KEY_DEVICE_NAME] = trimmed }
    }

    suspend fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.focalDeviceDataStore.edit { it[KEY_THEME_MODE] = mode.storageValue }
    }

    fun resolveDisplayName(context: Context): String {
        cachedDisplayName?.let { return it }
        return runBlocking {
            context.focalDeviceDataStore.data.first()[KEY_DEVICE_NAME]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: defaultDeviceName()
        }.also { cachedDisplayName = it }
    }

    /** mDNS-safe service name (LocalSend-style visible label). */
    fun mDnsServiceName(displayName: String): String {
        val clean = displayName
            .trim()
            .replace(' ', '-')
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(40)
        val base = if (clean.isBlank()) defaultDeviceName().replace(' ', '-') else clean
        return "Focal-$base"
    }
}
