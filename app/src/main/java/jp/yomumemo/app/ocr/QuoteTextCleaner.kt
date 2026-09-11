package jp.yomumemo.app.ocr

/**
 * OCR が返した生のテキストを、引用として貼れる形に整える。
 *
 * 本のページを撮ると、組版の都合で1行ごとに改行が入る。そのまま引用欄に入れると
 * 元の紙面の折り返し位置で改行された、読みにくい塊になる。
 * 日本語は行末に空白を置かずに続くため、単純に改行を消すのが自然に近い。
 * 一方で段落の区切りは残したい。
 */
object QuoteTextCleaner {

    /** 行末が句点や閉じ括弧なら、そこで文が切れているとみなす。 */
    private val SENTENCE_END = setOf('。', '！', '？', '」', '』', '）', '.', '!', '?')

    /**
     * 読み取った文を既存の引用に足す。
     *
     * 上書きではなく追記にしている。撮り直しや複数ページの取り込みで
     * 前の結果が消えると、書き写した手間が無駄になるため。
     */
    fun append(existing: String, addition: String): String {
        val cleaned = addition.trim()
        if (cleaned.isEmpty()) return existing
        if (existing.isBlank()) return cleaned
        return existing.trimEnd() + System.lineSeparator() + cleaned
    }

    fun clean(raw: String): String {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""

        val builder = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            builder.append(line)
            if (index == lines.lastIndex) break

            val endsSentence = line.lastOrNull() in SENTENCE_END
            val currentIsAscii = line.last().code < 128
            val nextIsAscii = lines[index + 1].first().code < 128

            when {
                // 文が終わっていれば段落の区切りとみなして改行を残す
                endsSentence -> builder.append("\n")
                // 欧文どうしの折り返しは空白で繋ぐ (単語が繋がってしまうのを防ぐ)
                currentIsAscii && nextIsAscii -> builder.append(" ")
                // 和文の折り返しはそのまま繋ぐ
                else -> Unit
            }
        }
        return builder.toString().trim()
    }
}
