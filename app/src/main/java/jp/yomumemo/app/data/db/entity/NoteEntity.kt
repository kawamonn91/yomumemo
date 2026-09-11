package jp.yomumemo.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** メモの種別。 */
enum class NoteType { QUOTE, THOUGHT, QUESTION, SUMMARY }

/**
 * 本に紐づくメモ。気になった点・面白いと思った点をその都度書き留める。
 * quote(引用) と comment(自分の言葉) を分けて保持し、引用元ページも残す。
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: NoteType = NoteType.THOUGHT,
    val page: Int? = null,
    val quote: String = "",
    val comment: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** メモ全文検索用の FTS テーブル (notes の内容をミラーする)。 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val quote: String,
    val comment: String,
)
