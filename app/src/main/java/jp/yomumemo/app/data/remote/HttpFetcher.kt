package jp.yomumemo.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP GET の最小の抽象。
 * 書誌APIの呼び出し元をテストから差し替えられるようにするためだけに存在する。
 */
interface HttpFetcher {
    /** 本文を返す。2xx 以外・通信失敗は null。 */
    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String?
}

class OkHttpFetcher(private val callFactory: Call.Factory) : HttpFetcher {

    override suspend fun getString(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        runCatching {
            callFactory.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    companion object {
        private const val USER_AGENT = "YomuMemo/1.0 (Android)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
