package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.FolderDao
import jp.yomumemo.app.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 自分で作るフォルダ(ジャンル分けなど)の管理。 */
class FolderRepository(
    private val dao: FolderDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeAll(): Flow<List<FolderEntity>> = dao.observeAll()

    suspend fun create(name: String): String {
        val timestamp = now()
        val folder = FolderEntity(
            id = newId(),
            name = name.trim(),
            sortOrder = dao.maxSortOrder() + 1,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(folder)
        return folder.id
    }

    suspend fun rename(id: String, name: String) {
        val folder = dao.findById(id) ?: return
        dao.upsert(folder.copy(name = name.trim(), updatedAt = now()))
    }

    /** フォルダを削除する。中の本は削除されず、未分類に戻る。 */
    suspend fun delete(id: String) {
        val timestamp = now()
        dao.clearFolderFromBooks(id, timestamp)
        dao.softDelete(id, timestamp)
    }
}
