package jp.yomumemo.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * 表紙を自分で撮影するためのランチャーを返す。
 *
 * Googleドライブの書類スキャン機能と同じML Kit Document Scannerを使う。
 * 起動すると専用のカメラUIが開き、辺の自動検出・トリミング・傾き補正まで
 * 端末側で行った上で、きれいに切り抜かれた画像のUriを [onScanned] に渡す。
 * 自前で画像処理を書く必要はない。ユーザーがキャンセルした場合は呼ばれない。
 */
@Composable
fun rememberDocumentScannerLauncher(onScanned: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages
            ?.firstOrNull()
            ?.imageUri
            ?.let(onScanned)
    }
    return start@{
        val activity = context.findActivity() ?: return@start
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // カメラが使えない等。無音で諦める(呼び出し側は結果が来なければ何も変わらない)
            }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
