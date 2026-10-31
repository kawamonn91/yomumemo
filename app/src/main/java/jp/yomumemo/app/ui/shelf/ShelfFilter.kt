package jp.yomumemo.app.ui.shelf

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus

/**
 * 本棚の表示条件（読書状態・フォルダ）に応じて書籍一覧を絞り込む。
 */
fun filterBooks(
    books: List<BookEntity>,
    status: ReadingStatus?,
    folder: FolderFilter,
): List<BookEntity> =
    books
        .filter { status == null || it.status == status }
        .filter { book ->
            when (folder) {
                FolderFilter.All -> true
                FolderFilter.Unassigned -> book.folderId == null
                is FolderFilter.Specific -> book.folderId == folder.folderId
            }
        }
