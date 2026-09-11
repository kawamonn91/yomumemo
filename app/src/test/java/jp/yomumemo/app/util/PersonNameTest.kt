package jp.yomumemo.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameTest {

    @Test
    fun `和名は姓と名を連結する`() {
        assertEquals("角征典", PersonName.format("角, 征典"))
    }

    @Test
    fun `欧文は名を先に戻す`() {
        assertEquals("Dustin Boswell", PersonName.format("Boswell, Dustin"))
    }

    @Test
    fun `生没年を取り除く`() {
        assertEquals("夏目漱石", PersonName.format("夏目, 漱石, 1867-1916"))
        assertEquals("存命作家", PersonName.format("存命, 作家, 1980-"))
    }

    @Test
    fun `区切りが無い名前はそのまま返す`() {
        assertEquals("よしもとばなな", PersonName.format("よしもとばなな"))
    }

    @Test
    fun `空文字は空文字のまま`() {
        assertEquals("", PersonName.format(""))
        assertEquals("", PersonName.format(" , "))
    }

    @Test
    fun `summary_author の複数人をまとめて分解する`() {
        val raw = "Boswell,Dustin Foucher,Trevor 角,征典"
        assertEquals(
            listOf("Dustin Boswell", "Trevor Foucher", "角征典"),
            PersonName.splitSummaryAuthors(raw),
        )
    }

    @Test
    fun `全角スペース区切りも人の境界として扱う`() {
        assertEquals(
            listOf("夏目漱石", "森鴎外"),
            PersonName.splitSummaryAuthors("夏目,漱石　森,鴎外"),
        )
    }
}
