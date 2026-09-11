package jp.yomumemo.app.data.remote

import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.util.PersonName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * openBD (https://openbd.jp) の書誌データを読む。
 *
 * APIキー不要・利用制限なしで、和書の書誌情報は極めて正確。
 * 一方で実測(無作為1000件)では表紙画像 約6%・ページ数 約10%・内容紹介 約16% しか
 * 持たないため、表紙などの補完は別の提供元に任せる前提で使う。
 *
 * レスポンスは ONIX 由来で型が揺れる(要素が単体だったり配列だったり、空文字だったり)ため、
 * データクラスへの厳密なデシリアライズはせず JsonElement を防御的に辿る。
 */
class OpenBdApi(private val http: HttpFetcher) {

    suspend fun lookup(isbn13: String): BookMetadata? {
        val body = http.getString("$BASE_URL?isbn=$isbn13") ?: return null
        val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        val record = array.firstOrNull() as? JsonObject ?: return null
        return parseRecord(isbn13, record)
    }

    internal fun parseRecord(isbn13: String, record: JsonObject): BookMetadata? {
        val summary = record["summary"] as? JsonObject ?: return null
        val rawTitle = summary.str("title").orEmpty()
        if (rawTitle.isBlank()) return null

        // openBD のタイトルは "本題 : 副題" の形で副題を含むことがある
        val (title, subtitle) = rawTitle.split(" : ", limit = 2)
            .let { it[0].trim() to it.getOrNull(1)?.trim() }

        val onix = record["onix"] as? JsonObject
        val descriptive = onix?.get("DescriptiveDetail") as? JsonObject
        val collateral = onix?.get("CollateralDetail") as? JsonObject

        return BookMetadata(
            isbn13 = summary.str("isbn")?.takeIf { it.isNotBlank() } ?: isbn13,
            title = title,
            subtitle = subtitle,
            authors = authors(descriptive, summary),
            publisher = summary.str("publisher")?.takeIf { it.isNotBlank() },
            publishedDate = summary.str("pubdate")?.let(::normalizePubDate),
            coverUrl = summary.str("cover")?.takeIf { it.isNotBlank() }
                ?: coverFromOnix(collateral),
            pageCount = pageCount(descriptive),
            description = description(collateral),
            series = summary.str("series")?.takeIf { it.isNotBlank() },
        )
    }

    /** ONIX の Contributor を優先し、無ければ summary.author を分解する。 */
    private fun authors(descriptive: JsonObject?, summary: JsonObject): List<String> {
        val fromOnix = (descriptive?.get("Contributor") as? JsonArray)
            ?.mapNotNull { entry ->
                val person = (entry as? JsonObject)?.get("PersonName") as? JsonObject
                person.str("content")?.takeIf { it.isNotBlank() }?.let(PersonName::format)
            }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (fromOnix.isNotEmpty()) return fromOnix

        return summary.str("author")
            ?.takeIf { it.isNotBlank() }
            ?.let(PersonName::splitSummaryAuthors)
            .orEmpty()
    }

    /** Extent は配列。ページ数を表す数値を持つ最初の要素を拾う。 */
    private fun pageCount(descriptive: JsonObject?): Int? =
        (descriptive?.get("Extent") as? JsonArray)
            ?.asSequence()
            ?.mapNotNull { (it as? JsonObject).str("ExtentValue")?.toIntOrNull() }
            ?.firstOrNull { it > 0 }

    /** TextContent の Text が内容紹介。複数ある場合は最も長いものを採る。 */
    private fun description(collateral: JsonObject?): String? =
        (collateral?.get("TextContent") as? JsonArray)
            ?.asSequence()
            ?.mapNotNull { (it as? JsonObject).str("Text") }
            ?.filter { it.isNotBlank() }
            ?.maxByOrNull { it.length }
            ?.trim()

    private fun coverFromOnix(collateral: JsonObject?): String? =
        (collateral?.get("SupportingResource") as? JsonArray)
            ?.asSequence()
            ?.mapNotNull { resource ->
                ((resource as? JsonObject)?.get("ResourceVersion") as? JsonArray)
                    ?.asSequence()
                    ?.mapNotNull { (it as? JsonObject).str("ResourceLink") }
                    ?.firstOrNull { it.isNotBlank() }
            }
            ?.firstOrNull()

    /** "201206" → "2012-06" / "20120601" → "2012-06-01"。 */
    private fun normalizePubDate(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length >= 8 -> "${digits.take(4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            digits.length >= 6 -> "${digits.take(4)}-${digits.substring(4, 6)}"
            digits.length >= 4 -> digits.take(4)
            else -> null
        }
    }

    private companion object {
        const val BASE_URL = "https://api.openbd.jp/v1/get"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** JsonObject から文字列を安全に取り出す。型が違えば null。 */
internal fun JsonObject?.str(key: String): String? {
    val element: JsonElement = this?.get(key) ?: return null
    return (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        ?: runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
}
