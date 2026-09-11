package jp.yomumemo.app.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yomumemo.app.billing.EntitlementRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 購入が通ったら自動的に閉じる
    LaunchedEffect(state.isPremium) {
        if (state.isPremium) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ヨムメモ プレミアム") },
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
            Text(
                "一度の購入で、ずっと使えます",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "月額ではありません。買い切りです。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    FeatureRow("本を無制限に登録", "無料版は ${EntitlementRepository.FREE_BOOK_LIMIT} 冊まで")
                    FeatureRow("引用をカメラで取り込む", "本のページを撮ると文字を読み取ります")
                    FeatureRow("メモの書き出し", "Markdown / CSV / Obsidian 形式")
                    FeatureRow("読書の統計", "冊数・ページ数・続いている日数")
                    FeatureRow("Google ドライブ同期", "機種変更しても引き継げます")
                }
            }

            Spacer(Modifier.height(24.dp))

            when {
                state.isPremium -> Text(
                    "購入済みです。ありがとうございます。",
                    style = MaterialTheme.typography.titleMedium,
                )

                else -> {
                    Button(
                        onClick = { (context as? Activity)?.let(viewModel::purchase) },
                        enabled = state.canPurchase && !state.isPurchaseInFlight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isPurchaseInFlight) {
                            CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        } else {
                            Text(state.price?.let { "$it で購入" } ?: "購入する")
                        }
                    }

                    if (!state.canPurchase) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "商品情報を取得しています。表示されない場合は、通信状況と " +
                                "Google Play ストアへのログインをご確認ください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = viewModel::restore,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("購入を復元する") }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            state.restoreMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "メモの数は無料版でも制限していません。書き留めること自体を止めては、" +
                    "このアプリの意味が無くなるためです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureRow(title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
