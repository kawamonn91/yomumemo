package jp.yomumemo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.backup.DriveBackupApi
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.export.BookWithNotes
import jp.yomumemo.app.export.ExportFormat
import jp.yomumemo.app.export.NoteExporter
import jp.yomumemo.app.sync.SnapshotRepository
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
    private val snapshots: SnapshotRepository,
    private val drive: DriveBackupApi,
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

    // --- バックアップと復元 ---

    /**
     * バックアップの中身を作る。書き出しと違い、こちらは削除済みの記録も含む
     * 完全な写しを作る。復元したときに削除が巻き戻らないようにするため。
     */
    fun buildBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            exporting.value = true
            try {
                onReady(snapshots.encode(snapshots.capture()))
            } finally {
                exporting.value = false
            }
        }
    }

    fun backupFileName(): String = "yomumemo-backup.json"

    /**
     * バックアップを取り込む。
     * 置き換えではなく統合なので、復元しても手元の新しい編集は消えない。
     */
    fun restoreFrom(content: String) {
        viewModelScope.launch {
            val snapshot = snapshots.decode(content)
            if (snapshot == null) {
                message.value = "バックアップとして読み取れないファイルでした。"
                return@launch
            }
            val result = snapshots.merge(snapshot)
            message.value = "復元しました。本 " + result.booksUpdatedLocally +
                " 件、メモ " + result.notesUpdatedLocally + " 件を取り込みました。"
        }
    }

    fun onBackupFinished(success: Boolean) {
        message.value = if (success) {
            "バックアップを保存しました。"
        } else {
            "バックアップを保存できませんでした。"
        }
    }

    // --- Googleドライブへの自動バックアップ(SAFでの手動保存とは別経路) ---

    fun backupToDrive(accessToken: String) {
        viewModelScope.launch {
            exporting.value = true
            runCatching {
                val content = snapshots.encode(snapshots.capture())
                val existingId = drive.findBackupFileId(accessToken)
                drive.save(accessToken, existingId, content)
            }
                .onSuccess { message.value = "Googleドライブにバックアップしました。" }
                .onFailure { message.value = it.message ?: "バックアップに失敗しました。" }
            exporting.value = false
        }
    }

    fun restoreFromDrive(accessToken: String) {
        viewModelScope.launch {
            exporting.value = true
            runCatching {
                val fileId = drive.findBackupFileId(accessToken) ?: throw NoDriveBackupException()
                val content = drive.read(accessToken, fileId)
                val snapshot = snapshots.decode(content) ?: throw IllegalStateException("バックアップとして読み取れないファイルでした。")
                snapshots.merge(snapshot)
            }
                .onSuccess { result ->
                    message.value = "Googleドライブから復元しました。本 " + result.booksUpdatedLocally +
                        " 件、メモ " + result.notesUpdatedLocally + " 件を取り込みました。"
                }
                .onFailure { message.value = it.message ?: "復元に失敗しました。" }
            exporting.value = false
        }
    }

    class NoDriveBackupException : Exception("Googleドライブにバックアップが見つかりませんでした。先に「今すぐバックアップ」を行ってください。")
}
