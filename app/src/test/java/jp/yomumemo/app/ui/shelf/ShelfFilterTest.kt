package jp.yomumemo.app.ui.shelf

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfFilterTest {

    private val book1 = BookEntity(id = "1", title = "Book 1", status = ReadingStatus.READING, folderId = "f1", addedAt = 1L, updatedAt = 1L)
    private val book2 = BookEntity(id = "2", title = "Book 2", status = ReadingStatus.DONE, folderId = "f1", addedAt = 1L, updatedAt = 1L)
    private val book3 = BookEntity(id = "3", title = "Book 3", status = ReadingStatus.READING, folderId = null, addedAt = 1L, updatedAt = 1L)
    private val book4 = BookEntity(id = "4", title = "Book 4", status = ReadingStatus.WANT, folderId = "f2", addedAt = 1L, updatedAt = 1L)

    private val allBooks = listOf(book1, book2, book3, book4)

    @Test
    fun `すべての本を取得（フィルタなし）`() {
        val result = filterBooks(allBooks, status = null, folder = FolderFilter.All)
        assertEquals(allBooks, result)
    }

    @Test
    fun `読書状態で絞り込む`() {
        val result = filterBooks(allBooks, status = ReadingStatus.READING, folder = FolderFilter.All)
        assertEquals(listOf(book1, book3), result)
    }

    @Test
    fun `特定フォルダで絞り込む`() {
        val result = filterBooks(allBooks, status = null, folder = FolderFilter.Specific("f1"))
        assertEquals(listOf(book1, book2), result)
    }

    @Test
    fun `未分類の本を絞り込む`() {
        val result = filterBooks(allBooks, status = null, folder = FolderFilter.Unassigned)
        assertEquals(listOf(book3), result)
    }

    @Test
    fun `読書状態と特定フォルダの組み合わせで絞り込む`() {
        val result = filterBooks(allBooks, status = ReadingStatus.READING, folder = FolderFilter.Specific("f1"))
        assertEquals(listOf(book1), result)
    }

    @Test
    fun `該当なしの場合は空リスト`() {
        val result = filterBooks(allBooks, status = ReadingStatus.PAUSED, folder = FolderFilter.All)
        assertEquals(emptyList<BookEntity>(), result)
    }
}
