package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.entity.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRepositoryTest {

    private val dao = FakeNoteDao()
    private var clock = 1_000L
    private var idCounter = 0

    private val repo = NoteRepository(
        dao = dao,
        now = { clock },
        newId = { "note-${++idCounter}" },
    )

    @Test
    fun `メモを作ると作成時刻と更新時刻が入る`() = runTest {
        val id = repo.create("book-1", NoteType.QUOTE, 42, "引用文", "感想文")
        val note = checkNotNull(dao.findById(id))

        assertEquals("book-1", note.bookId)
        assertEquals(NoteType.QUOTE, note.type)
        assertEquals(42, note.page)
        assertEquals("引用文", note.quote)
        assertEquals("感想文", note.comment)
        assertEquals(1_000L, note.createdAt)
        assertEquals(1_000L, note.updatedAt)
    }

    @Test
    fun `前後の空白を落として保存する`() = runTest {
        val id = repo.create("book-1", NoteType.THOUGHT, null, "  引用  ", "  感想  ")
        val note = checkNotNull(dao.findById(id))

        assertEquals("引用", note.quote)
        assertEquals("感想", note.comment)
    }

    @Test
    fun `更新しても作成時刻は変わらず更新時刻だけ進む`() = runTest {
        val id = repo.create("book-1", NoteType.THOUGHT, null, "", "最初")
        clock = 5_000L
        repo.update(id, NoteType.QUESTION, 10, "追記した引用", "書き直した感想")

        val note = checkNotNull(dao.findById(id))
        assertEquals(1_000L, note.createdAt)
        assertEquals(5_000L, note.updatedAt)
        assertEquals(NoteType.QUESTION, note.type)
        assertEquals(10, note.page)
    }

    @Test
    fun `存在しないメモの更新は何も起こさない`() = runTest {
        repo.update("存在しないID", NoteType.QUOTE, 1, "a", "b")
        assertNull(dao.findById("存在しないID"))
    }

    @Test
    fun `削除は論理削除で一覧から外れる`() = runTest {
        val id = repo.create("book-1", NoteType.THOUGHT, null, "", "メモ")
        clock = 7_000L
        repo.delete(id)

        assertTrue(repo.observeForBook("book-1").first().isEmpty())
        assertEquals(7_000L, checkNotNull(dao.findById(id)).deletedAt)
    }

    @Test
    fun `本ごとの一覧はページ順に並ぶ`() = runTest {
        repo.create("book-1", NoteType.THOUGHT, 30, "", "三番目")
        repo.create("book-1", NoteType.THOUGHT, 10, "", "一番目")
        repo.create("book-1", NoteType.THOUGHT, 20, "", "二番目")

        val comments = repo.observeForBook("book-1").first().map { it.comment }
        assertEquals(listOf("一番目", "二番目", "三番目"), comments)
    }

    @Test
    fun `ページ番号の無いメモは末尾に置く`() = runTest {
        repo.create("book-1", NoteType.THOUGHT, null, "", "ページなし")
        repo.create("book-1", NoteType.THOUGHT, 5, "", "ページあり")

        val comments = repo.observeForBook("book-1").first().map { it.comment }
        assertEquals(listOf("ページあり", "ページなし"), comments)
    }

    @Test
    fun `別の本のメモは混ざらない`() = runTest {
        repo.create("book-1", NoteType.THOUGHT, 1, "", "こちらの本")
        repo.create("book-2", NoteType.THOUGHT, 1, "", "別の本")

        val notes = repo.observeForBook("book-1").first()
        assertEquals(1, notes.size)
        assertEquals("こちらの本", notes.first().comment)
    }

    // --- 検索の経路選択 ---
    // FTS4 の既定トークナイザは空白区切りのため、単語境界の無い日本語では MATCH が
    // ほぼ機能しない。日本語を含む語は LIKE に回す必要がある。

    @Test
    fun `日本語の検索語は LIKE の部分一致で引く`() = runTest {
        repo.create("book-1", NoteType.QUOTE, 1, "変数名は短すぎてはいけない", "")
        val hits = repo.search("変数名").first()

        assertEquals(1, hits.size)
        assertEquals("変数名", dao.lastLikeQuery)
        assertNull("日本語で FTS を使ってはいけない", dao.lastFtsQuery)
    }

    @Test
    fun `英数字のみの検索語は FTS を前方一致で使う`() = runTest {
        repo.create("book-1", NoteType.QUOTE, 1, "readable code matters", "")
        val hits = repo.search("readable").first()

        assertEquals(1, hits.size)
        assertEquals("readable*", dao.lastFtsQuery)
    }

    @Test
    fun `FTS の構文として解釈される文字は落とす`() = runTest {
        repo.create("book-1", NoteType.QUOTE, 1, "code", "")
        repo.search("code\" OR ").first()

        // 引用符などが残ると FTS のクエリが壊れるため除去されている
        assertEquals("codeOR*", dao.lastFtsQuery)
    }

    @Test
    fun `検索語の前後の空白は無視する`() = runTest {
        repo.create("book-1", NoteType.QUOTE, 1, "余白のある引用", "")
        assertEquals(1, repo.search("  余白  ").first().size)
    }

    @Test
    fun `削除済みのメモは検索に出ない`() = runTest {
        val id = repo.create("book-1", NoteType.QUOTE, 1, "消える引用", "")
        repo.delete(id)
        assertTrue(repo.search("消える").first().isEmpty())
    }

    @Test
    fun `引用と自分の言葉の両方を検索対象にする`() = runTest {
        repo.create("book-1", NoteType.QUOTE, 1, "引用側にある言葉", "")
        repo.create("book-1", NoteType.THOUGHT, 2, "", "感想側にある言葉")

        assertEquals(1, repo.search("引用側").first().size)
        assertEquals(1, repo.search("感想側").first().size)
        assertEquals(2, repo.search("ある言葉").first().size)
    }
}
