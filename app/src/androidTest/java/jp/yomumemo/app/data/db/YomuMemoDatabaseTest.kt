package jp.yomumemo.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.repo.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 実際の SQLite に対して Room の挙動を確かめる。
 *
 * 単体テストでは DAO を差し替えているため、FTS の実挙動・外部キーのカスケード削除・
 * 型変換といった「SQLite が実際にどう動くか」に依存する部分は検証できない。
 * 特に日本語の全文検索は、実物で確かめないと意味がない。
 */
@RunWith(AndroidJUnit4::class)
class YomuMemoDatabaseTest {

    private lateinit var db: YomuMemoDatabase
    private lateinit var bookDao: BookDao
    private lateinit var noteDao: NoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YomuMemoDatabase::class.java,
        ).build()
        bookDao = db.bookDao()
        noteDao = db.noteDao()
    }

    @After
    fun tearDown() = db.close()

    private fun book(id: String, title: String, isbn: String? = null) = BookEntity(
        id = id,
        isbn13 = isbn,
        title = title,
        authors = listOf("著者A", "Dustin Boswell"),
        status = ReadingStatus.READING,
        addedAt = 1L,
        updatedAt = 1L,
    )

    private fun note(id: String, bookId: String, quote: String = "", comment: String = "") =
        NoteEntity(
            id = id,
            bookId = bookId,
            type = NoteType.QUOTE,
            page = 10,
            quote = quote,
            comment = comment,
            createdAt = 1L,
            updatedAt = 1L,
        )

    @Test
    fun 著者リストがJSONとして往復する() = runTest {
        bookDao.insert(book("b1", "リーダブルコード"))
        val loaded = checkNotNull(bookDao.findById("b1"))
        assertEquals(listOf("著者A", "Dustin Boswell"), loaded.authors)
        assertEquals(ReadingStatus.READING, loaded.status)
    }

    @Test
    fun 同じISBNは一意制約で重複登録できない() = runTest {
        bookDao.insert(book("b1", "本A", isbn = "9784873115658"))
        val failed = runCatching {
            bookDao.insert(book("b2", "本B", isbn = "9784873115658"))
        }.isFailure
        assertTrue("ISBN の一意制約が効いていない", failed)
    }

    @Test
    fun 本を物理削除するとメモも消える() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", comment = "メモ"))

        // 外部キーのカスケード削除は Room の既定では無効。実際に効くかを確かめる。
        db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id = 'b1'")

        assertNull(noteDao.findById("n1"))
    }

    @Test
    fun FTSテーブルが本文と同期する() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", quote = "readable code matters"))

        val hits = noteDao.search("readable*").first()
        assertEquals(1, hits.size)
        assertEquals("n1", hits.first().id)
    }

    @Test
    fun FTSは更新にも追随する() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", quote = "before"))
        noteDao.upsert(note("n1", "b1", quote = "after"))

        assertTrue(noteDao.search("before*").first().isEmpty())
        assertEquals(1, noteDao.search("after*").first().size)
    }

    /**
     * 日本語と FTS4 の実際の関係。
     *
     * SQLite の simple トークナイザは ASCII の英数字しか語の区切りを知らないため、
     * 空白を含まない日本語の連なりは「まるごと1語」として索引される。
     * その結果:
     *   - 文頭からの前方一致は引ける (1語の先頭と一致するため)
     *   - 語の途中は引けない
     * 検索語が文頭に来る保証はないので、日本語は LIKE に回す必要がある。
     */
    @Test
    fun 日本語のFTSは前方一致だけ効く() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", quote = "変数名は短すぎてはいけない"))

        // 文頭からの前方一致は成立する
        assertEquals(1, noteDao.search("変数名*").first().size)

        // 語の途中は引けない。ここが LIKE を使う理由。
        assertTrue(
            "途中一致が FTS で引けるなら LIKE への切り替えは不要になる。前提を見直すこと。",
            noteDao.search("短すぎ*").first().isEmpty(),
        )
    }

    @Test
    fun 語の途中にある日本語もLIKEなら引ける() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", quote = "変数名は短すぎてはいけない"))

        // FTS で引けなかった語が LIKE では引ける
        assertEquals(1, noteDao.searchLike("短すぎ").first().size)
    }

    @Test
    fun 日本語はLIKEの部分一致で引ける() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", quote = "変数名は短すぎてはいけない"))

        val hits = noteDao.searchLike("変数名").first()
        assertEquals(1, hits.size)
    }

    @Test
    fun リポジトリ経由の検索が日本語と英語の両方で機能する() = runTest {
        val repo = NoteRepository(noteDao, now = { 1L }, newId = { "n-generated" })
        bookDao.insert(book("b1", "本A"))
        repo.create("b1", NoteType.QUOTE, 1, "設計は分かりやすさが第一", "")

        assertEquals(1, repo.search("分かりやすさ").first().size)
        assertTrue(repo.search("存在しない語").first().isEmpty())
    }

    @Test
    fun メモはページ順に並ぶ() = runTest {
        bookDao.insert(book("b1", "本A"))
        noteDao.upsert(note("n1", "b1", comment = "後").copy(page = 50))
        noteDao.upsert(note("n2", "b1", comment = "先").copy(page = 10))
        noteDao.upsert(note("n3", "b1", comment = "末尾").copy(page = null))

        val order = noteDao.observeForBook("b1").first().map { it.comment }
        assertEquals(listOf("先", "後", "末尾"), order)
    }
}
