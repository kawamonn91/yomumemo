package jp.yomumemo.app

import android.content.Context
import jp.yomumemo.app.data.db.YomuMemoDatabase
import jp.yomumemo.app.data.remote.AndroidAppIdentity
import jp.yomumemo.app.data.remote.BookLookupRepository
import jp.yomumemo.app.data.remote.GoogleBooksApi
import jp.yomumemo.app.data.remote.HttpFetcher
import jp.yomumemo.app.data.remote.OkHttpFetcher
import jp.yomumemo.app.data.remote.OpenBdApi
import okhttp3.OkHttpClient

/**
 * 依存の組み立て。
 *
 * Hilt などのDIフレームワークは使っていない。この規模では生成される複雑さと
 * ビルド時間に見合わないため、明示的なコンテナで足りる。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: YomuMemoDatabase by lazy { YomuMemoDatabase.build(appContext) }

    private val okHttpClient: OkHttpClient by lazy { OkHttpFetcher.defaultClient() }

    private val httpFetcher: HttpFetcher by lazy { OkHttpFetcher(okHttpClient) }

    /**
     * 表紙・ページ数・内容紹介の補完元。
     *
     * キーが未設定なら補完なしで動かす。Google Books はキー無しだと
     * 共有の匿名プロジェクトの割り当てを使うことになり、それが既に枯渇していて
     * 必ず失敗するため、無駄な通信をせず最初から諦める。
     */
    private val enrichmentSource by lazy {
        BuildConfig.GOOGLE_BOOKS_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { key ->
                GoogleBooksApi(
                    http = httpFetcher,
                    apiKey = key,
                    // キーを Android アプリ制限にしている場合、このヘッダが無いと 403 になる
                    appIdentity = AndroidAppIdentity.of(appContext),
                )
            }
    }

    val bookLookup: BookLookupRepository by lazy {
        BookLookupRepository(openBd = OpenBdApi(httpFetcher), enrichment = enrichmentSource)
    }

    /** 表紙の補完が有効かどうか。設定画面で状態を出すために使う。 */
    val isCoverEnrichmentEnabled: Boolean
        get() = BuildConfig.GOOGLE_BOOKS_API_KEY.isNotBlank()
}
