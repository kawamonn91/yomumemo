package jp.yomumemo.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.backup.rememberDriveAuthorizationLauncher
import jp.yomumemo.app.export.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 書き出す内容と形式は、保存先を選ぶダイアログから戻ってきたときに必要になる
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var pendingFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }
    var backupContent by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(pendingFormat.mimeType),
    ) { uri: Uri? ->
        val content = pendingContent
        pendingContent = null
        if (uri == null) {
            viewModel.onExportCancelled()
            return@rememberLauncherForActivityResult
        }
        val ok = content != null && writeToUri(context, uri, content)
        viewModel.onExportFinished(ok, pendingFormat)
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val content = backupContent
        backupContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        viewModel.onBackupFinished(writeToUri(context, uri, content))
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = readFromUri(context, uri)
        if (content == null) {
            viewModel.onBackupFinished(false)
        } else {
            viewModel.restoreFrom(content)
        }
    }

    val authorizeForDriveBackup = rememberDriveAuthorizationLauncher(
        onAuthorized = viewModel::backupToDrive,
        onFailed = { },
    )
    val authorizeForDriveRestore = rememberDriveAuthorizationLauncher(
        onAuthorized = viewModel::restoreFromDrive,
        onFailed = { },
    )

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("設定") },
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
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPaywall)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (state.isPremium) "プレミアム" else "無料版",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.isPremium) {
                            "すべての機能が使えます。ありがとうございます。"
                        } else {
                            "登録 ${state.bookCount} 冊 / メモ ${state.noteCount} 件。" +
                                "タップしてプレミアムの内容を見る。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("読書の記録")
            OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                Text("統計を見る")
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("メモの書き出し")
            Text(
                if (state.isPremium) {
                    "保存先はご自身で選べます。書き出したファイルは端末に残ります。"
                } else {
                    "書き出しはプレミアムの機能です。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            ExportFormat.entries.forEach { format ->
                OutlinedButton(
                    onClick = {
                        if (!state.isPremium) {
                            onOpenPaywall()
                            return@OutlinedButton
                        }
                        pendingFormat = format
                        viewModel.buildExport(format) { content ->
                            pendingContent = content
                            createDocument.launch(viewModel.suggestedFileName(format))
                        }
                    },
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(format.label + " で書き出す")
                }
            }

            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("バックアップ")
            Text(
                "端末を変えるときや、万一に備えて控えを取っておけます。" +
                    "復元は置き換えではなく統合なので、手元の新しいメモが消えることはありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    viewModel.buildBackup { content ->
                        backupContent = content
                        createBackup.launch(viewModel.backupFileName())
                    }
                },
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("バックアップを保存") }

            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("バックアップから復元") }

            Spacer(Modifier.height(16.dp))
            Text(
                "または、自分のGoogleドライブに自動でバックアップ・復元できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = authorizeForDriveBackup,
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("Googleドライブに自動バックアップ") }
            OutlinedButton(
                onClick = authorizeForDriveRestore,
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("Googleドライブから復元") }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("このアプリについて")
            Text(
                "書誌情報は openBD から取得しています。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "メモと本の情報は端末内にのみ保存されます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

/**
 * ユーザーが選んだ場所へ書き込む。
 * 失敗しても落とさず false を返す。保存先が外部ストレージやクラウドの場合、
 * 権限や通信の都合で書き込めないことがある。
 */
private fun readFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

private fun writeToUri(context: Context, uri: Uri, content: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    } ?: return false
    true
}.getOrDefault(false)
