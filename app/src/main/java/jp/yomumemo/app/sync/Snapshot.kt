package jp.yomumemo.app.sync

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import kotlinx.serialization.Serializable

/**
 * 同期・バックアップで受け渡す全データ。
 *
 * Room のエンティティをそのまま直列化していないのは、DBのスキーマを変えたときに
 * 過去のバックアップが読めなくなるのを防ぐため。ここは別の層として固定し、
 * 読み込み時に現在のエンティティへ写す。
 */
@Serializable
data class Snapshot(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val books: List<BookSnapshot> = emptyList(),
    val notes: List<NoteSnapshot> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * [BookEntity.localCoverPath] はここに含めない。端末内の自分専用領域を指すパスで、
 * 他端末に持って行っても存在しないファイルを指すだけになるため。
 */
@Serializable
data class BookSnapshot(
    val id: String,
    val isbn13: String? = null,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val coverUrl: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    val status: String = ReadingStatus.WANT.name,
    val rating: Int? = null,
    val currentPage: Int = 0,
    val addedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class NoteSnapshot(
    val id: String,
    val bookId: String,
    val type: String = NoteType.THOUGHT.name,
    val page: Int? = null,
    val quote: String = "",
    val comment: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

fun BookEntity.toSnapshot() = BookSnapshot(
    id = id,
    isbn13 = isbn13,
    title = title,
    subtitle = subtitle,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    coverUrl = coverUrl,
    pageCount = pageCount,
    description = description,
    status = status.name,
    rating = rating,
    currentPage = currentPage,
    addedAt = addedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun BookSnapshot.toEntity() = BookEntity(
    id = id,
    isbn13 = isbn13,
    title = title,
    subtitle = subtitle,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    coverUrl = coverUrl,
    pageCount = pageCount,
    description = description,
    // 未知の値が来ても落とさない。将来の版で状態が増える可能性があるため。
    status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.WANT),
    rating = rating,
    currentPage = currentPage,
    addedAt = addedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun NoteEntity.toSnapshot() = NoteSnapshot(
    id = id,
    bookId = bookId,
    type = type.name,
    page = page,
    quote = quote,
    comment = comment,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun NoteSnapshot.toEntity() = NoteEntity(
    id = id,
    bookId = bookId,
    type = runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.THOUGHT),
    page = page,
    quote = quote,
    comment = comment,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
