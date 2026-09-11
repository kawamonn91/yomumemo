package jp.yomumemo.app.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Google API キーを「Android アプリ」で制限した場合に必要な識別情報。
 *
 * Cloud Console でキーにパッケージ名と署名証明書の SHA-1 を登録しても、
 * リクエストが下記2つのヘッダを送らなければ制限は機能しない
 * (Google のクライアントライブラリはこれを自動で付けるが、素の OkHttp は付けない)。
 * ヘッダが無いと、制限付きキーでは 403 が返る。
 */
data class AndroidAppIdentity(
    val packageName: String,
    val certSha1: String,
) {
    fun asHeaders(): Map<String, String> = mapOf(
        "X-Android-Package" to packageName,
        "X-Android-Cert" to certSha1,
    )

    companion object {

        /** 自分自身の署名証明書から算出する。取得できなければ null。 */
        fun of(context: Context): AndroidAppIdentity? {
            val sha1 = signingCertSha1(context) ?: return null
            return AndroidAppIdentity(context.packageName, sha1)
        }

        @Suppress("DEPRECATION")
        private fun signingCertSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val name = context.packageName
            val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return null
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            } else {
                pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures
            }

            val first = certificates?.firstOrNull() ?: return null
            MessageDigest.getInstance("SHA-1")
                .digest(first.toByteArray())
                .joinToString("") { "%02X".format(it) }
        }.getOrNull()
    }
}
