package jp.yomumemo.app.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookEntityTest {
    private fun book(coverUrl: String? = null, localCoverPath: String? = null) = BookEntity(
        id = "b1",
        title = "本A",
        coverUrl = coverUrl,
        localCoverPath = localCoverPath,
        addedAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `自分で撮影した表紙があればそちらを優先する`() {
        val book = book(coverUrl = "https://example.com/cover.jpg", localCoverPath = "/data/covers/b1.jpg")
        assertEquals("file:///data/covers/b1.jpg", book.displayCoverUrl)
    }

    @Test
    fun `自分で撮影した表紙が無ければ書誌データの表紙を使う`() {
        val book = book(coverUrl = "https://example.com/cover.jpg")
        assertEquals("https://example.com/cover.jpg", book.displayCoverUrl)
    }

    @Test
    fun `どちらも無ければnull(プレースホルダ表示になる)`() {
        assertNull(book().displayCoverUrl)
    }
}
