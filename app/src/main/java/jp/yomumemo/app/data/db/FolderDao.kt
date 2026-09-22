package jp.yomumemo.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import jp.yomumemo.app.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): FolderEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folders")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    /** 論理削除。books.folderId は外部キーの ON DELETE SET NULL では効かない(論理削除のため)ので、別途クリアする。 */
    @Query("UPDATE folders SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE books SET folderId = NULL, updatedAt = :now WHERE folderId = :folderId")
    suspend fun clearFolderFromBooks(folderId: String, now: Long)
}
