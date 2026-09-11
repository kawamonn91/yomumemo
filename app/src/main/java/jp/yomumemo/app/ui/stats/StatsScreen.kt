package jp.yomumemo.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.domain.ReadingStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("読書の統計") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (!state.isPremium) {
                Locked(onOpenPaywall)
                return@Column
            }

            val stats = state.stats
            StreakCard(stats.noteStreakDays)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("読了", stats.finished.toString() + " 冊", Modifier.weight(1f))
                StatTile("読書中", stats.reading.toString() + " 冊", Modifier.weight(1f))
                StatTile("読みたい", stats.want.toString() + " 冊", Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("今年の読了", stats.finishedThisYear.toString() + " 冊", Modifier.weight(1f))
                StatTile("今月の読了", stats.finishedThisMonth.toString() + " 冊", Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("メモ", stats.totalNotes.toString() + " 件", Modifier.weight(1f))
                StatTile(
                    "1冊あたり",
                    String.format("%.1f", stats.notesPerBook) + " 件",
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            StatTile("読んだページ", stats.pagesRead.toString() + " ページ", Modifier.fillMaxWidth())

            if (stats.pagesRead == 0 && stats.finished > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ページ数は書誌データに含まれていないことが多いため、" +
                        "本の詳細画面で手入力すると集計に反映されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(days: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                days.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (days > 0) "日続けてメモを書いています" else "今日からまた始めましょう",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Locked(onOpenPaywall: () -> Unit) {
    Column {
        Text("統計はプレミアムの機能です", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "読了した冊数、読んだページ数、メモを書き続けている日数などを振り返れます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
            Text("プレミアムを見る")
        }
    }
}
