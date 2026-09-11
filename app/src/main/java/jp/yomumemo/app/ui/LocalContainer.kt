package jp.yomumemo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import jp.yomumemo.app.AppContainer
import jp.yomumemo.app.YomuMemoApp

/** 画面から依存コンテナを取り出す。 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as YomuMemoApp).container
    }
}
