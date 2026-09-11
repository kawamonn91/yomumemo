package jp.yomumemo.app.util

/**
 * 書誌データの人名を表示用に整える。
 *
 * openBD / ONIX の人名は図書館目録の慣習に従っており、そのままでは読みづらい:
 *   "角, 征典"              → "角征典"
 *   "夏目, 漱石, 1867-1916" → "夏目漱石"      (生没年が付くことがある)
 *   "Boswell, Dustin"       → "Dustin Boswell" (欧文は姓名が逆転している)
 */
object PersonName {

    /** "1867-1916" や "1867-" のような生没年。 */
    private val YEAR_RANGE = Regex("""^\d{3,4}\s*-\s*\d{0,4}$""")

    private fun isCjk(text: String): Boolean = text.any { ch ->
        val block = Character.UnicodeBlock.of(ch)
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
    }

    fun format(raw: String): String {
        val parts = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !YEAR_RANGE.matches(it) }

        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0]
            // 和名は「姓名」と続けて書く
            isCjk(parts[0]) -> parts.joinToString("")
            // 欧文は「名 姓」に戻す
            else -> (parts.drop(1) + parts[0]).joinToString(" ")
        }
    }

    /**
     * openBD の summary.author は複数人が1つの文字列に詰め込まれている:
     *   "Boswell,Dustin Foucher,Trevor 角,征典"
     * 空白区切りで人を分け、それぞれを [format] にかける。
     * ONIX の Contributor が取れる場合はそちらを使うべきで、これは最後の手段。
     */
    fun splitSummaryAuthors(raw: String): List<String> =
        raw.split(" ", "　")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::format)
            .filter { it.isNotEmpty() }
}
