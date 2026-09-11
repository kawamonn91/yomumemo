package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.BookDao
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.domain.BookMetadata
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 本の登録・更新。ID生成と時刻はテストのため差し替えられるようにしてある。 */
class BookRepository(
    private val dao: BookDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<BookEntity>> = dao.observeAll()

    fun observeByStatus(status: ReadingStatus): Flow<List<BookEntity>> = dao.observeByStatus(status)

    fun observeById(id: String): Flow<BookEntity?> = dao.observeById(id)

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun countActive(): Int = dao.countActive()

    /** 同じ ISBN の本が既に本棚にあれば返す。二重登録の確認に使う。 */
    suspend fun findByIsbn(isbn13: String): BookEntity? = dao.findByIsbn(isbn13)

    suspend fun findById(id: String): BookEntity? = dao.findById(id)

    /** 書誌検索の結果から登録する。 */
    suspend fun addFromMetadata(
        metadata: BookMetadata,
        status: ReadingStatus = ReadingStatus.WANT,
    ): String {
        val timestamp = now()
        val book = BookEntity(
            id = newId(),
            isbn13 = metadata.isbn13,
            title = metadata.title,
            subtitle = metadata.subtitle,
            authors = metadata.authors,
            publisher = metadata.publisher,
            publishedDate = metadata.publishedDate,
            coverUrl = metadata.coverUrl,
            pageCount = metadata.pageCount,
            description = metadata.description,
            status = status,
            addedAt = timestamp,
            startedAt = if (status == ReadingStatus.READING) timestamp else null,
            updatedAt = timestamp,
        )
        dao.insert(book)
        return book.id
    }

    /**
     * 手動で登録する。
     * openBD にも補完元にも無い本は実在するため、この経路が必ず必要になる。
     */
    suspend fun addManual(
        title: String,
        authors: List<String> = emptyList(),
        publisher: String? = null,
        isbn13: String? = null,
        pageCount: Int? = null,
        status: ReadingStatus = ReadingStatus.WANT,
    ): String {
        val timestamp = now()
        val book = BookEntity(
            id = newId(),
            isbn13 = isbn13,
            title = title.trim(),
            authors = authors,
            publisher = publisher?.trim()?.takeIf { it.isNotEmpty() },
            pageCount = pageCount,
            status = status,
            addedAt = timestamp,
            startedAt = if (status == ReadingStatus.READING) timestamp else null,
            updatedAt = timestamp,
        )
        dao.insert(book)
        return book.id
    }

    /**
     * 読書状態を変える。
     * 「読書中」に初めて入った時刻と「読了」した時刻は、統計のために記録しておく。
     */
    suspend fun updateStatus(id: String, status: ReadingStatus) {
        val book = dao.findById(id) ?: return
        val timestamp = now()
        dao.upsert(
            book.copy(
                status = status,
                startedAt = book.startedAt ?: timestamp.takeIf { status == ReadingStatus.READING },
                finishedAt = when (status) {
                    ReadingStatus.DONE -> book.finishedAt ?: timestamp
                    else -> book.finishedAt
                },
                updatedAt = timestamp,
            ),
        )
    }

    suspend fun updateProgress(id: String, currentPage: Int) {
        val book = dao.findById(id) ?: return
        dao.upsert(book.copy(currentPage = currentPage.coerceAtLeast(0), updatedAt = now()))
    }

    suspend fun updateRating(id: String, rating: Int?) {
        val book = dao.findById(id) ?: return
        dao.upsert(book.copy(rating = rating?.coerceIn(1, 5), updatedAt = now()))
    }

    /** 論理削除。他端末へ削除を伝播させるため物理削除はしない。 */
    suspend fun delete(id: String) = dao.softDelete(id, now())
}
