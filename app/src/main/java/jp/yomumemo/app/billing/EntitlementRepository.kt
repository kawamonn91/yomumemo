package jp.yomumemo.app.billing

import jp.yomumemo.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** 課金で解放される機能。 */
enum class PremiumFeature {
    UNLIMITED_BOOKS,
    EXPORT,
    STATISTICS,
    QUOTE_OCR,
    DRIVE_SYNC,
}

/**
 * 機能が使えるかどうかを判断する唯一の窓口。
 *
 * 画面やビューモデルは Play Billing を直接見ない。判定をここに閉じ込めてあるので、
 * 将来サブスクリプション商品を追加する場合も、購入状態の求め方をここで変えるだけで済み、
 * UI や機能ゲートには手を入れずに済む。
 *
 * 現在の商品は買い切り1点のみ。
 */
class EntitlementRepository(
    private val settings: SettingsStore,
) {

    /**
     * プレミアムが有効か。
     *
     * T-tech Store経由の配布ではGoogle Play Billingでの購入処理自体が成立しない
     * (Playストア以外からインストールしたアプリはPlay Billingの決済を完了できない)。
     * 買えないのに機能を制限したままにする理由が無いため、この配布では常に全機能を
     * 無料で使えるようにする(指示: 「Store内のアプリは無料で使えるようにしてほしい」)。
     */
    val isPremium: Flow<Boolean> = flowOf(true)

    fun canUse(feature: PremiumFeature): Flow<Boolean> = isPremium.map { premium ->
        when (feature) {
            PremiumFeature.UNLIMITED_BOOKS,
            PremiumFeature.EXPORT,
            PremiumFeature.STATISTICS,
            PremiumFeature.QUOTE_OCR,
            PremiumFeature.DRIVE_SYNC,
            -> premium
        }
    }

    fun bookLimit(isPremium: Boolean): Int? = Companion.bookLimit(isPremium)

    fun remainingBooks(isPremium: Boolean, currentCount: Int): Int? =
        Companion.remainingBooks(isPremium, currentCount)

    /**
     * Play への問い合わせ結果を反映する。
     * 確認できなかった場合は控えている状態に触らない。通信できないだけで
     * 購入者の機能を取り上げてしまうのを避けるため。
     */
    suspend fun applyOwnership(check: OwnershipCheck) {
        when (check) {
            is OwnershipCheck.Known -> settings.setPremium(check.owned)
            OwnershipCheck.Unavailable -> Unit
        }
    }

    suspend fun grantFromPurchase(owned: Boolean) {
        if (owned) settings.setPremium(true)
    }

    companion object {
        const val FREE_BOOK_LIMIT = 3

        /**
         * 無料版で登録できる冊数の上限。プレミアムなら null(無制限)。
         *
         * メモの数は無料版でも制限しない。書き留めること自体を止めてしまうと
         * このアプリの存在意義が無くなるため、制限するのは冊数だけにしている。
         *
         * 保存状態に依存しない純粋な判断なので、companion に置いて
         * DataStore を用意せずに検証できるようにしてある。
         */
        fun bookLimit(isPremium: Boolean): Int? = if (isPremium) null else FREE_BOOK_LIMIT

        /** あと何冊登録できるか。プレミアムなら null(無制限)。 */
        fun remainingBooks(isPremium: Boolean, currentCount: Int): Int? =
            bookLimit(isPremium)?.let { (it - currentCount).coerceAtLeast(0) }
    }
}
