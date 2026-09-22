package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.BookDao
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** メモリ上で動く [BookDao]。リポジトリの判断ロジックだけを検証するために使う。 */
class FakeBookDao : BookDao {

    private val rows = MutableStateFlow<Map<String, BookEntity>>(emptyMap())

    private fun active() = rows.value.values.filter { it.deletedAt == null }

    override fun observeAll(): Flow<List<BookEntity>> =
        rows.map { m -> m.values.filter { it.deletedAt == null }.sortedByDescending { it.addedAt } }

    override fun observeByStatus(status: ReadingStatus): Flow<List<BookEntity>> =
        rows.map { m -> m.values.filter { it.deletedAt == null && it.status == status } }

    override fun observeByFolder(folderId: String?): Flow<List<BookEntity>> =
        rows.map { m -> m.values.filter { it.deletedAt == null && it.folderId == folderId } }

    override fun observeById(id: String): Flow<BookEntity?> =
        rows.map { m -> m[id]?.takeIf { it.deletedAt == null } }

    override suspend fun findById(id: String): BookEntity? = rows.value[id]

    override suspend fun findByIsbn(isbn13: String): BookEntity? =
        active().firstOrNull { it.isbn13 == isbn13 }

    override suspend fun findByIsbnIncludingDeleted(isbn13: String): BookEntity? =
        rows.value.values.firstOrNull { it.isbn13 == isbn13 }

    override suspend fun countActive(): Int = active().size

    override fun observeActiveCount(): Flow<Int> =
        rows.map { m -> m.values.count { it.deletedAt == null } }

    override fun searchByTitle(q: String): Flow<List<BookEntity>> =
        rows.map { m -> m.values.filter { it.deletedAt == null && it.title.contains(q) } }

    override suspend fun insert(book: BookEntity) {
        check(!rows.value.containsKey(book.id)) { "ID が重複しています: ${book.id}" }
        rows.value = rows.value + (book.id to book)
    }

    override suspend fun upsert(book: BookEntity) {
        rows.value = rows.value + (book.id to book)
    }

    override suspend fun upsertAll(books: List<BookEntity>) {
        rows.value = rows.value + books.associateBy { it.id }
    }

    override suspend fun softDelete(id: String, now: Long) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(deletedAt = now, updatedAt = now)) }
    }

    override suspend fun allIncludingDeleted(): List<BookEntity> = rows.value.values.toList()
}
