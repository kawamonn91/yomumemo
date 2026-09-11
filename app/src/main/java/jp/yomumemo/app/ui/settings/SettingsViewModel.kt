package jp.yomumemo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.export.BookWithNotes
import jp.yomumemo.app.export.ExportFormat
import jp.yomumemo.app.export.NoteExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isPremium: Boolean = false,
    val bookCount: Int = 0,
    val noteCount: Int = 0,
    val message: String? = null,
    val isExporting: Boolean = false,
)

class SettingsViewModel(
    private val books: BookRepository,
    private val notes: NoteRepository,
    private val entitlements: EntitlementRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val exporting = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            entitlements.isPremium,
            books.observeActiveCount(),
            notes.observeActiveCount(),
            message,
            exporting,
        ) { premium, bookCount, noteCount, msg, isExporting ->
            SettingsUiState(
                isPremium = premium,
                bookCount = bookCount,
                noteCount = noteCount,
                message = msg,
                isExporting = isExporting,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun suggestedFileName(format: ExportFormat): String = NoteExporter.fileName(format)

    /**
     * 書き出す中身を組み立てる。
     * 実際の書き込み先はユーザーが選ぶので、ここでは文字列を返すだけにしている。
     */
    fun buildExport(format: ExportFormat, onReady: (String) -> Unit) {
        viewModelScope.launch {
            exporting.value = true
            try {
                val allBooks = books.observeAll().first()
                val content = NoteExporter.export(
                    books = allBooks.map { book ->
                        BookWithNotes(book, notes.observeForBook(book.id).first())
                    },
                    format = format,
                )
                onReady(content)
            } finally {
                exporting.value = false
            }
        }
    }

    fun onExportFinished(success: Boolean, format: ExportFormat) {
        message.value = if (success) {
            format.label + " で書き出しました。"
        } else {
            "書き出しに失敗しました。保存先を確認してください。"
        }
    }

    fun onExportCancelled() {
        message.value = null
    }

    fun clearMessage() {
        message.value = null
    }
}
