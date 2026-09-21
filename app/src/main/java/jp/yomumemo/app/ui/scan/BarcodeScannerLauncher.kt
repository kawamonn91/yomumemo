package jp.yomumemo.app.ui.scan

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google code scanner を起動する。
 *
 * Play 開発者サービスがスキャン用の画面ごと提供するため、
 * **カメラ権限を要求する必要がない**(権限ダイアログを出さずに済むので、
 * 初回登録の離脱が減る)。自前のカメラ実装も不要。
 *
 * 書籍のバーコードは EAN-13。念のため EAN-8 も許可しておく。
 */
object BarcodeScannerLauncher {

    private const val TAG = "BarcodeScanner"

    /**
     * 呼び出し側(Compose の LaunchedEffect)が短時間に二重に呼んでしまっても、
     * SDK に `CODE_SCANNER_TASK_IN_PROGRESS` で弾かれてユーザーにエラーを見せることがないよう、
     * 実行中は新しい呼び出しを無視する。
     */
    private val scanning = AtomicBoolean(false)

    fun scan(
        context: Context,
        onSuccess: (String) -> Unit,
        onCancelled: () -> Unit,
        onFailure: (BarcodeScanFailure) -> Unit,
    ) {
        if (!scanning.compareAndSet(false, true)) {
            Log.w(TAG, "スキャンが既に実行中のため、今回の呼び出しは無視します")
            return
        }

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            // 棚差しの本や小さなバーコードでも合焦しやすくする
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                scanning.set(false)
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) onFailure(BarcodeScanFailure.Unknown) else onSuccess(raw)
            }
            .addOnCanceledListener {
                scanning.set(false)
                onCancelled()
            }
            .addOnFailureListener { error ->
                scanning.set(false)
                val code = (error as? MlKitException)?.errorCode
                // 実機・エミュレータともに、戻る操作は addOnCanceledListener ではなく
                // CODE_SCANNER_CANCELLED の失敗として届くことがある。
                // ユーザーの意図は「やめた」なので、失敗ではなくキャンセルとして扱う。
                if (code == MlKitException.CODE_SCANNER_CANCELLED) {
                    onCancelled()
                    return@addOnFailureListener
                }
                // SDK の例外メッセージは英語なので、画面には出さずログにだけ残す
                Log.w(TAG, "バーコードの読み取りに失敗しました errorCode=$code", error)
                onFailure(
                    when (code) {
                        MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED ->
                            BarcodeScanFailure.CameraPermissionBlocked
                        MlKitException.CODE_SCANNER_UNAVAILABLE,
                        MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD ->
                            BarcodeScanFailure.ScannerUnavailable
                        else -> BarcodeScanFailure.Unknown
                    },
                )
            }
    }
}
