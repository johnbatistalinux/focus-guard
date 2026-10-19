package com.focusguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.focusguard.app.domain.TimeProvider
import java.time.LocalDate

/**
 * Abstração mínima de chave-valor para desacoplar a lógica de serialização e lazy-reset
 * do framework Android, permitindo testes unitários diretos na JVM sem Robolectric.
 */
interface KeyValueBackend {
    fun getStringSet(key: String, defaultValues: Set<String>): Set<String>
    fun putStringSet(key: String, values: Set<String>)

    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}

/**
 * Implementação de produção baseada diretamente no [SharedPreferences] nativo do Android.
 * Usa `apply()` para gravação assíncrona em memória sem bloquear a Main Thread.
 */
class SharedPreferencesBackend(
    private val prefs: SharedPreferences
) : KeyValueBackend {

    override fun getStringSet(key: String, defaultValues: Set<String>): Set<String> {
        val stored = prefs.getStringSet(key, null) ?: return defaultValues
        return HashSet(stored)
    }

    override fun putStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, HashSet(values)).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, null) ?: defaultValue

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "focus_guard_prefs"

        fun create(context: Context): SharedPreferencesBackend {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_FILE_NAME,
                Context.MODE_PRIVATE
            )
            return SharedPreferencesBackend(prefs)
        }
    }
}

/**
 * Provedor de tempo real do sistema Android usando relógio monotônico (`elapsedRealtime`)
 * e data local (`LocalDate.now().toEpochDay()`).
 */
object SystemTimeProvider : TimeProvider {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun currentEpochDay(): Long = LocalDate.now().toEpochDay()
}
