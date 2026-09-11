package jp.yomumemo.app.data.remote

/** URL ごとに決め打ちの本文を返すテスト用 [HttpFetcher]。 */
class FakeHttpFetcher(
    private val responses: Map<String, String?> = emptyMap(),
    private val default: String? = null,
) : HttpFetcher {

    val requestedUrls = mutableListOf<String>()

    override suspend fun getString(url: String): String? {
        requestedUrls += url
        val match = responses.entries.firstOrNull { url.contains(it.key) }
        return if (match != null) match.value else default
    }
}

/** テストリソースから本文を読む。 */
fun loadResource(name: String): String =
    checkNotNull(object {}.javaClass.classLoader?.getResourceAsStream(name)) {
        "テストリソースが見つかりません: $name"
    }.bufferedReader().use { it.readText() }
