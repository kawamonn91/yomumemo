package jp.yomumemo.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.remote.BookLookupRepository
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.domain.LookupResult
import jp.yomumemo.app.util.Isbn
import jp.yomumemo.app.util.ScanOutcome
import jp.yomumemo.app.util.interpretScannedCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    /** スキャナ起動待ち。 */
    data object Idle : ScanUiState

    data class LookingUp(val isbn13: String) : ScanUiState

    /** 書誌が取れた。登録するか確認する。 */
    data class Confirm(
        val metadata: BookMetadata,
        /** 既に本棚にある場合はその本。二重登録を防ぐ。 */
        val existing: BookEntity? = null,
    ) : ScanUiState

    /**
     * 日本の書籍バーコードの下段(価格コード)を読んだ。
     * 誤操作として最も多いので、専用の案内を出す。
     */
    data object PriceCodeScanned : ScanUiState

    /** 書籍のバーコードではなかった。 */
    data object NotABook : ScanUiState

    /** どの提供元にも無かった。手動入力へ誘導する。 */
    data class NotFound(val isbn13: String) : ScanUiState

    data class Failed(val message: String) : ScanUiState

    data class Saved(val bookId: String) : ScanUiState
}

class ScanViewModel(
    private val lookup: BookLookupRepository,
    private val books: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** スキャナが返した生の文字列を処理する。 */
    fun onScanned(rawValue: String) {
        when (val outcome = interpretScannedCode(rawValue)) {
            is ScanOutcome.Isbn -> lookUp(outcome.isbn13)
            ScanOutcome.JapanesePriceCode -> _state.value = ScanUiState.PriceCodeScanned
            ScanOutcome.NotABook -> _state.value = ScanUiState.NotABook
        }
    }

    fun onScanCancelled() {
        _state.value = ScanUiState.Idle
    }

    fun onScanFailed(message: String?) {
        _state.value = ScanUiState.Failed(message ?: "バーコードを読み取れませんでした。")
    }

    /** ISBN を手で入力した場合。 */
    fun lookUpTyped(rawIsbn: String) {
        val isbn13 = Isbn.toIsbn13(rawIsbn)
        if (isbn13 == null) {
            _state.value = ScanUiState.Failed("ISBN として正しくありません。10桁または13桁で入力してください。")
            return
        }
        lookUp(isbn13)
    }

    private fun lookUp(isbn13: String) {
        _state.value = ScanUiState.LookingUp(isbn13)
        viewModelScope.launch {
            val existing = books.findByIsbn(isbn13)
            when (val result = lookup.lookup(isbn13)) {
                is LookupResult.Found ->
                    _state.value = ScanUiState.Confirm(result.metadata, existing)

                LookupResult.NotFound ->
                    _state.value = if (existing != null) {
                        // 書誌は引けなかったが本棚にはある
                        ScanUiState.Confirm(existing.toMetadata(), existing)
                    } else {
                        ScanUiState.NotFound(isbn13)
                    }

                is LookupResult.Failed ->
                    _state.value = ScanUiState.Failed(
                        "書誌情報を取得できませんでした。通信状況を確認してください。",
                    )
            }
        }
    }

    fun save(metadata: BookMetadata, status: ReadingStatus) {
        viewModelScope.launch {
            val id = books.addFromMetadata(metadata, status)
            _state.value = ScanUiState.Saved(id)
        }
    }

    fun saveManual(
        title: String,
        authors: String,
        publisher: String,
        isbn13: String?,
        status: ReadingStatus,
    ) {
        if (title.isBlank()) {
            _state.value = ScanUiState.Failed("タイトルを入力してください。")
            return
        }
        viewModelScope.launch {
            val id = books.addManual(
                title = title,
                authors = authors.split("、", ",").map(String::trim).filter(String::isNotEmpty),
                publisher = publisher,
                isbn13 = isbn13,
                status = status,
            )
            _state.value = ScanUiState.Saved(id)
        }
    }

    fun reset() {
        _state.value = ScanUiState.Idle
    }
}

private fun BookEntity.toMetadata() = BookMetadata(
    isbn13 = isbn13.orEmpty(),
    title = title,
    subtitle = subtitle,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    coverUrl = coverUrl,
    pageCount = pageCount,
    description = description,
)
