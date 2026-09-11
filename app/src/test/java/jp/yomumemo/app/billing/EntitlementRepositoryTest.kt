package jp.yomumemo.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 機能ゲートの判断はここに集約されている。
 * 上限の境界を間違えると、無料のまま無制限に使えたり、逆に購入者が使えなくなる。
 */
class EntitlementRepositoryTest {

    @Test
    fun `無料版には冊数の上限がある`() {
        assertEquals(EntitlementRepository.FREE_BOOK_LIMIT, EntitlementRepository.bookLimit(isPremium = false))
    }

    @Test
    fun `プレミアムは無制限`() {
        assertNull(EntitlementRepository.bookLimit(isPremium = true))
    }

    @Test
    fun `残り冊数を正しく数える`() {
        assertEquals(3, EntitlementRepository.remainingBooks(isPremium = false, currentCount = 0))
        assertEquals(1, EntitlementRepository.remainingBooks(isPremium = false, currentCount = 2))
        assertEquals(0, EntitlementRepository.remainingBooks(isPremium = false, currentCount = 3))
    }

    @Test
    fun `上限を超えていても残りは負にならない`() {
        // 購入後に解約はできない買い切りだが、無料版へ戻る経路(返金)は存在しうる
        assertEquals(0, EntitlementRepository.remainingBooks(isPremium = false, currentCount = 99))
    }

    @Test
    fun `プレミアムの残り冊数は無制限を表す null`() {
        assertNull(EntitlementRepository.remainingBooks(isPremium = true, currentCount = 100))
    }

    @Test
    fun `すべてのプレミアム機能は購入前は使えない`() {
        // 新しい機能を足したときにゲートを付け忘れると、ここで気づける
        assertEquals(5, PremiumFeature.entries.size)
    }
}
