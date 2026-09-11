package jp.yomumemo.app.data.remote

import jp.yomumemo.app.domain.LookupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実際の openBD へ繋いで書誌が引けることを端末上で確かめる。
 *
 * 単体テストは記録済みのレスポンスに対して解析を検証しているが、それだけでは
 * 「端末から本当に通信できるか」は分からない。HTTPS の設定、ネットワーク権限、
 * R8 後の OkHttp の挙動といった、実機でしか現れない失敗がある。
 *
 * 外部サービスに依存するため、通信できない環境では失敗する。
 * openBD が落ちているときに CI を止めたくない場合は、このクラスを
 * 通常の実行から外して手動確認用にすること。
 */
class BookLookupNetworkTest {

    private val repository = BookLookupRepository(
        openBd = OpenBdApi(OkHttpFetcher(OkHttpFetcher.defaultClient())),
        // 補完元はキーが要るのでここでは使わない。openBD 単独で書誌が引けることを見る。
        enrichment = null,
    )

    @Test
    fun 実在するISBNから書誌を取得できる() = runBlocking {
        // 『リーダブルコード』(オーム社)
        val result = repository.lookup("9784873115658")

        assertTrue("書誌を取得できなかった: " + result, result is LookupResult.Found)
        val meta = (result as LookupResult.Found).metadata

        assertEquals("9784873115658", meta.isbn13)
        assertEquals("リーダブルコード", meta.title)
        assertEquals("オーム社", meta.publisher)
        assertTrue("著者が取れていない: " + meta.authors, meta.authors.any { it.contains("角") })
    }

    @Test
    fun ハイフン付きのISBNでも引ける() = runBlocking {
        val result = repository.lookup("978-4-87311-565-8")
        assertTrue(result is LookupResult.Found)
        assertEquals("リーダブルコード", (result as LookupResult.Found).metadata.title)
    }

    @Test
    fun ISBN10を13に正規化して引ける() = runBlocking {
        val result = repository.lookup("4873115655")
        assertTrue(result is LookupResult.Found)
        assertEquals("9784873115658", (result as LookupResult.Found).metadata.isbn13)
    }

    @Test
    fun 存在しないISBNはNotFoundになる() = runBlocking {
        // チェックディジットは正しいが、割り当てられていない番号
        val result = repository.lookup("9784000000000")
        assertEquals(LookupResult.NotFound, result)
    }

    @Test
    fun ISBNとして不正な入力は通信せずNotFoundになる() = runBlocking {
        assertEquals(LookupResult.NotFound, repository.lookup("これはISBNではない"))
    }
}
