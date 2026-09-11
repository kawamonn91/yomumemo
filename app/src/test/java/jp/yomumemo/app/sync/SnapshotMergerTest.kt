package jp.yomumemo.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 同期の統合はデータを失う最も危険な場所なので、境界を丁寧に確かめる。
 * 特に「消したはずの本が復活する」「手元の編集が黙って消える」のは
 * 利用者の信頼を最も損なう壊れ方なので、専用のケースを置いている。
 */
class SnapshotMergerTest {

    private fun book(
        id: String,
        title: String = "本" + id,
        updatedAt: Long,
        deletedAt: Long? = null,
        addedAt: Long = 0L,
    ) = BookSnapshot(
        id = id,
        title = title,
        addedAt = addedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun note(
        id: String,
        comment: String = "メモ" + id,
        updatedAt: Long,
        deletedAt: Long? = null,
    ) = NoteSnapshot(
        id = id,
        bookId = "b1",
        comment = comment,
        createdAt = 0L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun snapshot(
        books: List<BookSnapshot> = emptyList(),
        notes: List<NoteSnapshot> = emptyList(),
    ) = Snapshot(exportedAt = 0L, books = books, notes = notes)

    private fun merge(local: Snapshot, remote: Snapshot) =
        SnapshotMerger.merge(local, remote, now = 999L)

    @Test
    fun `両方にしか無いレコードは両方とも残る`() {
        val result = merge(
            snapshot(books = listOf(book("a", updatedAt = 1))),
            snapshot(books = listOf(book("b", updatedAt = 1))),
        )
        assertEquals(setOf("a", "b"), result.merged.books.map { it.id }.toSet())
    }

    @Test
    fun `更新が新しい方の内容を採る`() {
        val result = merge(
            snapshot(books = listOf(book("a", title = "古い", updatedAt = 10))),
            snapshot(books = listOf(book("a", title = "新しい", updatedAt = 20))),
        )
        assertEquals("新しい", result.merged.books.single().title)
    }

    @Test
    fun `手元の方が新しければ相手で上書きしない`() {
        val result = merge(
            snapshot(books = listOf(book("a", title = "手元の編集", updatedAt = 30))),
            snapshot(books = listOf(book("a", title = "古い同期", updatedAt = 20))),
        )
        assertEquals("手元の編集", result.merged.books.single().title)
        assertEquals("手元が勝った場合は更新扱いにしない", 0, result.booksUpdatedLocally)
    }

    @Test
    fun `更新時刻が同着なら手元を残す`() {
        // 端末の時計は揃っていない。同着で相手優先にすると往復のたびに入れ替わる。
        val result = merge(
            snapshot(books = listOf(book("a", title = "手元", updatedAt = 10))),
            snapshot(books = listOf(book("a", title = "相手", updatedAt = 10))),
        )
        assertEquals("手元", result.merged.books.single().title)
    }

    @Test
    fun `相手で削除された本は削除として取り込む`() {
        val result = merge(
            snapshot(books = listOf(book("a", updatedAt = 10))),
            snapshot(books = listOf(book("a", updatedAt = 20, deletedAt = 20))),
        )
        assertNotNull("削除は行ごと消さず記録として残す", result.merged.books.single().deletedAt)
    }

    @Test
    fun `削除より後の編集は削除を取り消す`() {
        val result = merge(
            snapshot(books = listOf(book("a", title = "復活させた", updatedAt = 30))),
            snapshot(books = listOf(book("a", updatedAt = 20, deletedAt = 20))),
        )
        val merged = result.merged.books.single()
        assertNull(merged.deletedAt)
        assertEquals("復活させた", merged.title)
    }

    @Test
    fun `削除済みの本が相手の古い情報で復活しない`() {
        // 消したはずの本が戻ってくるのは、最も信頼を損なう壊れ方
        val result = merge(
            snapshot(books = listOf(book("a", updatedAt = 30, deletedAt = 30))),
            snapshot(books = listOf(book("a", title = "相手の古い状態", updatedAt = 10))),
        )
        assertNotNull(result.merged.books.single().deletedAt)
    }

    @Test
    fun `メモも同じ規則で統合する`() {
        val result = merge(
            snapshot(notes = listOf(note("n1", comment = "古い", updatedAt = 10))),
            snapshot(notes = listOf(note("n1", comment = "新しい", updatedAt = 20))),
        )
        assertEquals("新しい", result.merged.notes.single().comment)
        assertEquals(1, result.notesUpdatedLocally)
    }

    @Test
    fun `相手にしか無いメモは取り込まれる`() {
        val result = merge(
            snapshot(),
            snapshot(notes = listOf(note("n1", updatedAt = 5))),
        )
        assertEquals(1, result.merged.notes.size)
        assertEquals(1, result.notesUpdatedLocally)
    }

    @Test
    fun `空同士でも壊れない`() {
        val result = merge(snapshot(), snapshot())
        assertEquals(0, result.merged.books.size)
        assertEquals(0, result.merged.notes.size)
    }

    @Test
    fun `統合結果に書き出し時刻と版が入る`() {
        val result = merge(snapshot(), snapshot())
        assertEquals(999L, result.merged.exportedAt)
        assertEquals(Snapshot.CURRENT_VERSION, result.merged.version)
    }

    @Test
    fun `3端末を順に統合しても全件そろう`() {
        val a = snapshot(books = listOf(book("a", updatedAt = 1)))
        val b = snapshot(books = listOf(book("b", updatedAt = 1)))
        val c = snapshot(books = listOf(book("c", updatedAt = 1)))

        val ab = merge(a, b).merged
        val abc = merge(ab, c).merged

        assertEquals(setOf("a", "b", "c"), abc.books.map { it.id }.toSet())
    }

    @Test
    fun `統合をもう一度行っても結果が変わらない`() {
        // 同期は何度も走る。回すたびに内容が揺れないことが要る。
        val local = snapshot(books = listOf(book("a", title = "手元", updatedAt = 30)))
        val remote = snapshot(books = listOf(book("a", title = "相手", updatedAt = 20)))

        val once = merge(local, remote).merged
        val twice = merge(once, remote).merged

        assertEquals(once.books, twice.books)
    }
}
