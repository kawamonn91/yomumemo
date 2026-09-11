package jp.yomumemo.app.domain

/**
 * 外部の書誌サービスから取得した1冊分の情報。
 *
 * 出典ごとに埋まるフィールドが違うため、すべて null 許容にしてある
 * (例: openBD は書誌情報は正確だが表紙画像をほとんど持たない)。
 */
data class BookMetadata(
    val isbn13: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val coverUrl: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    val series: String? = null,
) {
    /**
     * 不足しているフィールドだけを [other] で補う。上書きはしない。
     *
     * 書誌情報(タイトル・著者・出版社)は和書に強い openBD を優先し、
     * 表紙・ページ数・内容紹介だけを補完元から埋める、という使い方を想定している。
     */
    fun fillMissingFrom(other: BookMetadata): BookMetadata = copy(
        title = title.ifBlank { other.title },
        subtitle = subtitle ?: other.subtitle,
        authors = authors.ifEmpty { other.authors },
        publisher = publisher ?: other.publisher,
        publishedDate = publishedDate ?: other.publishedDate,
        coverUrl = coverUrl ?: other.coverUrl,
        pageCount = pageCount ?: other.pageCount,
        description = description ?: other.description,
        series = series ?: other.series,
    )
}

/** 書誌検索の結果。 */
sealed interface LookupResult {
    data class Found(val metadata: BookMetadata) : LookupResult

    /** どの提供元にも該当が無かった。UI は手動入力へ誘導する。 */
    data object NotFound : LookupResult

    /** 通信エラーなど。再試行の余地がある。 */
    data class Failed(val cause: Throwable) : LookupResult
}
