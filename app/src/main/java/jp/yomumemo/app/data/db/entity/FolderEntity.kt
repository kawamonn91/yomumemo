package jp.yomumemo.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが自分で作るフォルダ(ジャンル分けなど自由な区分け)。
 * 1冊は1つのフォルダにしか入らない(タグではなく、本棚の区画のイメージ)。
 *
 * id は UUID 文字列。BookEntity と同じ理由(複数端末での採番衝突を避ける)。
 */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["sortOrder"])],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 表示順。小さいほど先に出す。作成時に末尾へ追加する形で採番する。 */
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
