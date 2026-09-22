package jp.yomumemo.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 読書状態。 */
enum class ReadingStatus { WANT, READING, PAUSED, DONE }

/**
 * 登録された本。
 *
 * id は UUID 文字列。複数端末が自動採番すると Drive 同期時に ID が衝突するため、
 * 端末側で一意に生成できる UUID を採用している。
 * updatedAt / deletedAt は同期のマージ判定(last-write-wins・論理削除)に使う。
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["isbn13"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    /** 手動登録した本は ISBN を持たないことがあるため null 許容。 */
    val isbn13: String? = null,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val coverUrl: String? = null,
    /**
     * 自分で撮影した表紙画像の端末内絶対パス。書誌データに表紙が無かった/合わなかった
     * ときのために撮り直せるようにしたもので、あれば [coverUrl] より優先して表示する。
     * 端末のアプリ専用領域を指すため他端末には同期しない([sync.Snapshot] には含めない)。
     */
    val localCoverPath: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    val status: ReadingStatus = ReadingStatus.WANT,
    val rating: Int? = null,
    val currentPage: Int = 0,
    val addedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    /** 表示すべき表紙。自分で撮影した分があればそちらを優先する。 */
    val displayCoverUrl: String?
        get() = localCoverPath?.let { "file://$it" } ?: coverUrl
}
