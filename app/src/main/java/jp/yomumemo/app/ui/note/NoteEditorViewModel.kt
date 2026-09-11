package jp.yomumemo.app.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.ocr.QuoteTextCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val type: NoteType = NoteType.THOUGHT,
    val page: String = "",
    val quote: String = "",
    val comment: String = "",
    val isExisting: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaved: Boolean = false,
    val isPremium: Boolean = false,
) {
    /** 引用も感想も空なら保存させない。 */
    val canSave: Boolean get() = quote.isNotBlank() || comment.isNotBlank()
}

class NoteEditorViewModel(
    private val bookId: String,
    private val noteId: String?,
    private val notes: NoteRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorUiState())
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.isPremium.collect { premium ->
                _state.update { it.copy(isPremium = premium) }
            }
        }

        if (noteId == null) {
            _state.update { it.copy(isLoaded = true) }
        } else {
            viewModelScope.launch {
                val note = notes.findById(noteId)
                _state.value = if (note == null) {
                    NoteEditorUiState(isLoaded = true)
                } else {
                    NoteEditorUiState(
                        type = note.type,
                        page = note.page?.toString().orEmpty(),
                        quote = note.quote,
                        comment = note.comment,
                        isExisting = true,
                        isLoaded = true,
                    )
                }
            }
        }
    }

    fun setType(type: NoteType) = _state.update { it.copy(type = type) }

    fun setPage(page: String) = _state.update {
        it.copy(page = page.filter(Char::isDigit).take(5))
    }

    fun setQuote(quote: String) = _state.update { it.copy(quote = quote) }

    fun setComment(comment: String) = _state.update { it.copy(comment = comment) }

    /**
     * OCR で読み取った文を引用欄に足す。
     * 既に書いてある内容は消さずに追記する。撮り直しや複数ページの取り込みで
     * 前の結果が消えると、書き写した手間が無駄になるため。
     */
    fun appendRecognizedQuote(text: String) {
        _state.update { it.copy(quote = QuoteTextCleaner.append(it.quote, text)) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val page = current.page.toIntOrNull()
            if (noteId == null) {
                notes.create(bookId, current.type, page, current.quote, current.comment)
            } else {
                notes.update(noteId, current.type, page, current.quote, current.comment)
            }
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        val id = noteId ?: return
        viewModelScope.launch {
            notes.delete(id)
            _state.update { it.copy(isSaved = true) }
        }
    }
}
