package jp.yomumemo.app.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.repo.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 本棚の絞り込み。null は「すべて」。 */
data class ShelfUiState(
    val books: List<BookEntity> = emptyList(),
    val filter: ReadingStatus? = null,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

class ShelfViewModel(
    private val books: BookRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<ReadingStatus?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredBooks = filter.flatMapLatest { status ->
        if (status == null) books.observeAll() else books.observeByStatus(status)
    }

    val uiState: StateFlow<ShelfUiState> =
        combine(filteredBooks, filter, books.observeActiveCount()) { list, status, total ->
            ShelfUiState(books = list, filter = status, totalCount = total, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShelfUiState(),
        )

    val filterState: StateFlow<ReadingStatus?> = filter.asStateFlow()

    fun setFilter(status: ReadingStatus?) {
        filter.value = status
    }

    fun delete(id: String) {
        viewModelScope.launch { books.delete(id) }
    }
}
