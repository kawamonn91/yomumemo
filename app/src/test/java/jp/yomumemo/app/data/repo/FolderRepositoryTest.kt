package jp.yomumemo.app.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRepositoryTest {

    private val dao = FakeFolderDao()
    private var clock = 1_000L
    private var idCounter = 0

    private val repo = FolderRepository(
        dao = dao,
        now = { clock },
        newId = { "folder-${++idCounter}" },
    )

    @Test
    fun `作成したフォルダは名前の前後の空白を取り除いて保存する`() = runTest {
        val id = repo.create("  SF  ")
        val folder = checkNotNull(dao.findById(id))

        assertEquals("SF", folder.name)
        assertEquals(1_000L, folder.createdAt)
        assertEquals(1_000L, folder.updatedAt)
    }

    @Test
    fun `作成順に並び順が振られる`() = runTest {
        val first = repo.create("小説")
        val second = repo.create("技術書")

        val folders = repo.observeAll().first()
        assertEquals(listOf(first, second), folders.map { it.id })
    }

    @Test
    fun `改名すると更新時刻が入る`() = runTest {
        val id = repo.create("小説")
        clock = 2_000L
        repo.rename(id, "  ミステリー  ")

        val folder = checkNotNull(dao.findById(id))
        assertEquals("ミステリー", folder.name)
        assertEquals(2_000L, folder.updatedAt)
    }

    @Test
    fun `存在しないフォルダの改名は何もしない`() = runTest {
        repo.rename("no-such-id", "何か")
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `削除すると論理削除され一覧から消える`() = runTest {
        val id = repo.create("小説")
        repo.delete(id)

        assertTrue(repo.observeAll().first().isEmpty())
        assertNull(dao.findById(id))
    }

    @Test
    fun `削除するとフォルダ内の本の割り当てを解除してから論理削除する`() = runTest {
        val id = repo.create("小説")
        repo.delete(id)

        // 本が削除ではなく未分類に戻ることを、フォルダのクリア処理が
        // 論理削除より先に呼ばれていることで保証する。
        assertEquals(listOf(id), dao.clearedBookFolders)
    }
}
