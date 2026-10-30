package com.focusguard.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.focusguard.app.domain.FocusPolicyEngine
import com.focusguard.app.domain.TimeProvider
import java.time.LocalDate

/**
 * Editor em lote para gravar múltiplas chaves em uma única transação (`SharedPreferences.Editor.apply()`),
 * evitando alocações repetitivas de objetos Editor a cada segundo.
 */
interface BatchEditor {
    fun putStringSet(key: String, values: Set<String>)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
}

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

    fun editBatch(block: BatchEditor.() -> Unit) {
        val fallbackEditor = object : BatchEditor {
            override fun putStringSet(key: String, values: Set<String>) = this@KeyValueBackend.putStringSet(key, values)
            override fun putInt(key: String, value: Int) = this@KeyValueBackend.putInt(key, value)
            override fun putLong(key: String, value: Long) = this@KeyValueBackend.putLong(key, value)
            override fun putBoolean(key: String, value: Boolean) = this@KeyValueBackend.putBoolean(key, value)
            override fun putString(key: String, value: String) = this@KeyValueBackend.putString(key, value)
        }
        fallbackEditor.block()
    }
}

/**
 * Implementação de produção baseada diretamente no [SharedPreferences] nativo do Android.
 * Usa `apply()` em lote para gravação assíncrona em memória sem bloquear a Main Thread.
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

    override fun editBatch(block: BatchEditor.() -> Unit) {
        val editor = prefs.edit()
        val localBatch = object : BatchEditor {
            override fun putStringSet(key: String, values: Set<String>) {
                editor.putStringSet(key, HashSet(values))
            }

            override fun putInt(key: String, value: Int) {
                editor.putInt(key, value)
            }

            override fun putLong(key: String, value: Long) {
                editor.putLong(key, value)
            }

            override fun putBoolean(key: String, value: Boolean) {
                editor.putBoolean(key, value)
            }

            override fun putString(key: String, value: String) {
                editor.putString(key, value)
            }
        }
        localBatch.block()
        editor.apply()
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
    override fun currentMinuteOfDay(): Int {
        val now = java.time.LocalTime.now()
        return now.hour * 60 + now.minute
    }
}

/**
 * Singleton ultraleve de processo que compartilha a mesma instância de [FocusPreferencesStore]
 * e [FocusPolicyEngine] entre `FocusAccessibilityService` e `MainActivity`.
 */
object FocusRuntime {
    @Volatile
    private var sharedStore: FocusPreferencesStore? = null

    @Volatile
    private var sharedEngine: FocusPolicyEngine? = null

    fun getStore(context: Context): FocusPreferencesStore {
        sharedStore?.let { return it }
        synchronized(this) {
            sharedStore?.let { return it }
            val created = FocusPreferencesStore(SharedPreferencesBackend.create(context))
            sharedStore = created
            return created
        }
    }

    fun getEngine(context: Context): FocusPolicyEngine {
        sharedEngine?.let { return it }
        synchronized(this) {
            sharedEngine?.let { return it }
            val created = FocusPolicyEngine(
                store = getStore(context),
                timeProvider = SystemTimeProvider
            )
            sharedEngine = created
            return created
        }
    }
}
