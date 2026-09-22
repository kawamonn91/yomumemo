package jp.yomumemo.app.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

private val DRIVE_FILE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

/**
 * 自分のバックアップをGoogleドライブへ保存・復元するための、drive.fileスコープの
 * アクセストークンを取る。このスコープは「アプリ自身が作ったファイルだけ」に
 * アクセスを限定できる、Googleが「機微でない」と分類している安全なスコープ。
 *
 * 既に許可済みなら確認画面なしで即座にトークンが返り、未許可ならGoogleの同意画面を
 * 一度だけ表示する。
 */
@Composable
fun rememberDriveAuthorizationLauncher(
    onAuthorized: (String) -> Unit,
    onFailed: (Exception) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val client = remember { Identity.getAuthorizationClient(context) }
    val request = remember { AuthorizationRequest.builder().setRequestedScopes(listOf(DRIVE_FILE_SCOPE)).build() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching {
            val authResult = client.getAuthorizationResultFromIntent(result.data)
            authResult.accessToken?.let(onAuthorized) ?: onFailed(IllegalStateException("同意が完了しませんでした"))
        }.onFailure { onFailed(it as? Exception ?: Exception(it)) }
    }

    return start@{
        client.authorize(request)
            .addOnSuccessListener { authResult ->
                val pendingIntent = authResult.pendingIntent
                if (authResult.hasResolution() && pendingIntent != null) {
                    runCatching {
                        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }.onFailure { onFailed(it as? Exception ?: Exception(it)) }
                } else {
                    authResult.accessToken?.let(onAuthorized) ?: onFailed(IllegalStateException("同意が完了しませんでした"))
                }
            }
            .addOnFailureListener(onFailed)
    }
}
