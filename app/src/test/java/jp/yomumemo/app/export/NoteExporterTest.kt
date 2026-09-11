package jp.yomumemo.app.export

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 書き出したファイルはユーザーの資産で、他のツールで開かれる。
 * 壊れた CSV を吐くと読み込めなくなるため、エスケープは厳密に確かめる。
 */
class NoteExporterTest {

    private val book = BookEntity(
        id = "b1",
        isbn13 = "9784873115658",
        title = "リーダブルコード",
        authors = listOf("Dustin Boswell", "角征典"),
        publisher = "オーム社",
        publishedDate = "2012-06",
        status = ReadingStatus.DONE,
        addedAt = 0L,
        updatedAt = 0L,
    )

    private fun note(
        id: String = "n1",
        quote: String = "",
        comment: String = "",
        page: Int? = 42,
        type: NoteType = NoteType.QUOTE,
    ) = NoteEntity(
        id = id,
        bookId = "b1",
        type = type,
        page = page,
        quote = quote,
        comment = comment,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // --- CSV のエスケープ ---

    @Test
    fun `特殊文字が無い値はそのまま出す`() {
        assertEquals("ふつうの文字列", NoteExporter.csvField("ふつうの文字列"))
    }

    @Test
    fun `カンマを含む値は引用符で囲む`() {
        assertEquals("\"a,b\"", NoteExporter.csvField("a,b"))
    }

    @Test
    fun `引用符は2つ重ねて囲む`() {
        assertEquals("\"彼は\"\"そう\"\"と言った\"", NoteExporter.csvField("彼は\"そう\"と言った"))
    }

    @Test
    fun `改行を含む値は引用符で囲む`() {
        assertEquals("\"1行目\n2行目\"", NoteExporter.csvField("1行目\n2行目"))
    }

    @Test
    fun `改行とカンマと引用符が同時にあっても壊れない`() {
        val input = "引用の1行目,\n\"2行目\""
        val expected = "\"引用の1行目,\n\"\"2行目\"\"\""
        assertEquals(expected, NoteExporter.csvField(input))
    }

    @Test
    fun `CSV に BOM を付けて Excel で文字化けさせない`() {
        val csv = NoteExporter.export(listOf(BookWithNotes(book, emptyList())), ExportFormat.CSV)
        assertTrue("BOM が無いと Excel で日本語が化ける", csv.startsWith("\uFEFF"))
    }

    @Test
    fun `CSV の行数はメモの数と一致する`() {
        val csv = NoteExporter.export(
            listOf(BookWithNotes(book, listOf(note("n1"), note("n2"), note("n3")))),
            ExportFormat.CSV,
        )
        // ヘッダ1行 + メモ3行
        assertEquals(4, csv.trim().lines().count { it.isNotBlank() })
    }

    @Test
    fun `メモが無い本も1行として書き出す`() {
        val csv = NoteExporter.export(listOf(BookWithNotes(book, emptyList())), ExportFormat.CSV)
        assertTrue("本の情報が失われている", csv.contains("リーダブルコード"))
    }

    // --- Markdown ---

    @Test
    fun `Markdown に書名と著者と出版社を出す`() {
        val md = NoteExporter.export(listOf(BookWithNotes(book, emptyList())), ExportFormat.MARKDOWN)
        assertTrue(md.contains("## リーダブルコード"))
        assertTrue(md.contains("Dustin Boswell、角征典"))
        assertTrue(md.contains("オーム社"))
        assertTrue(md.contains("読了"))
    }

    @Test
    fun `複数行の引用は各行に引用記号を付ける`() {
        val md = NoteExporter.export(
            listOf(BookWithNotes(book, listOf(note(quote = "1行目\n2行目")))),
            ExportFormat.MARKDOWN,
        )
        assertTrue("1行目が引用になっていない", md.contains("> 1行目"))
        assertTrue("2行目が引用になっていない", md.contains("> 2行目"))
    }

    @Test
    fun `ページ番号を見出しに出す`() {
        val md = NoteExporter.export(
            listOf(BookWithNotes(book, listOf(note(page = 42)))),
            ExportFormat.MARKDOWN,
        )
        assertTrue(md.contains("p.42"))
    }

    @Test
    fun `ページ番号が無いメモでも見出しが壊れない`() {
        val md = NoteExporter.export(
            listOf(BookWithNotes(book, listOf(note(page = null, comment = "感想")))),
            ExportFormat.MARKDOWN,
        )
        assertTrue(md.contains("### 引用"))
        assertTrue(!md.contains("p.null"))
    }

    // --- Obsidian ---

    @Test
    fun `Obsidian はフロントマターと著者リンクを出す`() {
        val md = NoteExporter.export(listOf(BookWithNotes(book, emptyList())), ExportFormat.OBSIDIAN)
        assertTrue("フロントマターが無い", md.startsWith("---"))
        assertTrue("著者リンクが無い", md.contains("[[角征典]]"))
    }

    // --- ファイル名 ---

    @Test
    fun `ファイル名に日付と拡張子を付ける`() {
        val name = NoteExporter.fileName(ExportFormat.CSV, now = 0L)
        assertTrue(name.startsWith("yomumemo-"))
        assertTrue(name.endsWith(".csv"))
    }

    @Test
    fun `Obsidian の拡張子は md`() {
        assertTrue(NoteExporter.fileName(ExportFormat.OBSIDIAN).endsWith(".md"))
    }
}
