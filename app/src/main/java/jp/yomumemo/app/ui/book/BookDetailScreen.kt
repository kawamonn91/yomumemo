package jp.yomumemo.app.ui.book

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.ui.common.BookCover
import jp.yomumemo.app.ui.common.NoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.book?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "この本を削除")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNote,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("メモを追加") },
            )
        },
    ) { inner ->
        val book = state.book
        if (book == null) {
            Column(
                modifier = Modifier.padding(inner).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!state.isLoading) Text("本が見つかりません")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(inner).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BookHeader(book) }
            item {
                StatusSelector(
                    current = book.status,
                    onSelect = viewModel::updateStatus,
                )
            }
            item {
                ReadingProgress(
                    book = book,
                    onUpdate = viewModel::updateProgress,
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.notes.isEmpty()) "メモ" else "メモ (${state.notes.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.notes.isEmpty()) {
                item {
                    Text(
                        "気になった一文や思いついたことを、その場で書き留めておけます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.notes, key = { it.id }) { note ->
                NoteCard(note = note, onClick = { onEditNote(note.id) })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("この本を削除しますか?") },
            text = { Text("本に紐づくメモもすべて削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteBook(onBack)
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun BookHeader(book: BookEntity) {
    Row {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier.width(104.dp).aspectRatio(0.68f),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (book.authors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(book.authors.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
            }
            listOfNotNull(book.publisher, book.publishedDate)
                .takeIf { it.isNotEmpty() }
                ?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSelector(current: ReadingStatus, onSelect: (ReadingStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ReadingStatus.WANT to "読みたい",
            ReadingStatus.READING to "読書中",
            ReadingStatus.DONE to "読了",
            ReadingStatus.PAUSED to "中断",
        ).forEach { (value, label) ->
            FilterChip(
                selected = current == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ReadingProgress(book: BookEntity, onUpdate: (Int) -> Unit) {
    val total = book.pageCount
    if (total == null || total <= 0) return

    val ratio = (book.currentPage.toFloat() / total).coerceIn(0f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("読書の進み", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${book.currentPage} / $total ページ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 25, 50).forEach { step ->
                TextButton(onClick = { onUpdate(book.currentPage + step) }) {
                    Text("+$step")
                }
            }
        }
    }
}
