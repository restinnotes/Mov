package moe.ouom.neriplayer.data.search

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.ouom.neriplayer.core.logging.NPLogger

private const val TAG = "ClassicalSearchPreference"
private val DefaultModeKey = stringPreferencesKey("classical_default_search_mode_v1")

private val Context.classicalSearchDataStore by preferencesDataStore(
    name = "classical_search",
    corruptionHandler = ReplaceFileCorruptionHandler {
        NPLogger.e(TAG, "classical search DataStore is corrupted", it)
        emptyPreferences()
    }
)

/**
 * Classical 默认搜索（Subagent 04 合约）。
 * 干净安装默认 BILIBILI；用户可切换为 AGGREGATE / YOUTUBE。
 */
enum class ClassicalDefaultSearchMode {
    BILIBILI,
    AGGREGATE,
    YOUTUBE;

    internal fun storageName(): String = name

    companion object {
        internal fun fromStorage(raw: String?): ClassicalDefaultSearchMode? =
            values().firstOrNull { it.name == raw }
    }
}

/**
 * 归一化默认搜索：未知值回 BILIBILI；YOUTUBE 不可用（stale 偏好）时回 BILIBILI。
 * 纯函数，可单测。
 */
internal fun normalizeDefaultSearchMode(
    raw: String?,
    youtubeAvailable: Boolean
): ClassicalDefaultSearchMode {
    val mode = ClassicalDefaultSearchMode.fromStorage(raw) ?: ClassicalDefaultSearchMode.BILIBILI
    if (mode == ClassicalDefaultSearchMode.YOUTUBE && !youtubeAvailable) {
        return ClassicalDefaultSearchMode.BILIBILI
    }
    return mode
}

class ClassicalSearchPreference(context: Context) {
    private val appContext = context.applicationContext

    val defaultModeFlow: Flow<ClassicalDefaultSearchMode> =
        appContext.classicalSearchDataStore.data.map { prefs ->
            // 可用性门由调用方传入当前值做二次归一化；此处只做存储层解析。
            ClassicalDefaultSearchMode.fromStorage(prefs[DefaultModeKey])
                ?: ClassicalDefaultSearchMode.BILIBILI
        }

    suspend fun setDefaultMode(mode: ClassicalDefaultSearchMode) {
        appContext.classicalSearchDataStore.edit { prefs ->
            prefs[DefaultModeKey] = mode.storageName()
        }
    }
}
