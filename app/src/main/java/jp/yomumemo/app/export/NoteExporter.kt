package jp.yomumemo.app.export

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 書き出し形式。 */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    MARKDOWN("Markdown", "md", "text/markdown"),
    CSV("CSV", "csv", "text/csv"),
    OBSIDIAN("Obsidian", "md", "text/markdown"),
}

/** 1冊分の本とそのメモ。 */
data class BookWithNotes(val book: BookEntity, val notes: List<NoteEntity>)

/**
 * 読書メモを外部の形式に書き出す。
 *
 * 文字列を組み立てるだけの純粋な処理にしてあるので、端末が無くても検証できる。
 * 出力はユーザーの資産なので、壊れた CSV を吐くと他のツールで開けなくなる。
 * 特に引用文には改行・カンマ・引用符が普通に含まれるため、
 * エスケープは RFC 4180 に従って厳密に行う。
 */
object NoteExporter {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    fun export(books: List<BookWithNotes>, format: ExportFormat): String = when (format) {
        ExportFormat.MARKDOWN -> toMarkdown(books)
        ExportFormat.CSV -> toCsv(books)
        ExportFormat.OBSIDIAN -> toObsidian(books)
    }

    fun fileName(format: ExportFormat, now: Long = System.currentTimeMillis()): String =
        "yomumemo-" + dateFormatter.format(Instant.ofEpochMilli(now)) + "." + format.extension

    // --- Markdown ---

    private fun toMarkdown(books: List<BookWithNotes>): String = buildString {
        appendLine("# 読書メモ")
        appendLine()
        for ((index, entry) in books.withIndex()) {
            if (index > 0) appendLine()
            appendBookMarkdown(entry, headingLevel = 2)
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendBookMarkdown(entry: BookWithNotes, headingLevel: Int) {
        val book = entry.book
        appendLine("#".repeat(headingLevel) + " " + book.title)
        book.subtitle?.takeIf { it.isNotBlank() }?.let { appendLine("*" + it + "*") }

        val meta = buildList {
            if (book.authors.isNotEmpty()) add(book.authors.joinToString("、"))
            book.publisher?.let(::add)
            book.publishedDate?.let(::add)
            add(book.status.label())
        }
        if (meta.isNotEmpty()) {
            appendLine()
            appendLine(meta.joinToString(" / "))
        }

        if (entry.notes.isEmpty()) {
            appendLine()
            appendLine("(メモはありません)")
            return
        }

        for (note in entry.notes) {
            appendLine()
            val head = buildList {
                add(note.type.label())
                note.page?.let { add("p." + it) }
            }.joinToString(" ")
            appendLine("#".repeat(headingLevel + 1) + " " + head)

            if (note.quote.isNotBlank()) {
                appendLine()
                // 引用は行ごとに > を付ける。複数行の引用が崩れないようにするため。
                note.quote.lines().forEach { appendLine("> " + it) }
            }
            if (note.comment.isNotBlank()) {
                appendLine()
                appendLine(note.comment)
            }
        }
    }

    // --- Obsidian ---

    /**
     * Obsidian 向け。YAML フロントマターを付け、著者を [[ ]] で繋いで
     * 著者ノートから辿れるようにする。
     */
    private fun toObsidian(books: List<BookWithNotes>): String = buildString {
        appendLine("---")
        appendLine("tags:")
        appendLine("  - 読書メモ")
        appendLine("created: " + dateFormatter.format(Instant.now()))
        appendLine("---")
        appendLine()
        for ((index, entry) in books.withIndex()) {
            if (index > 0) appendLine()
            val book = entry.book
            appendLine("## " + book.title)
            if (book.authors.isNotEmpty()) {
                appendLine()
                appendLine(book.authors.joinToString(" ") { "[[" + it + "]]" })
            }
            appendBookNotesObsidian(entry)
        }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendBookNotesObsidian(entry: BookWithNotes) {
        for (note in entry.notes) {
            appendLine()
            val head = buildList {
                add(note.type.label())
                note.page?.let { add("p." + it) }
            }.joinToString(" ")
            appendLine("### " + head)
            if (note.quote.isNotBlank()) {
                appendLine()
                note.quote.lines().forEach { appendLine("> " + it) }
            }
            if (note.comment.isNotBlank()) {
                appendLine()
                appendLine(note.comment)
            }
        }
    }

    // --- CSV ---

    private val CSV_HEADER = listOf(
        "書名", "著者", "出版社", "状態", "種別", "ページ", "引用", "自分の言葉", "作成日",
    )

    private fun toCsv(books: List<BookWithNotes>): String = buildString {
        // Excel が UTF-8 と判別できるよう BOM を付ける。
        // 付けないと日本語が文字化けし、書き出せていないように見える。
        append("\uFEFF")
        appendLine(CSV_HEADER.joinToString(",") { csvField(it) })

        for (entry in books) {
            val book = entry.book
            if (entry.notes.isEmpty()) {
                appendLine(
                    listOf(
                        book.title,
                        book.authors.joinToString("、"),
                        book.publisher.orEmpty(),
                        book.status.label(),
                        "", "", "", "",
                        dateFormatter.format(Instant.ofEpochMilli(book.addedAt)),
                    ).joinToString(",") { csvField(it) },
                )
                continue
            }
            for (note in entry.notes) {
                appendLine(
                    listOf(
                        book.title,
                        book.authors.joinToString("、"),
                        book.publisher.orEmpty(),
                        book.status.label(),
                        note.type.label(),
                        note.page?.toString().orEmpty(),
                        note.quote,
                        note.comment,
                        dateFormatter.format(Instant.ofEpochMilli(note.createdAt)),
                    ).joinToString(",") { csvField(it) },
                )
            }
        }
    }

    /**
     * RFC 4180 のエスケープ。
     * 引用符・カンマ・改行のいずれかを含む値は全体を引用符で囲み、
     * 値の中の引用符は2つ重ねる。引用文にはこれらが普通に含まれる。
     */
    internal fun csvField(value: String): String {
        val needsQuoting = value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun NoteType.label(): String = when (this) {
        NoteType.QUOTE -> "引用"
        NoteType.THOUGHT -> "感想"
        NoteType.QUESTION -> "疑問"
        NoteType.SUMMARY -> "要約"
    }

    private fun ReadingStatus.label(): String = when (this) {
        ReadingStatus.WANT -> "読みたい"
        ReadingStatus.READING -> "読書中"
        ReadingStatus.PAUSED -> "中断"
        ReadingStatus.DONE -> "読了"
    }
}
