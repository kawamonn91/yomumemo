package jp.yomumemo.app.data.repo

import jp.yomumemo.app.data.db.NoteDao
import jp.yomumemo.app.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** メモリ上で動く [NoteDao]。 */
class FakeNoteDao : NoteDao {

    private val rows = MutableStateFlow<Map<String, NoteEntity>>(emptyMap())

    /** 直近に呼ばれた検索。FTS と LIKE のどちらを使ったか検証するために記録する。 */
    var lastFtsQuery: String? = null
        private set
    var lastLikeQuery: String? = null
        private set

    private fun active() = rows.value.values.filter { it.deletedAt == null }

    override fun observeForBook(bookId: String): Flow<List<NoteEntity>> =
        rows.map { m ->
            m.values
                .filter { it.deletedAt == null && it.bookId == bookId }
                .sortedWith(compareBy({ it.page ?: Int.MAX_VALUE }, { it.createdAt }))
        }

    override fun observeRecent(limit: Int): Flow<List<NoteEntity>> =
        rows.map { m ->
            m.values.filter { it.deletedAt == null }.sortedByDescending { it.createdAt }.take(limit)
        }

    override suspend fun findById(id: String): NoteEntity? = rows.value[id]

    override fun observeActiveCount(): Flow<Int> =
        rows.map { m -> m.values.count { it.deletedAt == null } }

    override fun search(query: String): Flow<List<NoteEntity>> {
        lastFtsQuery = query
        val term = query.removeSuffix("*")
        return rows.map { m ->
            m.values.filter {
                it.deletedAt == null && (it.quote.contains(term, true) || it.comment.contains(term, true))
            }
        }
    }

    override fun searchLike(q: String): Flow<List<NoteEntity>> {
        lastLikeQuery = q
        return rows.map { m ->
            m.values.filter {
                it.deletedAt == null && (it.quote.contains(q) || it.comment.contains(q))
            }
        }
    }

    override suspend fun upsert(note: NoteEntity) {
        rows.value = rows.value + (note.id to note)
    }

    override suspend fun upsertAll(notes: List<NoteEntity>) {
        rows.value = rows.value + notes.associateBy { it.id }
    }

    override suspend fun softDelete(id: String, now: Long) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(deletedAt = now, updatedAt = now)) }
    }

    override suspend fun allIncludingDeleted(): List<NoteEntity> = rows.value.values.toList()
}
