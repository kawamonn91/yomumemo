package jp.yomumemo.app.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.data.db.entity.FolderEntity

/** フォルダの作成・改名・削除。本の割り当ては本の詳細画面から行う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementScreen(viewModel: FolderManagementViewModel, onBack: () -> Unit) {
    val folders by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<FolderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("フォルダ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "フォルダを作成")
            }
        },
    ) { inner ->
        if (folders.isEmpty()) {
            Column(
                Modifier.padding(inner).fillMaxSize().padding(20.dp),
            ) {
                Text(
                    "まだフォルダがありません。右下の+から、ジャンルなど好きな名前で作成できます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(folders, key = FolderEntity::id) { folder ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                            Row {
                                IconButton(onClick = { editingFolder = folder }) {
                                    Icon(Icons.Default.Edit, contentDescription = "改名")
                                }
                                IconButton(onClick = { deletingFolder = folder }) {
                                    Icon(Icons.Default.Delete, contentDescription = "削除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "フォルダを作成",
            initialName = "",
            onConfirm = { viewModel.create(it); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingFolder?.let { folder ->
        FolderNameDialog(
            title = "フォルダ名を変更",
            initialName = folder.name,
            onConfirm = { viewModel.rename(folder.id, it); editingFolder = null },
            onDismiss = { editingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("「${folder.name}」を削除しますか?") },
            text = { Text("フォルダ内の本は削除されず、未分類に戻ります。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(folder.id); deletingFolder = null }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun FolderNameDialog(title: String, initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("フォルダ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
