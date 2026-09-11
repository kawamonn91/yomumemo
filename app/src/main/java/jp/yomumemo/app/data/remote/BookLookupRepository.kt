package jp.yomumemo.app.data.remote

import jp.yomumemo.app.domain.BookMetadata
import jp.yomumemo.app.domain.LookupResult
import jp.yomumemo.app.util.Isbn

/**
 * ISBN から書誌情報を引く。
 *
 * 方針:
 *  1. openBD を第一の情報源にする。和書のタイトル・著者・出版社が最も正確で、
 *     APIキー不要・利用制限なしのため全ユーザーで確実に動く。
 *  2. openBD が持たない表紙画像・ページ数・内容紹介だけを [enrichment] で補う。
 *     補完元が未設定でもアプリは動く(表紙はプレースホルダ表示になる)。
 *  3. openBD に該当が無ければ補完元だけで組み立てる。それも空なら NotFound を返し、
 *     UI は手動入力へ誘導する。実在するのに両方に無い本は珍しくない。
 */
class BookLookupRepository(
    private val openBd: OpenBdApi,
    private val enrichment: MetadataEnrichmentSource? = null,
) {

    suspend fun lookup(rawIsbn: String): LookupResult {
        val isbn13 = Isbn.toIsbn13(rawIsbn)
            ?: return LookupResult.NotFound

        return runCatching {
            val primary = openBd.lookup(isbn13)

            // 補完元を呼ぶのは、必要な項目が欠けているときだけにして通信と割り当てを節約する
            val needsEnrichment = primary == null || primary.isMissingRichFields()
            val secondary = if (needsEnrichment) enrichment?.lookup(isbn13) else null

            val merged = when {
                primary != null && secondary != null -> primary.fillMissingFrom(secondary)
                primary != null -> primary
                secondary != null -> secondary
                else -> null
            }

            merged?.let(LookupResult::Found) ?: LookupResult.NotFound
        }.getOrElse { LookupResult.Failed(it) }
    }

    private fun BookMetadata.isMissingRichFields(): Boolean =
        coverUrl.isNullOrBlank() || pageCount == null || description.isNullOrBlank()
}
