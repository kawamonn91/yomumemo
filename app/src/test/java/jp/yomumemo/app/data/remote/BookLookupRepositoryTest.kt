package jp.yomumemo.app.data.remote

import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.domain.LookupResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLookupRepositoryTest {

    private class FakeEnrichment(
        private val result: BookMetadata?,
    ) : MetadataEnrichmentSource {
        var callCount = 0
        override suspend fun lookup(isbn13: String): BookMetadata? {
            callCount++
            return result
        }
    }

    private fun openBdReturning(body: String?) = OpenBdApi(FakeHttpFetcher(default = body))

    @Test
    fun `openBD の書誌に補完元の表紙とページ数だけを足す`() = runTest {
        val enrichment = FakeEnrichment(
            BookMetadata(
                isbn13 = "9784873115658",
                title = "Readable Code",            // openBD 側を上書きしてはいけない
                authors = listOf("Wrong Author"),   // 同上
                coverUrl = "https://example.com/cover.jpg",
                pageCount = 260,
                description = "説明文",
            ),
        )
        val repo = BookLookupRepository(openBdReturning(loadResource("openbd_readable_code.json")), enrichment)

        val result = repo.lookup("9784873115658")
        assertTrue(result is LookupResult.Found)
        val meta = (result as LookupResult.Found).metadata

        // 書誌情報は和書に強い openBD を優先
        assertEquals("リーダブルコード", meta.title)
        assertEquals(listOf("Dustin Boswell", "Trevor Foucher", "角征典"), meta.authors)
        // 欠けていた項目だけ補完元から
        assertEquals("https://example.com/cover.jpg", meta.coverUrl)
        assertEquals(260, meta.pageCount)
        assertEquals("説明文", meta.description)
    }

    @Test
    fun `openBD が該当なしでも補完元が持っていれば登録できる`() = runTest {
        val enrichment = FakeEnrichment(
            BookMetadata(isbn13 = "9784041026618", title = "補完元だけが知っている本"),
        )
        val repo = BookLookupRepository(openBdReturning(loadResource("openbd_notfound.json")), enrichment)

        val result = repo.lookup("9784041026618")
        assertTrue(result is LookupResult.Found)
        assertEquals("補完元だけが知っている本", (result as LookupResult.Found).metadata.title)
    }

    @Test
    fun `どちらにも無ければ NotFound を返し手動入力へ回す`() = runTest {
        val repo = BookLookupRepository(openBdReturning(loadResource("openbd_notfound.json")), FakeEnrichment(null))
        assertEquals(LookupResult.NotFound, repo.lookup("9784041026618"))
    }

    @Test
    fun `補完元が未設定でもアプリは動く`() = runTest {
        val repo = BookLookupRepository(openBdReturning(loadResource("openbd_readable_code.json")), enrichment = null)

        val result = repo.lookup("9784873115658")
        assertTrue(result is LookupResult.Found)
        assertEquals("リーダブルコード", (result as LookupResult.Found).metadata.title)
    }

    @Test
    fun `ISBN として解釈できない入力は通信せず NotFound`() = runTest {
        val fetcher = FakeHttpFetcher(default = loadResource("openbd_readable_code.json"))
        val repo = BookLookupRepository(OpenBdApi(fetcher), null)

        assertEquals(LookupResult.NotFound, repo.lookup("これはISBNではない"))
        assertTrue("無効な入力で通信してはいけない", fetcher.requestedUrls.isEmpty())
    }

    @Test
    fun `ISBN-10 で引いても ISBN-13 に正規化して検索する`() = runTest {
        val fetcher = FakeHttpFetcher(default = loadResource("openbd_readable_code.json"))
        val repo = BookLookupRepository(OpenBdApi(fetcher), null)

        assertTrue(repo.lookup("4873115655") is LookupResult.Found)
        assertTrue(fetcher.requestedUrls.single().contains("9784873115658"))
    }

    @Test
    fun `必要な項目が揃っていれば補完元を呼ばない`() = runTest {
        val complete = """
            [{"summary":{"isbn":"9784873115658","title":"完全な本","publisher":"出版社",
              "pubdate":"202401","cover":"https://example.com/c.jpg","author":"著者,名"},
              "onix":{"DescriptiveDetail":{"Extent":[{"ExtentValue":"300"}]},
              "CollateralDetail":{"TextContent":[{"Text":"内容紹介"}]}}}]
        """.trimIndent()
        val enrichment = FakeEnrichment(BookMetadata(isbn13 = "x", title = "呼ばれないはず"))
        val repo = BookLookupRepository(openBdReturning(complete), enrichment)

        assertTrue(repo.lookup("9784873115658") is LookupResult.Found)
        assertEquals("補完元を無駄に呼んでいる", 0, enrichment.callCount)
    }
}
