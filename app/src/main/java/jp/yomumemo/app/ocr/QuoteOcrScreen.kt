package jp.yomumemo.app.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.Executors

/**
 * 本のページを撮って引用文を取り込む。
 *
 * バーコード読み取りと違い、こちらは自前のカメラを使うためカメラ権限が要る。
 * 権限を求めるのはこの画面に来たときだけにしてある。登録しかしない利用者に
 * 最初から権限を求めると、それだけで離脱する人がいるため。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteOcrScreen(
    onTextRecognized: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var isRecognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("引用を撮る") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize()) {
            if (!hasPermission) {
                PermissionNotice(
                    alreadyAsked = permissionRequested,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBack = onBack,
                )
                return@Column
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture,
                                )
                            }.onFailure { e ->
                                Log.w(TAG, "カメラを開始できません", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                if (isRecognizing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "読み取りたい文の部分が大きく写るようにしてください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        error = null
                        isRecognizing = true
                        imageCapture.takePicture(
                            executor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    recognize(image) { text ->
                                        isRecognizing = false
                                        if (text.isBlank()) {
                                            error = "文字を読み取れませんでした。もう少し近づいてお試しください。"
                                        } else {
                                            onTextRecognized(text)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.w(TAG, "撮影に失敗", exception)
                                    isRecognizing = false
                                    error = "撮影できませんでした。"
                                }
                            },
                        )
                    },
                    enabled = !isRecognizing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("撮って読み取る") }
            }
        }
    }
}

@Composable
private fun PermissionNotice(alreadyAsked: Boolean, onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("カメラの使用を許可してください", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "本のページを撮影して引用文を読み取るために使います。" +
                "撮影した写真は保存も送信もせず、読み取った文字だけをメモに残します。",
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
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
    }
}

private const val TAG = "QuoteOcr"

/** 撮影した画像から日本語の文字を読み取る。端末内で完結し、画像は送信しない。 */
private fun recognize(image: ImageProxy, onResult: (String) -> Unit) {
    val mediaImage = image.image
    if (mediaImage == null) {
        image.close()
        onResult("")
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
    val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    recognizer.process(input)
        .addOnSuccessListener { result -> onResult(QuoteTextCleaner.clean(result.text)) }
        .addOnFailureListener { e ->
            Log.w(TAG, "文字認識に失敗", e)
            onResult("")
        }
        .addOnCompleteListener { image.close() }
}
