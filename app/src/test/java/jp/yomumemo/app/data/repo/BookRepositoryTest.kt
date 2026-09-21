package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.domain.BookMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRepositoryTest {

    private val dao = FakeBookDao()
    private var clock = 1_000L
    private var idCounter = 0

    private val repo = BookRepository(
        dao = dao,
        now = { clock },
        newId = { "id-${++idCounter}" },
    )

    private val sample = BookMetadata(
        isbn13 = "9784873115658",
        title = "リーダブルコード",
        authors = listOf("Dustin Boswell", "角征典"),
        publisher = "オーム社",
        pageCount = 260,
    )

    @Test
    fun `書誌から登録すると追加時刻と更新時刻が入る`() = runTest {
        val id = repo.addFromMetadata(sample)
        val book = checkNotNull(dao.findById(id))

        assertEquals("リーダブルコード", book.title)
        assertEquals(listOf("Dustin Boswell", "角征典"), book.authors)
        assertEquals(1_000L, book.addedAt)
        assertEquals(1_000L, book.updatedAt)
        assertEquals(ReadingStatus.WANT, book.status)
    }

    @Test
    fun `読書中として登録すると開始時刻が記録される`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.READING)
        assertEquals(1_000L, dao.findById(id)?.startedAt)
    }

    @Test
    fun `読みたいとして登録した時点では開始時刻は入らない`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.WANT)
        assertNull(dao.findById(id)?.startedAt)
    }

    @Test
    fun `読書中に変えたとき開始時刻が記録される`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.WANT)
        clock = 2_000L
        repo.updateStatus(id, ReadingStatus.READING)

        val book = checkNotNull(dao.findById(id))
        assertEquals(2_000L, book.startedAt)
        assertEquals(2_000L, book.updatedAt)
    }

    @Test
    fun `一度記録した開始時刻は読み直しても上書きされない`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.READING)
        clock = 2_000L
        repo.updateStatus(id, ReadingStatus.DONE)
        clock = 3_000L
        repo.updateStatus(id, ReadingStatus.READING)

        // 最初に読み始めた時刻が統計の基準になるので保持する
        assertEquals(1_000L, dao.findById(id)?.startedAt)
    }

    @Test
    fun `読了にすると読了時刻が入る`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.READING)
        clock = 5_000L
        repo.updateStatus(id, ReadingStatus.DONE)

        assertEquals(5_000L, dao.findById(id)?.finishedAt)
    }

    @Test
    fun `読了を取り消しても読了時刻は残す`() = runTest {
        val id = repo.addFromMetadata(sample, ReadingStatus.READING)
        clock = 5_000L
        repo.updateStatus(id, ReadingStatus.DONE)
        clock = 6_000L
        repo.updateStatus(id, ReadingStatus.READING)

        assertEquals(5_000L, dao.findById(id)?.finishedAt)
    }

    @Test
    fun `削除は論理削除で行い他端末へ伝播できるようにする`() = runTest {
        val id = repo.addFromMetadata(sample)
        clock = 9_000L
        repo.delete(id)

        // 一覧からは消える
        assertTrue(repo.observeAll().first().isEmpty())
        // 行自体は残り、削除時刻が入る
        val row = checkNotNull(dao.findById(id))
        assertEquals(9_000L, row.deletedAt)
    }

    @Test
    fun `同じ ISBN の本を検出して二重登録を防げる`() = runTest {
        repo.addFromMetadata(sample)
        assertNotNull(repo.findByIsbn("9784873115658"))
        assertNull(repo.findByIsbn("9784101010014"))
    }

    @Test
    fun `削除済みの本は ISBN 照合に引っかからない`() = runTest {
        val id = repo.addFromMetadata(sample)
        repo.delete(id)
        assertNull(repo.findByIsbn("9784873115658"))
    }

    @Test
    fun `削除した本を同じISBNで登録し直すと新規行を作らず復元する`() = runTest {
        // isbn13 には一意制約があるため、削除済みでも別行を新規INSERTすると
        // 一意制約違反でクラッシュする(実機で確認された不具合)。復元されるべき。
        val id = repo.addFromMetadata(sample)
        repo.delete(id)

        clock = 2_000L
        val secondId = repo.addFromMetadata(sample)

        assertEquals("削除前と同じ行が復元される(新しいIDが振られない)", id, secondId)
        val restored = checkNotNull(dao.findById(id))
        assertNull(restored.deletedAt)
        assertEquals(1, repo.countActive())
    }

    @Test
    fun `本棚にある本を同じISBNでもう一度登録しても行が重複しない`() = runTest {
        val id = repo.addFromMetadata(sample)
        val secondId = repo.addFromMetadata(sample.copy(title = "リーダブルコード(第2版)"))

        assertEquals(id, secondId)
        assertEquals(1, repo.countActive())
        assertEquals("リーダブルコード(第2版)", dao.findById(id)?.title)
    }

    @Test
    fun `手動登録でも同じISBNの削除済みの本があれば復元する`() = runTest {
        val id = repo.addManual(title = "手動登録した本", isbn13 = "9784101010014")
        repo.delete(id)

        val secondId = repo.addManual(title = "手動登録した本(再登録)", isbn13 = "9784101010014")

        assertEquals(id, secondId)
        assertEquals(1, repo.countActive())
    }

    @Test
    fun `手動登録では著者を分解して保存する`() = runTest {
        val id = repo.addManual(title = "  自費出版の本  ", authors = listOf("著者A", "著者B"))
        val book = checkNotNull(dao.findById(id))

        assertEquals("自費出版の本", book.title)
        assertEquals(listOf("著者A", "著者B"), book.authors)
        assertNull(book.isbn13)
    }

    @Test
    fun `進捗は負の値にならない`() = runTest {
        val id = repo.addFromMetadata(sample)
        repo.updateProgress(id, -50)
        assertEquals(0, dao.findById(id)?.currentPage)
    }

    @Test
    fun `評価は1から5に収める`() = runTest {
        val id = repo.addFromMetadata(sample)
        repo.updateRating(id, 9)
        assertEquals(5, dao.findById(id)?.rating)

        repo.updateRating(id, 0)
        assertEquals(1, dao.findById(id)?.rating)

        repo.updateRating(id, null)
        assertNull(dao.findById(id)?.rating)
    }

    @Test
    fun `登録数は論理削除を除いて数える`() = runTest {
        val a = repo.addFromMetadata(sample)
        repo.addFromMetadata(sample.copy(isbn13 = "9784101010014", title = "吾輩は猫である"))
        assertEquals(2, repo.countActive())

        repo.delete(a)
        assertEquals(1, repo.countActive())
    }
}
