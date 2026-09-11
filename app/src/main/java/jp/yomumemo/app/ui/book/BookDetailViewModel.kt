package jp.yomumemo.app.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val book: BookEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = true,
)

class BookDetailViewModel(
    private val bookId: String,
    private val books: BookRepository,
    private val notes: NoteRepository,
) : ViewModel() {

    val uiState: StateFlow<BookDetailUiState> =
        combine(books.observeById(bookId), notes.observeForBook(bookId)) { book, noteList ->
            BookDetailUiState(book = book, notes = noteList, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookDetailUiState(),
        )

    fun updateStatus(status: ReadingStatus) {
        viewModelScope.launch { books.updateStatus(bookId, status) }
    }

    fun updateProgress(page: Int) {
        viewModelScope.launch { books.updateProgress(bookId, page) }
    }

    fun updateRating(rating: Int?) {
        viewModelScope.launch { books.updateRating(bookId, rating) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { notes.delete(noteId) }
    }

    fun deleteBook(onDeleted: () -> Unit) {
        viewModelScope.launch {
            books.delete(bookId)
            onDeleted()
        }
    }
}
