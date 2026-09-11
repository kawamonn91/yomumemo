package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.NoteDao
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 読書メモの作成・更新・検索。 */
class NoteRepository(
    private val dao: NoteDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeForBook(bookId: String): Flow<List<NoteEntity>> = dao.observeForBook(bookId)

    fun observeRecent(limit: Int = 50): Flow<List<NoteEntity>> = dao.observeRecent(limit)

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun findById(id: String): NoteEntity? = dao.findById(id)

    suspend fun create(
        bookId: String,
        type: NoteType,
        page: Int?,
        quote: String,
        comment: String,
    ): String {
        val timestamp = now()
        val note = NoteEntity(
            id = newId(),
            bookId = bookId,
            type = type,
            page = page,
            quote = quote.trim(),
            comment = comment.trim(),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(note)
        return note.id
    }

    suspend fun update(
        id: String,
        type: NoteType,
        page: Int?,
        quote: String,
        comment: String,
    ) {
        val existing = dao.findById(id) ?: return
        dao.upsert(
            existing.copy(
                type = type,
                page = page,
                quote = quote.trim(),
                comment = comment.trim(),
                updatedAt = now(),
            ),
        )
    }

    suspend fun delete(id: String) = dao.softDelete(id, now())

    /**
     * メモを検索する。
     *
     * FTS4 の既定トークナイザは空白で区切るため、単語境界の無い日本語はほぼ分割されず
     * MATCH が期待通りに効かない。そのため日本語を含む語は LIKE の部分一致で引く。
     * 英数字のみの語は FTS の方が速く、前方一致も効くのでそちらを使う。
     */
    fun search(query: String): Flow<List<NoteEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isAscii()) {
            dao.search(trimmed.escapeForFts() + "*")
        } else {
            dao.searchLike(trimmed)
        }
    }

    private fun String.isAscii(): Boolean = isNotEmpty() && all { it.code < 128 }

    /** FTS のクエリ構文として解釈されると困る文字を落とす。 */
    private fun String.escapeForFts(): String =
        filter { it.isLetterOrDigit() || it == '_' }
}
