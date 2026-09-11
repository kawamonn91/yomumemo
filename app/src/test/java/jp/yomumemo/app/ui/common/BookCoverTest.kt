package jp.yomumemo.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表紙のプレースホルダ色の選び方。
 *
 * ここは一度落ちている。hashCode は負になりうるうえ、Kotlin の % は
 * 被除数の符号を引き継ぐため、素直に剰余を取ると負の添字になって
 * ArrayIndexOutOfBoundsException で本棚ごと表示できなくなる。
 * openBD は表紙を約6%しか持たず、ほとんどの本がこの経路を通るので、
 * ここが落ちるとアプリが使い物にならない。
 */
class BookCoverTest {

    private val paletteSize = 6

    @Test
    fun `ハッシュが負になる文字列でも添字が範囲内に収まる`() {
        // hashCode が負になる実在しそうなタイトルを含めて広く試す
        val titles = listOf(
            "リーダブルコード",
            "吾輩は猫である",
            "積んである本",
            "zzzzzzzzzzzzzzzzzzzz",
            "デザインパターン入門",
            "ソフトウェア設計の原則と実践",
            "a",
            "",
        )
        for (title in titles) {
            val index = placeholderColorIndex(title, paletteSize)
            assertTrue(
                "添字が範囲外: title=" + title + " index=" + index,
                index in 0 until paletteSize,
            )
        }
    }

    @Test
    fun `総当たりで負の添字が出ないことを確かめる`() {
        // 実際のタイトルは無数にあるので、ハッシュの分布を広く突く
        for (i in 0 until 20000) {
            val index = placeholderColorIndex("タイトル" + i, paletteSize)
            assertTrue("添字が範囲外: i=" + i + " index=" + index, index in 0 until paletteSize)
        }
    }

    @Test
    fun `同じタイトルはいつも同じ色になる`() {
        // 背表紙のように識別の手がかりになるので、起動のたびに変わっては困る
        val first = placeholderColorIndex("リーダブルコード", paletteSize)
        val second = placeholderColorIndex("リーダブルコード", paletteSize)
        assertEquals(first, second)
    }

    @Test
    fun `色数が1でも壊れない`() {
        assertEquals(0, placeholderColorIndex("なんでも", 1))
    }
}
