package jp.yomumemo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val notes: List<NoteEntity> = emptyList(),
    /** メモが属する本を引くための対応表。 */
    val booksById: Map<String, BookEntity> = emptyMap(),
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val notes: NoteRepository,
    books: BookRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val results = _query
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else notes.search(q)
        }

    val uiState: StateFlow<SearchUiState> =
        combine(_query, results, books.observeAll()) { q, noteList, bookList ->
            SearchUiState(
                query = q,
                notes = noteList,
                booksById = bookList.associateBy { it.id },
                hasSearched = q.isNotBlank(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    fun setQuery(value: String) {
        _query.value = value
    }
}
