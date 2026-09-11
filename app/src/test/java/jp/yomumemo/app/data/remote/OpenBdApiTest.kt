package jp.yomumemo.app.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実際の openBD レスポンスを固定データとして使う。
 * 想像した形ではなく本物の構造に対して検証するため。
 */
class OpenBdApiTest {

    private fun apiReturning(body: String?) =
        OpenBdApi(FakeHttpFetcher(default = body))

    @Test
    fun `実レスポンスからタイトルと副題を分離する`() = runTest {
        val api = apiReturning(loadResource("openbd_readable_code.json"))
        val meta = checkNotNull(api.lookup("9784873115658"))

        assertEquals("リーダブルコード", meta.title)
        assertEquals("より良いコードを書くためのシンプルで実践的なテクニック", meta.subtitle)
    }

    @Test
    fun `ONIX の Contributor から著者を個別に取り出す`() = runTest {
        val api = apiReturning(loadResource("openbd_readable_code.json"))
        val meta = checkNotNull(api.lookup("9784873115658"))

        // "Boswell, Dustin" は欧文なので姓名を戻し、"角, 征典" は和名なので連結する
        assertEquals(listOf("Dustin Boswell", "Trevor Foucher", "角征典"), meta.authors)
    }

    @Test
    fun `出版社とシリーズと刊行年月を読む`() = runTest {
        val api = apiReturning(loadResource("openbd_readable_code.json"))
        val meta = checkNotNull(api.lookup("9784873115658"))

        assertEquals("オーム社", meta.publisher)
        assertEquals("THEORY/IN/PRACTICE", meta.series)
        assertEquals("2012-06", meta.publishedDate)
    }

    @Test
    fun `生没年つきの著者名から年を落とす`() = runTest {
        val api = apiReturning(loadResource("openbd_wagahai.json"))
        val meta = checkNotNull(api.lookup("9784101010014"))

        assertEquals("吾輩は猫である", meta.title)
        assertTrue("生没年が著者名に混入している: ${meta.authors}", meta.authors.none { it.contains("1867") })
        assertTrue("夏目漱石 が取れていない: ${meta.authors}", meta.authors.any { it.contains("夏目") })
    }

    @Test
    fun `openBD は表紙をほとんど持たないので null になりうる`() = runTest {
        val api = apiReturning(loadResource("openbd_readable_code.json"))
        val meta = checkNotNull(api.lookup("9784873115658"))

        // この実レコードには表紙が登録されていない (実測で約94%がこの状態)
        assertNull(meta.coverUrl)
    }

    @Test
    fun `該当なしのレスポンス null要素 を安全に扱う`() = runTest {
        val api = apiReturning(loadResource("openbd_notfound.json"))
        assertNull(api.lookup("9784041026618"))
    }

    @Test
    fun `通信失敗時は null を返す`() = runTest {
        assertNull(apiReturning(null).lookup("9784873115658"))
    }

    @Test
    fun `壊れた JSON でも例外を投げない`() = runTest {
        assertNull(apiReturning("これはJSONではない").lookup("9784873115658"))
        assertNull(apiReturning("[]").lookup("9784873115658"))
        assertNull(apiReturning("""[{"summary":{}}]""").lookup("9784873115658"))
    }
}
