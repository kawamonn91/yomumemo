package jp.yomumemo.app.ui.scan

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.CoverImageStore
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.data.remote.BookLookupRepository
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.domain.LookupResult
import jp.yomumemo.app.util.Isbn
import jp.yomumemo.app.util.ScanOutcome
import jp.yomumemo.app.util.interpretScannedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanUiState {
    /** 自前のカメラ(BarcodeCameraScanner)でスキャン中。 */
    data object Idle : ScanUiState

    /**
     * カメラを閉じた・読み取れなかったときの受け皿。
     *
     * 利用者が自分で閉じた場合と、読み取りに失敗した場合を区別しにくいため、
     * どちらも同じ画面に集約し、心当たりのある人向けのヒントだけを添える。
     */
    data class Choose(val hint: String? = null) : ScanUiState

    /** ISBN を手で入力する。 */
    data object TypingIsbn : ScanUiState

    /** 書誌検索を介さず本の情報を直接入力する。 */
    data object TypingBook : ScanUiState

    data class LookingUp(val isbn13: String) : ScanUiState

    /** 書誌が取れた。登録するか確認する。 */
    data class Confirm(
        val metadata: BookMetadata,
        /** 既に本棚にある場合はその本。二重登録を防ぐ。 */
        val existing: BookEntity? = null,
    ) : ScanUiState

    /** 無料版の登録上限に達した。 */
    data class LimitReached(val limit: Int) : ScanUiState

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

private const val TAG = "ScanViewModel"

class ScanViewModel(
    private val lookup: BookLookupRepository,
    private val books: BookRepository,
    private val entitlements: EntitlementRepository,
    private val coverImages: CoverImageStore,
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

    /** カメラを閉じた。戻らずに代替手段を示す。 */
    fun onScanCancelled() {
        _state.value = ScanUiState.Choose()
    }

    fun showScanner() {
        _state.value = ScanUiState.Idle
    }

    fun showIsbnInput() {
        _state.value = ScanUiState.TypingIsbn
    }

    fun showBookInput() {
        _state.value = ScanUiState.TypingBook
    }

    /**
     * カメラを起動できなかった、または解析中にエラーが起きた。
     * 自分で閉じた場合との区別がつかないため、断定を避けたヒントを添えるに留める。
     */
    fun onScanFailed() {
        _state.value = ScanUiState.Choose(
            hint = "うまく読み取れないときは、明るい場所でバーコード全体が枠に入るようにするか、" +
                "ISBN の手入力をお試しください。",
        )
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

    /**
     * 無料版の上限に達していないか。
     * 上限の決め方は EntitlementRepository に閉じてあるので、ここは問い合わせるだけ。
     */
    private suspend fun withinBookLimit(): Boolean {
        val premium = entitlements.isPremium.first()
        val limit = entitlements.bookLimit(premium) ?: return true
        return books.countActive() < limit
    }

    fun save(metadata: BookMetadata, status: ReadingStatus, coverUri: Uri? = null) {
        viewModelScope.launch {
            if (!withinBookLimit()) {
                _state.value = ScanUiState.LimitReached(EntitlementRepository.FREE_BOOK_LIMIT)
                return@launch
            }
            // データベースへの書き込みは、想定していない例外(例:一意制約違反)が
            // 起きてもアプリごと落とさない。原因は addFromMetadata 側で対処済みだが、
            // 予期しない状況が起きても致命的にならないよう最後の砦として保護しておく。
            runCatching { books.addFromMetadata(metadata, status) }
                .onSuccess { id ->
                    saveCoverIfAny(id, coverUri)
                    _state.value = ScanUiState.Saved(id)
                }
                .onFailure { e ->
                    Log.w(TAG, "本の登録に失敗しました", e)
                    _state.value = ScanUiState.Failed("本を登録できませんでした。もう一度お試しください。")
                }
        }
    }

    fun saveManual(
        title: String,
        authors: String,
        publisher: String,
        isbn13: String?,
        status: ReadingStatus,
        coverUri: Uri? = null,
    ) {
        if (title.isBlank()) {
            _state.value = ScanUiState.Failed("タイトルを入力してください。")
            return
        }
        viewModelScope.launch {
            if (!withinBookLimit()) {
                _state.value = ScanUiState.LimitReached(EntitlementRepository.FREE_BOOK_LIMIT)
                return@launch
            }
            runCatching {
                books.addManual(
                    title = title,
                    authors = authors.split("、", ",").map(String::trim).filter(String::isNotEmpty),
                    publisher = publisher,
                    isbn13 = isbn13,
                    status = status,
                )
            }
                .onSuccess { id ->
                    saveCoverIfAny(id, coverUri)
                    _state.value = ScanUiState.Saved(id)
                }
                .onFailure { e ->
                    Log.w(TAG, "本の登録に失敗しました", e)
                    _state.value = ScanUiState.Failed("本を登録できませんでした。もう一度お試しください。")
                }
        }
    }

    fun reset() {
        _state.value = ScanUiState.Idle
    }

    /**
     * 撮影した表紙があれば端末内へコピーして紐づける。
     * 失敗しても本自体の登録は既に成功しているので、ログに残すだけで登録処理は継続する。
     */
    private suspend fun saveCoverIfAny(bookId: String, coverUri: Uri?) {
        val uri = coverUri ?: return
        val path = withContext(Dispatchers.IO) { coverImages.save(bookId, uri) }
        if (path != null) {
            books.updateLocalCoverPath(bookId, path)
        } else {
            Log.w(TAG, "撮影した表紙の保存に失敗しました")
        }
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
