package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.FolderDao
import jp.yomumemo.app.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** メモリ上で動く [FolderDao]。リポジトリの判断ロジックだけを検証するために使う。 */
class FakeFolderDao : FolderDao {

    private val rows = MutableStateFlow<Map<String, FolderEntity>>(emptyMap())

    /** [FakeBookDao] を経由せず、フォルダ削除時の一括更新を検証するための素朴な記録。 */
    val clearedBookFolders = mutableListOf<String>()

    override fun observeAll(): Flow<List<FolderEntity>> =
        rows.map { m -> m.values.filter { it.deletedAt == null }.sortedBy { it.sortOrder } }

    override suspend fun findById(id: String): FolderEntity? =
        rows.value[id]?.takeIf { it.deletedAt == null }

    override suspend fun maxSortOrder(): Int =
        rows.value.values.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun upsert(folder: FolderEntity) {
        rows.value = rows.value + (folder.id to folder)
    }

    override suspend fun softDelete(id: String, now: Long) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(deletedAt = now, updatedAt = now)) }
    }

    override suspend fun clearFolderFromBooks(folderId: String, now: Long) {
        clearedBookFolders += folderId
    }
}
