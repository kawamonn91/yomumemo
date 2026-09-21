package jp.yomumemo.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BarcodeCameraScanner"

/**
 * 自前のカメラでバーコードを読み取る(引用文の読み取り[jp.yomumemo.app.ocr.QuoteOcrScreen]と同じ
 * CameraX + オンデバイスML Kit方式)。
 *
 * 以前は Google Play services の「code scanner」(別プロセスの専用UI、カメラ権限不要)を
 * 使っていたが、そちらは2023年8月から更新が止まっており、モジュール初期化まわりの
 * NullPointerExceptionが未修正のまま残っていて、起動直後にアプリごと落ちることがあった
 * (この不具合はアプリのコードでは回避できないことを実機再現とSDK内部の調査で確認済み)。
 * 自前実装にしたことで、カメラ権限をこの画面で求める必要がある。
 */
@Composable
fun BarcodeCameraScanner(
    onScanned: (String) -> Unit,
    onFailed: () -> Unit,
    onManualEntry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        CameraPermissionNotice(
            alreadyAsked = permissionRequested,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onManualEntry = onManualEntry,
        )
        return
    }

    CameraPreviewWithAnalysis(onScanned = onScanned, onFailed = onFailed, onManualEntry = onManualEntry, onCancel = onCancel)
}

@Composable
private fun CameraPreviewWithAnalysis(
    onScanned: (String) -> Unit,
    onFailed: () -> Unit,
    onManualEntry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .build(),
        )
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }

    // 1回のスキャンで複数フレームから重複してコールバックしないようにするガード。
    // アナライザーはCameraXの別スレッドで動くため AtomicBoolean を使う。
    val handled = remember { AtomicBoolean(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null || handled.get()) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(input)
                                    .addOnSuccessListener { barcodes ->
                                        val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                        if (raw != null && handled.compareAndSet(false, true)) {
                                            onScanned(raw)
                                        }
                                    }
                                    .addOnFailureListener { e -> Log.w(TAG, "解析に失敗しました", e) }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }.onFailure { e ->
                        Log.w(TAG, "カメラを起動できません", e)
                        onFailed()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        // バーコードを合わせる位置の目安(装飾のみ。判定領域は絞っていない)
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .aspectRatio(1.8f)
                .border(3.dp, Color.White, MaterialTheme.shapes.medium),
        )

        TextButton(
            onClick = onManualEntry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small),
        ) { Text("ISBN を手入力する", color = Color.White) }

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small),
        ) { Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Color.White) }
    }
}

@Composable
private fun CameraPermissionNotice(alreadyAsked: Boolean, onRequest: () -> Unit, onManualEntry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("カメラの使用を許可してください", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "本のバーコードを読み取るために使います。撮影した映像は保存も送信もせず、" +
                "読み取ったバーコードの番号だけを使います。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (alreadyAsked) {
            Spacer(Modifier.height(12.dp))
            Text(
                "許可しない設定になっている場合は、端末の設定から変更できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("許可する") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) { Text("ISBN を手入力する") }
    }
}
