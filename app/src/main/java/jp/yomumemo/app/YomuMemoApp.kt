package jp.yomumemo.app

import android.app.Application

class YomuMemoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 購入状態を Play に確認し直す (機種変更・再インストール後の復元)
        container.onAppStart()
    }
}
