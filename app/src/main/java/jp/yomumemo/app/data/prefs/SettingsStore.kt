package jp.yomumemo.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "yomumemo_settings")

/**
 * 端末に保存する設定。
 *
 * プレミアムの購入状態もここに控える。Play に問い合わせられない状況
 * (機内モード、圏外、Play 開発者サービスの不調) で購入済みの人が
 * 機能を使えなくなるのを避けるため。
 * 正としては毎回 Play に確認し、確認できたときだけ書き換える。
 */
class SettingsStore(private val context: Context) {

    val isPremiumCached: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PREMIUM] ?: false }

    val lastSyncedAt: Flow<Long?> =
        context.dataStore.data.map { it[KEY_LAST_SYNCED] }

    suspend fun setPremium(value: Boolean) {
        context.dataStore.edit { it[KEY_PREMIUM] = value }
    }

    suspend fun setLastSyncedAt(value: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNCED] = value }
    }

    private companion object {
        val KEY_PREMIUM = booleanPreferencesKey("is_premium")
        val KEY_LAST_SYNCED = longPreferencesKey("last_synced_at")
    }
}
