package jp.yomumemo.app.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    onOpenBook: (String) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本棚") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "メモを検索")
                    }
                    IconButton(onClick = onOpenPaywall) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = "プレミアム")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                text = { Text("本を追加") },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize()) {
            StatusFilterRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )

            when {
                state.isLoading -> Box_CenteredProgress()
                state.books.isEmpty() -> EmptyShelf(hasAnyBook = state.totalCount > 0)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookGridItem(book = book, onClick = { onOpenBook(book.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Box_CenteredProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterRow(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
) {
    val options: List<Pair<ReadingStatus?, String>> = listOf(
        null to "すべて",
        ReadingStatus.READING to "読書中",
        ReadingStatus.WANT to "読みたい",
        ReadingStatus.DONE to "読了",
        ReadingStatus.PAUSED to "中断",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun BookGridItem(book: BookEntity, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                // 書籍の一般的な判型に近い比率
                .aspectRatio(0.68f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.authors.isNotEmpty()) {
            Text(
                text = book.authors.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyShelf(hasAnyBook: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasAnyBook) "この条件の本はありません" else "まだ本がありません",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasAnyBook) {
                "絞り込みを変えてみてください。"
            } else {
                "「本を追加」から、本の裏表紙にあるバーコードを読み取って登録できます。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
