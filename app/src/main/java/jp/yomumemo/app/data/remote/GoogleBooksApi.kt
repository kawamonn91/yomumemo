package jp.yomumemo.app.data.remote

import jp.yomumemo.app.domain.BookMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表紙画像・内容紹介・ページ数を補完する提供元。
 * openBD がこれらをほとんど持たないため、別系統で補う。
 */
interface MetadataEnrichmentSource {
    suspend fun lookup(isbn13: String): BookMetadata?
}

/**
 * Google Books API。
 *
 * APIキー無しでも呼べる形になっているが、その場合は Google の共有匿名プロジェクトの
 * 割り当てを使うことになり、実測で既に枯渇していて利用できない
 * (Quota exceeded ... consumer 'project_number:624717413613')。
 * 実運用では必ず [apiKey] を指定すること。
 */
class GoogleBooksApi(
    private val http: HttpFetcher,
    private val apiKey: String? = null,
) : MetadataEnrichmentSource {

    override suspend fun lookup(isbn13: String): BookMetadata? {
        val url = buildString {
            append(BASE_URL)
            append("?q=isbn:").append(isbn13)
            append("&country=JP")
            apiKey?.takeIf { it.isNotBlank() }?.let { append("&key=").append(it) }
        }
        val body = http.getString(url) ?: return null
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val volume = (root["items"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val info = volume["volumeInfo"] as? JsonObject ?: return null

        val title = info.str("title")?.takeIf { it.isNotBlank() } ?: return null
        return BookMetadata(
            isbn13 = isbn13,
            title = title,
            subtitle = info.str("subtitle")?.takeIf { it.isNotBlank() },
            authors = (info["authors"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            publisher = info.str("publisher")?.takeIf { it.isNotBlank() },
            publishedDate = info.str("publishedDate")?.takeIf { it.isNotBlank() },
            coverUrl = coverUrl(info),
            pageCount = (info["pageCount"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
            description = info.str("description")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * imageLinks の URL は http:// で返り、`edge=curl` で角折れ加工が入ることがある。
     * https に直し、加工指定を落として素の表紙を得る。
     */
    private fun coverUrl(info: JsonObject): String? {
        val links = info["imageLinks"] as? JsonObject ?: return null
        val raw = links.str("thumbnail") ?: links.str("smallThumbnail") ?: return null
        return raw.replace("http://", "https://").replace("&edge=curl", "")
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { jsonPrimitive.content }.getOrNull()

    private companion object {
        const val BASE_URL = "https://www.googleapis.com/books/v1/volumes"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
