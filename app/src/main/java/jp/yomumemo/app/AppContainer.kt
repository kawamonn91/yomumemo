package jp.yomumemo.app

import android.content.Context
import jp.yomumemo.app.billing.BillingManager
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.data.db.YomuMemoDatabase
import jp.yomumemo.app.data.prefs.SettingsStore
import jp.yomumemo.app.data.remote.AndroidAppIdentity
import jp.yomumemo.app.data.remote.BookLookupRepository
import jp.yomumemo.app.data.remote.GoogleBooksApi
import jp.yomumemo.app.data.remote.HttpFetcher
import jp.yomumemo.app.data.remote.OkHttpFetcher
import jp.yomumemo.app.data.remote.OpenBdApi
import jp.yomumemo.app.data.repo.BookRepository
import jp.yomumemo.app.data.repo.NoteRepository
import jp.yomumemo.app.sync.SnapshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * 依存の組み立て。
 *
 * Hilt などのDIフレームワークは使っていない。この規模では生成される複雑さと
 * ビルド時間に見合わないため、明示的なコンテナで足りる。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** アプリの生存期間に紐づく作業用スコープ。課金の接続や問い合わせに使う。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: YomuMemoDatabase by lazy { YomuMemoDatabase.build(appContext) }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao()) }

    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }

    val entitlements: EntitlementRepository by lazy { EntitlementRepository(settings) }

    val snapshots: SnapshotRepository by lazy {
        SnapshotRepository(database.bookDao(), database.noteDao())
    }

    val billing: BillingManager by lazy {
        BillingManager(
            context = appContext,
            scope = appScope,
            onPurchaseVerified = { owned -> entitlements.grantFromPurchase(owned) },
        )
    }

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

    /**
     * 起動時に一度だけ呼ぶ。
     *
     * Play に購入状態を問い合わせ直すことで、機種変更や再インストールの後でも
     * 購入者が何もしなくても権利を取り戻せるようにする。
     * 問い合わせられなかった場合は控えている状態を維持する。
     */
    fun onAppStart() {
        billing.connect()
        appScope.launch {
            entitlements.applyOwnership(billing.refreshOwnership())
        }
    }
}
