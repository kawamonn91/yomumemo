package jp.yomumemo.app.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 手動撮影した表紙画像をアプリ専用領域に保存する。
 *
 * ML Kit Document Scanner が返す画像URIはスキャナー側の一時領域を指しており、
 * いつまで有効か保証されないため、本の登録が確定した時点で自前の永続領域へ
 * コピーしておく。
 */
class CoverImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** [sourceUri] の中身を [bookId] 用のファイルへコピーし、保存先の絶対パスを返す。失敗したら null。 */
    fun save(bookId: String, sourceUri: Uri): String? = runCatching {
        val file = File(dir, "$bookId.jpg")
        val input = context.contentResolver.openInputStream(sourceUri) ?: return null
        input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
        file.absolutePath
    }.getOrNull()

    /** 撮り直し・削除で不要になった古いファイルを消す。無くても問題ない。 */
    fun delete(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }
}
