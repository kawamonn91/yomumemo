package jp.yomumemo.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.data.db.entity.ReadingStatus
import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Idle に入るたびにスキャナを起動する(再スキャンもこの経路で行う)
    LaunchedEffect(state) {
        if (state is ScanUiState.Idle) {
            BarcodeScannerLauncher.scan(
                context = context,
                onSuccess = viewModel::onScanned,
                onCancelled = viewModel::onScanCancelled,
                onFailure = viewModel::onScanFailed,
            )
        }
        if (state is ScanUiState.Saved) {
            onSaved((state as ScanUiState.Saved).bookId)
            viewModel.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本を追加") },
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
            when (val s = state) {
                ScanUiState.Idle -> Centered { Text("バーコードを読み取っています…") }

                is ScanUiState.Choose -> ChooseMethod(
                    hint = s.hint,
                    onScan = viewModel::showScanner,
                    onTypeIsbn = viewModel::showIsbnInput,
                    onTypeBook = viewModel::showBookInput,
                    onBack = onBack,
                )

                ScanUiState.TypingIsbn -> IsbnInput(
                    onSubmit = viewModel::lookUpTyped,
                    onCancel = viewModel::onScanCancelled,
                )

                ScanUiState.TypingBook -> ManualEntry(
                    isbn13 = null,
                    headline = "本の情報を入力",
                    description = "ISBN が無い本や、書誌データに登録されていない本を手で登録します。",
                    onSave = viewModel::saveManual,
                    onRescan = viewModel::onScanCancelled,
                )

                is ScanUiState.LookingUp -> Centered {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("書誌情報を検索中… (${s.isbn13})")
                }

                is ScanUiState.Confirm -> ConfirmBook(
                    metadata = s.metadata,
                    alreadyInShelf = s.existing != null,
                    onSave = viewModel::save,
                    onOpenExisting = { s.existing?.let { onSaved(it.id) } },
                    onRescan = viewModel::reset,
                )

                ScanUiState.PriceCodeScanned -> PriceCodeGuidance(
                    onRetry = viewModel::showScanner,
                    onTypeIsbn = viewModel::showIsbnInput,
                )

                ScanUiState.NotABook -> Message(
                    title = "書籍のバーコードではないようです",
                    body = "読み取れたのは書籍以外の商品コードでした。本の裏表紙にある " +
                        "978 から始まるバーコードを読み取ってください。",
                    onRetry = viewModel::showScanner,
                    onTypeIsbn = viewModel::showIsbnInput,
                )

                is ScanUiState.NotFound -> ManualEntry(
                    isbn13 = s.isbn13,
                    headline = "この本の書誌情報が見つかりませんでした",
                    description = "データベースに未登録の本です。お手数ですが手入力で登録してください。",
                    onSave = viewModel::saveManual,
                    onRescan = viewModel::showScanner,
                )

                is ScanUiState.Failed -> Message(
                    title = "読み取れませんでした",
                    body = s.message,
                    onRetry = viewModel::showScanner,
                    onTypeIsbn = viewModel::showIsbnInput,
                )

                is ScanUiState.Saved -> Centered { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

/**
 * 日本の書籍バーコードは上下2段になっており、下段は価格を表す別のコード。
 * 下段を読んでしまう誤操作が非常に多いため、何が起きたかを具体的に説明する。
 */
@Composable
private fun PriceCodeGuidance(onRetry: () -> Unit, onTypeIsbn: () -> Unit) {
    Column {
        Text("下の段を読み取りました", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "日本の本には、バーコードが上下2段で印刷されています。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("上段  978… ←  これを読み取ってください", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "下段  192…      価格を表すコードで、本の特定には使えません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("上段を読み取る")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onTypeIsbn, modifier = Modifier.fillMaxWidth()) {
            Text("ISBN を手入力する")
        }
    }
}

@Composable
private fun Message(title: String, body: String, onRetry: () -> Unit, onTypeIsbn: () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("もう一度読み取る")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onTypeIsbn, modifier = Modifier.fillMaxWidth()) {
            Text("ISBN を手入力する")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmBook(
    metadata: BookMetadata,
    alreadyInShelf: Boolean,
    onSave: (BookMetadata, ReadingStatus) -> Unit,
    onOpenExisting: () -> Unit,
    onRescan: () -> Unit,
) {
    var status by rememberSaveable { mutableStateOf(ReadingStatus.WANT) }

    Column {
        Row {
            BookCover(
                title = metadata.title,
                coverUrl = metadata.coverUrl,
                modifier = Modifier.width(96.dp).aspectRatio(0.68f),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(metadata.title, style = MaterialTheme.typography.titleMedium)
                metadata.subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (metadata.authors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(metadata.authors.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                }
                listOfNotNull(metadata.publisher, metadata.publishedDate)
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

        if (alreadyInShelf) {
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("この本は既に本棚にあります", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenExisting) { Text("登録済みの本を開く") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("読書状態", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReadingStatus.WANT to "読みたい",
                ReadingStatus.READING to "読書中",
                ReadingStatus.DONE to "読了",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(metadata, status) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("本棚に追加") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
            Text("別の本を読み取る")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntry(
    isbn13: String?,
    headline: String,
    description: String,
    onSave: (String, String, String, String?, ReadingStatus) -> Unit,
    onRescan: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var authors by rememberSaveable { mutableStateOf("") }
    var publisher by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(ReadingStatus.WANT) }

    Column {
        Text(headline, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        isbn13?.let {
            Spacer(Modifier.height(8.dp))
            Text("ISBN: $it", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("タイトル (必須)") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = authors,
            onValueChange = { authors = it },
            label = { Text("著者 (「、」区切りで複数可)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = publisher,
            onValueChange = { publisher = it },
            label = { Text("出版社") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ReadingStatus.WANT to "読みたい",
                ReadingStatus.READING to "読書中",
                ReadingStatus.DONE to "読了",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(title, authors, publisher, isbn13, status) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("本棚に追加") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
            Text("もう一度読み取る")
        }
    }
}

/**
 * カメラを閉じたときの受け皿。
 * ここで行き止まりにすると「バーコードが読めない本は登録できない」アプリになってしまう。
 */
@Composable
private fun ChooseMethod(
    hint: String?,
    onScan: () -> Unit,
    onTypeIsbn: () -> Unit,
    onTypeBook: () -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Text("本の追加方法", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Text("バーコードを読み取る")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onTypeIsbn, modifier = Modifier.fillMaxWidth()) {
            Text("ISBN を手入力する")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onTypeBook, modifier = Modifier.fillMaxWidth()) {
            Text("本の情報を直接入力する")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            hint ?: "バーコードが汚れている、カバーで隠れている、電子書籍で現物が無いといった場合は " +
                "ISBN の手入力が使えます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBack) { Text("本棚に戻る") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsbnInput(onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var isbn by rememberSaveable { mutableStateOf("") }
    // ハイフンを除いた桁数で判定する (978-4-87311-565-8 のような入力を許すため)
    val digits = isbn.count { it.isDigit() || it == 'X' || it == 'x' }

    Column {
        Text("ISBN を入力", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "本の裏表紙やカバー袖に印刷されている番号です。ハイフンは入れても入れなくても構いません。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = isbn,
            onValueChange = { isbn = it.take(20) },
            label = { Text("ISBN (10桁 または 13桁)") },
            placeholder = { Text("978-4-87311-565-8") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(isbn) },
            enabled = digits == 10 || digits == 13,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("この ISBN で検索") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("戻る")
        }
    }
}
