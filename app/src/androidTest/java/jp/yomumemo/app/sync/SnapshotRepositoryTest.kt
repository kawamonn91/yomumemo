package jp.yomumemo.app.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.yomumemo.app.data.db.YomuMemoDatabase
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * バックアップと復元を実際の SQLite に対して確かめる。
 *
 * ここが壊れると利用者のメモが失われる。外部キー制約や型変換など、
 * 実物でないと現れない失敗があるため、実機上で通しの往復を検証する。
 */
@RunWith(AndroidJUnit4::class)
class SnapshotRepositoryTest {

    private lateinit var db: YomuMemoDatabase
    private lateinit var repo: SnapshotRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuMemoDatabase::class.java,
        ).build()
        repo = SnapshotRepository(db.bookDao(), db.noteDao(), now = { clock })
    }

    @After
    fun tearDown() = db.close()

    private fun book(id: String, title: String = "本" + id, updatedAt: Long = 1L, deletedAt: Long? = null) =
        BookEntity(
            id = id,
            title = title,
            authors = listOf("著者", "Someone Else"),
            status = ReadingStatus.READING,
            addedAt = 1L,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
        )

    private fun note(id: String, bookId: String, comment: String = "メモ", updatedAt: Long = 1L) =
        NoteEntity(
            id = id,
            bookId = bookId,
            type = NoteType.QUOTE,
            page = 12,
            quote = "引用文",
            comment = comment,
            createdAt = 1L,
            updatedAt = updatedAt,
        )

    @Test
    fun 書き出して読み直すと内容が保たれる() = runTest {
        db.bookDao().insert(book("b1"))
        db.noteDao().upsert(note("n1", "b1"))

        val json = repo.encode(repo.capture())
        val decoded = checkNotNull(repo.decode(json))

        assertEquals(1, decoded.books.size)
        assertEquals(1, decoded.notes.size)
        assertEquals(listOf("著者", "Someone Else"), decoded.books.single().authors)
        assertEquals("引用文", decoded.notes.single().quote)
        assertEquals(12, decoded.notes.single().page)
    }

    @Test
    fun 削除済みの記録もバックアップに含める() = runTest {
        db.bookDao().insert(book("b1"))
        db.bookDao().softDelete("b1", 5L)

        val snapshot = repo.capture()
        // 含めないと、別端末で削除が伝わらず本が復活する
        assertNotNull(snapshot.books.single().deletedAt)
    }

    @Test
    fun 相手にしか無いデータを取り込む() = runTest {
        val incoming = Snapshot(
            exportedAt = 2L,
            books = listOf(book("b1").toSnapshot()),
            notes = listOf(note("n1", "b1").toSnapshot()),
        )

        val result = repo.merge(incoming)

        assertEquals(1, result.booksUpdatedLocally)
        assertNotNull(db.bookDao().findById("b1"))
        assertNotNull(db.noteDao().findById("n1"))
    }

    @Test
    fun 手元の新しい編集は復元で上書きされない() = runTest {
        db.bookDao().insert(book("b1", title = "手元で直した", updatedAt = 100L))

        val incoming = Snapshot(
            exportedAt = 2L,
            books = listOf(book("b1", title = "古いバックアップ", updatedAt = 50L).toSnapshot()),
        )
        repo.merge(incoming)

        assertEquals("手元で直した", db.bookDao().findById("b1")?.title)
    }

    @Test
    fun 本の無いメモは取り込まない() = runTest {
        // 外部キー制約で失敗するうえ、本体の無いメモは画面のどこにも出せない
        val incoming = Snapshot(
            exportedAt = 2L,
            books = emptyList(),
            notes = listOf(note("n1", "存在しない本").toSnapshot()),
        )

        repo.merge(incoming)

        assertNull(db.noteDao().findById("n1"))
    }

    @Test
    fun 壊れたファイルを読んでも落ちない() {
        assertNull(repo.decode("これはJSONではない"))
        assertNull(repo.decode(""))
        assertNull(repo.decode("{}"))
    }

    @Test
    fun 将来の版のバックアップは読み込まない() {
        // 知らない形式を無理に読むと壊れたデータを取り込むことになる
        val future = """{"version":999,"exportedAt":1,"books":[],"notes":[]}"""
        assertNull(repo.decode(future))
    }

    @Test
    fun 復元を繰り返しても件数が増えない() = runTest {
        db.bookDao().insert(book("b1"))
        db.noteDao().upsert(note("n1", "b1"))
        val json = repo.encode(repo.capture())

        repeat(3) { repo.merge(checkNotNull(repo.decode(json))) }

        assertEquals(1, db.bookDao().allIncludingDeleted().size)
        assertEquals(1, db.noteDao().allIncludingDeleted().size)
    }

    @Test
    fun 統合結果をそのまま書き戻せる() = runTest {
        db.bookDao().insert(book("b1"))
        val incoming = Snapshot(exportedAt = 2L, books = listOf(book("b2").toSnapshot()))

        val result = repo.merge(incoming)
        val encoded = repo.encode(result.merged)

        assertTrue(encoded.contains("b1"))
        assertTrue(encoded.contains("b2"))
    }
}
