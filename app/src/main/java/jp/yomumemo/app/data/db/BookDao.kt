package jp.yomumemo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** 論理削除されていない本を新着順に返す。 */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND status = :status ORDER BY addedAt DESC")
    fun observeByStatus(status: ReadingStatus): Flow<List<BookEntity>>

    /** [folderId] が null なら「フォルダ未設定」の本を返す。 */
    @Query("SELECT * FROM books WHERE deletedAt IS NULL AND folderId IS :folderId ORDER BY addedAt DESC")
    fun observeByFolder(folderId: String?): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    /** 同じ本を二重登録しないための ISBN 照合(本棚に表示されている本だけが対象)。 */
    @Query("SELECT * FROM books WHERE isbn13 = :isbn13 AND deletedAt IS NULL LIMIT 1")
    suspend fun findByIsbn(isbn13: String): BookEntity?

    /**
     * 論理削除済みの行も含めて ISBN で探す。
     *
     * isbn13 には一意インデックスが張ってあるため、一度削除した本を後で
     * (誤って、または削除を取り消すつもりで)もう一度スキャンすると、
     * findByIsbn では見つからない(deletedAt IS NULL の条件で除外される)のに
     * INSERT は一意制約違反で失敗する、という不整合が起きる。
     * 登録処理側でこの行を見つけて上書き(復元)することでその不整合を無くす。
     */
    @Query("SELECT * FROM books WHERE isbn13 = :isbn13 LIMIT 1")
    suspend fun findByIsbnIncludingDeleted(isbn13: String): BookEntity?

    /** 無料版の登録上限判定に使う。 */
    @Query("SELECT COUNT(*) FROM books WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM books WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :q || '%' AND deletedAt IS NULL")
    fun searchByTitle(q: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    /** 物理削除ではなく論理削除。同期先の他端末へ削除を伝播させるため。 */
    @Query("UPDATE books SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** 同期用: 全件(論理削除済みを含む)。 */
    @Query("SELECT * FROM books")
    suspend fun allIncludingDeleted(): List<BookEntity>
}
