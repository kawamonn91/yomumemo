package jp.yomumemo.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {

    // 『リーダブルコード』(オーム社) の実在する ISBN
    private val readableCode13 = "9784873115658"
    private val readableCode10 = "4873115655"

    @Test
    fun `有効な ISBN-13 を受理する`() {
        assertTrue(Isbn.isValidIsbn13(readableCode13))
    }

    @Test
    fun `チェックディジットが誤った ISBN-13 を拒否する`() {
        assertFalse(Isbn.isValidIsbn13("9784873115659"))
    }

    @Test
    fun `978や979で始まらない EAN-13 は書籍とみなさない`() {
        // 一般商品の EAN。チェックディジット自体は正しくても書籍ではない。
        val body = "490177730044"
        val ean = body + Isbn.ean13CheckDigit(body)
        assertFalse(Isbn.isValidIsbn13(ean))
    }

    @Test
    fun `ハイフンや空白を含む入力を正規化して受理する`() {
        assertTrue(Isbn.isValidIsbn13("978-4-87311-565-8"))
        assertTrue(Isbn.isValidIsbn13(" 978 4 87311 565 8 "))
    }

    @Test
    fun `有効な ISBN-10 を受理する`() {
        assertTrue(Isbn.isValidIsbn10(readableCode10))
    }

    @Test
    fun `チェック文字が X の ISBN-10 を受理する`() {
        // 先頭9桁から算出した正しいチェック文字が X になるケース
        val first9 = "043942089"
        assertEquals('X', Isbn.isbn10CheckChar(first9))
        assertTrue(Isbn.isValidIsbn10(first9 + "X"))
    }

    @Test
    fun `ISBN-10 を ISBN-13 に変換する`() {
        assertEquals(readableCode13, Isbn.toIsbn13(readableCode10))
    }

    @Test
    fun `ISBN-13 はそのまま返す`() {
        assertEquals(readableCode13, Isbn.toIsbn13("978-4-87311-565-8"))
    }

    @Test
    fun `書籍として解釈できない入力は null を返す`() {
        assertNull(Isbn.toIsbn13("hello"))
        assertNull(Isbn.toIsbn13("12345"))
        assertNull(Isbn.toIsbn13(""))
    }

    @Test
    fun `EAN-13 のチェックディジットを算出する`() {
        assertEquals(8, Isbn.ean13CheckDigit("978487311565"))
    }

    // --- 日本の書籍バーコードは上下2段。下段(価格コード)を読んでしまう誤操作が多い ---

    @Test
    fun `下段の価格バーコードを価格コードとして検出する`() {
        // 192 で始まる13桁 = 日本図書コード下段
        assertTrue(Isbn.isJapanesePriceCode("1921234567890"))
    }

    @Test
    fun `上段の ISBN は価格コードと誤判定しない`() {
        assertFalse(Isbn.isJapanesePriceCode(readableCode13))
    }

    @Test
    fun `スキャン結果の解釈 - 上段は ISBN として扱う`() {
        val outcome = interpretScannedCode("978-4-87311-565-8")
        assertEquals(ScanOutcome.Isbn(readableCode13), outcome)
    }

    @Test
    fun `スキャン結果の解釈 - 下段は価格コードとして案内する`() {
        assertEquals(ScanOutcome.JapanesePriceCode, interpretScannedCode("1921234567890"))
    }

    @Test
    fun `スキャン結果の解釈 - 書籍以外は NotABook`() {
        assertEquals(ScanOutcome.NotABook, interpretScannedCode("4901777300446"))
    }
}
