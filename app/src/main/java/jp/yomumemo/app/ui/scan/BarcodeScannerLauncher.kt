package jp.yomumemo.app.ui.scan

import android.content.Context
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

    fun scan(
        context: Context,
        onSuccess: (String) -> Unit,
        onCancelled: () -> Unit,
        onFailure: (String?) -> Unit,
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
                if (raw.isNullOrBlank()) onFailure(null) else onSuccess(raw)
            }
            .addOnCanceledListener { onCancelled() }
            .addOnFailureListener { error -> onFailure(error.message) }
    }
}
