package jp.yomumemo.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.domain.ReadingStats
import jp.yomumemo.app.domain.ReadingStatsCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val stats: ReadingStats = ReadingStats(),
    val isPremium: Boolean = false,
)

class StatsViewModel(
    books: BookRepository,
    notes: NoteRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> =
        combine(
            books.observeAll(),
            // 統計は全メモを対象にする。件数の上限は設けない。
            notes.observeRecent(limit = Int.MAX_VALUE),
            entitlements.isPremium,
        ) { bookList, noteList, premium ->
            StatsUiState(
                stats = ReadingStatsCalculator.calculate(bookList, noteList),
                isPremium = premium,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )
}
