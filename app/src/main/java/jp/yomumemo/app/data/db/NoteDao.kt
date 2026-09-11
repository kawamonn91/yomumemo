package jp.yomumemo.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import jp.yomumemo.app.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        "SELECT * FROM notes WHERE bookId = :bookId AND deletedAt IS NULL " +
            "ORDER BY COALESCE(page, 2147483647) ASC, createdAt ASC",
    )
    fun observeForBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    /**
     * メモ全文検索。FTS4 の MATCH を使う。
     * 日本語は単語境界が無く FTS の既定トークナイザでは分割されないため、
     * 呼び出し側で前方一致用に "語*" の形へ整形して渡す。
     */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid " +
            "WHERE notes_fts MATCH :query AND notes.deletedAt IS NULL " +
            "ORDER BY notes.createdAt DESC",
    )
    fun search(query: String): Flow<List<NoteEntity>>

    /** FTS が苦手な日本語向けのフォールバック (部分一致)。 */
    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL " +
            "AND (quote LIKE '%' || :q || '%' OR comment LIKE '%' || :q || '%') " +
            "ORDER BY createdAt DESC",
    )
    fun searchLike(q: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM notes")
    suspend fun allIncludingDeleted(): List<NoteEntity>
}
