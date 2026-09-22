package jp.yomumemo.app.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.FolderEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** フォルダの絞り込み。null は「すべて」、[FolderFilter.UNASSIGNED] は「未分類」。 */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unassigned : FolderFilter
    data class Specific(val folderId: String) : FolderFilter
}

/** 本棚の絞り込み。statusFilter は null は「すべて」。 */
data class ShelfUiState(
    val books: List<BookEntity> = emptyList(),
    val statusFilter: ReadingStatus? = null,
    val folderFilter: FolderFilter = FolderFilter.All,
    val folders: List<FolderEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

class ShelfViewModel(
    private val books: BookRepository,
    private val folders: FolderRepository,
) : ViewModel() {

    private val statusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val folderFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)

    // 状態・フォルダの両方で絞り込む必要があるため、全件を取得してから
    // メモリ上でAND条件をかける(個人の蔵書規模ならこれで十分)。
    private val filteredBooks = combine(books.observeAll(), statusFilter, folderFilter) { all, status, folder ->
        all
            .filter { status == null || it.status == status }
            .filter { book ->
                when (folder) {
                    FolderFilter.All -> true
                    FolderFilter.Unassigned -> book.folderId == null
                    is FolderFilter.Specific -> book.folderId == folder.folderId
                }
            }
    }

    val uiState: StateFlow<ShelfUiState> =
        combine(filteredBooks, statusFilter, folderFilter, folders.observeAll(), books.observeActiveCount()) {
                list, status, folder, folderList, total ->
            ShelfUiState(
                books = list,
                statusFilter = status,
                folderFilter = folder,
                folders = folderList,
                totalCount = total,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShelfUiState(),
        )

    val filterState: StateFlow<ReadingStatus?> = statusFilter.asStateFlow()

    fun setFilter(status: ReadingStatus?) {
        statusFilter.value = status
    }

    fun setFolderFilter(filter: FolderFilter) {
        folderFilter.value = filter
    }

    fun delete(id: String) {
        viewModelScope.launch { books.delete(id) }
    }
}
