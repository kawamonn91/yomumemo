package jp.yomumemo.app.ui.scan

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

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

    fun scan(
        context: Context,
        onSuccess: (String) -> Unit,
        onCancelled: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            // 棚差しの本や小さなバーコードでも合焦しやすくする
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) onFailure() else onSuccess(raw)
            }
            .addOnCanceledListener(onCancelled)
            .addOnFailureListener { error ->
                // 実機・エミュレータともに、戻る操作は addOnCanceledListener ではなく
                // CODE_SCANNER_CANCELLED の失敗として届くことがある。
                // ユーザーの意図は「やめた」なので、失敗ではなくキャンセルとして扱う。
                val cancelled = error is MlKitException &&
                    error.errorCode == MlKitException.CODE_SCANNER_CANCELLED
                if (cancelled) {
                    onCancelled()
                } else {
                    // SDK の例外メッセージは英語なので、画面には出さずログにだけ残す
                    val code = (error as? MlKitException)?.errorCode
                    Log.w(TAG, "バーコードの読み取りに失敗しました errorCode=" + code, error)
                    onFailure()
                }
            }
    }
}
