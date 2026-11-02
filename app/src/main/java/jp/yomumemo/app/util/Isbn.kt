package jp.yomumemo.app.util

/**
 * ISBN / EAN-13 のユーティリティ。
 *
 * 日本の書籍には2段のバーコードが印刷されている:
 *  - 上段: ISBN を表す EAN-13 (978/979 で始まる)  ← 書誌検索に使うのはこちら
 *  - 下段: 「日本図書コード」の価格情報 (192 で始まる)
 * 下段を読んでしまうケースが非常に多いため、両者を明確に区別して扱う。
 */
object Isbn {

    /** 日本図書コード下段(価格コード)の接頭辞。書誌検索には使えない。 */
    private const val JAPANESE_PRICE_CODE_PREFIX = "192"

    /** ハイフン・空白・全角文字を除去し、大文字に揃える。 */
    fun normalize(raw: String): String =
        raw.trim()
            .replace('－', '-')
            .filter { !it.isWhitespace() && it != '-' }
            .uppercase()

    /** 日本図書コードの下段(価格バーコード)か。 */
    fun isJapanesePriceCode(raw: String): Boolean {
        val n = normalize(raw)
        return n.length == 13 && n.all(Char::isDigit) && n.startsWith(JAPANESE_PRICE_CODE_PREFIX)
    }

    /** 書籍を表す有効な ISBN-13 か (978/979 始まり かつ チェックディジット一致)。 */
    fun isValidIsbn13(raw: String): Boolean {
        val n = normalize(raw)
        if (n.length != 13 || !n.all(Char::isDigit)) return false
        if (!n.startsWith("978") && !n.startsWith("979")) return false
        return ean13CheckDigit(n.substring(0, 12)) == n[12].digitToInt()
    }

    /** 有効な ISBN-10 か (チェックディジットは 0-9 または X)。 */
    fun isValidIsbn10(raw: String): Boolean {
        val n = normalize(raw)
        if (n.length != 10) return false
        if (!n.take(9).all(Char::isDigit)) return false
        val last = n[9]
        if (!last.isDigit() && last != 'X') return false
        return isbn10CheckChar(n.take(9)) == last
    }

    /**
     * 入力を ISBN-13 に正規化する。ISBN-10 は 978 プレフィックスを付けて変換する。
     * 書籍の ISBN として解釈できない場合は null。
     */
    fun toIsbn13(raw: String): String? {
        val n = normalize(raw)
        return when {
            isValidIsbn13(n) -> n
            isValidIsbn10(n) -> {
                val body = "978" + n.take(9)
                body + ean13CheckDigit(body)
            }
            else -> null
        }
    }

    /** 先頭12桁から EAN-13 のチェックディジットを算出する。 */
    fun ean13CheckDigit(first12: String): Int {
        require(first12.length == 12 && first12.all(Char::isDigit)) {
            "EAN-13 のチェックディジット計算には数字12桁が必要です: $first12"
        }
        val sum = first12.foldIndexed(0) { index, acc, c ->
            acc + c.digitToInt() * if (index % 2 == 0) 1 else 3
        }
        return (10 - sum % 10) % 10
    }

    /** 先頭9桁から ISBN-10 のチェック文字を算出する ('0'-'9' または 'X')。 */
    fun isbn10CheckChar(first9: String): Char {
        require(first9.length == 9 && first9.all(Char::isDigit)) {
            "ISBN-10 のチェック文字計算には数字9桁が必要です: $first9"
        }
        val sum = first9.foldIndexed(0) { index, acc, c ->
            acc + c.digitToInt() * (10 - index)
        }
        return when (val check = (11 - sum % 11) % 11) {
            10 -> 'X'
            else -> '0' + check
        }
    }

    /** 表示用にハイフンを入れる (簡易: 978-4-87311-565-8 形式の桁区切りではなく先頭3桁のみ区切る)。 */
    fun formatForDisplay(isbn13: String): String {
        val n = normalize(isbn13)
        return if (n.length == 13) "${n.take(3)}-${n.substring(3)}" else n
    }
}

/** スキャン結果の解釈。UI はこれを見て次の挙動を決める。 */
sealed interface ScanOutcome {
    data class Isbn(val isbn13: String) : ScanOutcome

    /** 下段の価格バーコードを読んでしまった場合。上段を読むよう促す。 */
    data object JapanesePriceCode : ScanOutcome

    /** 書籍のバーコードではない (商品 EAN, QR など)。 */
    data object NotABook : ScanOutcome
}

/** スキャンした生の文字列を解釈する。 */
fun interpretScannedCode(raw: String): ScanOutcome = when {
    Isbn.isJapanesePriceCode(raw) -> ScanOutcome.JapanesePriceCode
    else -> Isbn.toIsbn13(raw)?.let(ScanOutcome::Isbn) ?: ScanOutcome.NotABook
}
