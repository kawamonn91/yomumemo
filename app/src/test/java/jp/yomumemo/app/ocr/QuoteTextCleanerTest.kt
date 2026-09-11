package jp.yomumemo.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 本のページを撮ると、組版の折り返し位置で1行ずつ改行が入る。
 * そのまま貼ると紙面の形をなぞった読みにくい塊になるので、整形の規則を固める。
 */
class QuoteTextCleanerTest {

    @Test
    fun `和文の折り返しは改行を取り除いて繋ぐ`() {
        val raw = "変数名は短すぎては\nいけない"
        assertEquals("変数名は短すぎてはいけない", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `句点で終わる行は段落の区切りとして改行を残す`() {
        val raw = "一つ目の文です。\n二つ目の文です。"
        assertEquals("一つ目の文です。\n二つ目の文です。", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `閉じ括弧で終わる行も区切りとして扱う`() {
        val raw = "彼は「そうだ」と言った」\n次の段落"
        assertEquals("彼は「そうだ」と言った」\n次の段落", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `欧文の折り返しは空白で繋いで単語をくっつけない`() {
        val raw = "readable\ncode"
        assertEquals("readable code", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `和文と欧文が混ざる折り返しは繋げる`() {
        val raw = "これは\nreadable なコード"
        assertEquals("これはreadable なコード", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `前後の空白と空行を落とす`() {
        val raw = "\n\n  変数名は  \n\n  短すぎてはいけない  \n\n"
        assertEquals("変数名は短すぎてはいけない", QuoteTextCleaner.clean(raw))
    }

    @Test
    fun `空文字は空文字のまま`() {
        assertEquals("", QuoteTextCleaner.clean(""))
        assertEquals("", QuoteTextCleaner.clean("\n\n  \n"))
    }

    @Test
    fun `1行だけならそのまま返す`() {
        assertEquals("一行だけ", QuoteTextCleaner.clean("  一行だけ  "))
    }

    // --- 追記 ---
    // 撮り直しや複数ページの取り込みで前の結果が消えると、書き写した手間が無駄になる

    @Test
    fun `引用が空なら読み取った文をそのまま入れる`() {
        assertEquals("読み取った文", QuoteTextCleaner.append("", "読み取った文"))
        assertEquals("読み取った文", QuoteTextCleaner.append("   ", "読み取った文"))
    }

    @Test
    fun `既にある引用は消さずに追記する`() {
        val result = QuoteTextCleaner.append("一枚目の引用", "二枚目の引用")
        assertEquals("一枚目の引用" + System.lineSeparator() + "二枚目の引用", result)
    }

    @Test
    fun `読み取れなかったときは既存の引用を壊さない`() {
        assertEquals("既存の引用", QuoteTextCleaner.append("既存の引用", ""))
        assertEquals("既存の引用", QuoteTextCleaner.append("既存の引用", "   "))
    }

    @Test
    fun `追記しても余分な空行が増えない`() {
        val result = QuoteTextCleaner.append("既存の引用\n\n", "追加分")
        assertEquals("既存の引用" + System.lineSeparator() + "追加分", result)
    }

    @Test
    fun `複数の段落と折り返しが混ざっても崩れない`() {
        val raw = """
            設計とは何かを
            考える。
            それは選択の
            連続である。
        """.trimIndent()
        assertEquals("設計とは何かを考える。\nそれは選択の連続である。", QuoteTextCleaner.clean(raw))
    }
}
