package jp.yomumemo.app.domain

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadingStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 9, 11)

    private fun epoch(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun book(
        id: String,
        status: ReadingStatus = ReadingStatus.WANT,
        pageCount: Int? = null,
        currentPage: Int = 0,
        finishedAt: LocalDate? = null,
        deleted: Boolean = false,
    ) = BookEntity(
        id = id,
        title = "本" + id,
        status = status,
        pageCount = pageCount,
        currentPage = currentPage,
        finishedAt = finishedAt?.let(::epoch),
        addedAt = 0L,
        updatedAt = 0L,
        deletedAt = if (deleted) 1L else null,
    )

    private fun note(id: String, createdAt: LocalDate, deleted: Boolean = false) = NoteEntity(
        id = id,
        bookId = "b1",
        type = NoteType.THOUGHT,
        createdAt = epoch(createdAt),
        updatedAt = 0L,
        deletedAt = if (deleted) 1L else null,
    )

    private fun calc(books: List<BookEntity>, notes: List<NoteEntity> = emptyList()) =
        ReadingStatsCalculator.calculate(books, notes, zone, today)

    @Test
    fun `状態ごとの冊数を数える`() {
        val stats = calc(
            listOf(
                book("1", ReadingStatus.READING),
                book("2", ReadingStatus.DONE),
                book("3", ReadingStatus.WANT),
                book("4", ReadingStatus.WANT),
            ),
        )
        assertEquals(4, stats.totalBooks)
        assertEquals(1, stats.reading)
        assertEquals(1, stats.finished)
        assertEquals(2, stats.want)
    }

    @Test
    fun `論理削除した本とメモは数えない`() {
        val stats = calc(
            listOf(book("1"), book("2", deleted = true)),
            listOf(note("n1", today), note("n2", today, deleted = true)),
        )
        assertEquals(1, stats.totalBooks)
        assertEquals(1, stats.totalNotes)
    }

    @Test
    fun `読了した本はページ数の全体を数える`() {
        val stats = calc(listOf(book("1", ReadingStatus.DONE, pageCount = 300)))
        assertEquals(300, stats.pagesRead)
    }

    @Test
    fun `読書中の本は現在のページまでを数える`() {
        val stats = calc(listOf(book("1", ReadingStatus.READING, pageCount = 300, currentPage = 120)))
        assertEquals(120, stats.pagesRead)
    }

    @Test
    fun `ページ数が不明な本は0として扱う`() {
        // openBD はページ数を1割程度しか持たないため、これは普通に起きる
        val stats = calc(listOf(book("1", ReadingStatus.DONE, pageCount = null)))
        assertEquals(0, stats.pagesRead)
    }

    @Test
    fun `今年と今月の読了数を分けて数える`() {
        val stats = calc(
            listOf(
                book("1", ReadingStatus.DONE, finishedAt = LocalDate.of(2026, 9, 1)),
                book("2", ReadingStatus.DONE, finishedAt = LocalDate.of(2026, 3, 1)),
                book("3", ReadingStatus.DONE, finishedAt = LocalDate.of(2025, 9, 1)),
            ),
        )
        assertEquals(2, stats.finishedThisYear)
        assertEquals(1, stats.finishedThisMonth)
    }

    @Test
    fun `今日から遡って連続した日数を数える`() {
        val stats = calc(
            listOf(book("1")),
            listOf(
                note("n1", today),
                note("n2", today.minusDays(1)),
                note("n3", today.minusDays(2)),
            ),
        )
        assertEquals(3, stats.noteStreakDays)
    }

    @Test
    fun `今日まだ書いていなくても昨日書いていれば途切れない`() {
        // 今日の分を書く前に0日と出ると、続けている実感を損なう
        val stats = calc(
            listOf(book("1")),
            listOf(note("n1", today.minusDays(1)), note("n2", today.minusDays(2))),
        )
        assertEquals(2, stats.noteStreakDays)
    }

    @Test
    fun `2日以上空いていれば継続は0`() {
        val stats = calc(listOf(book("1")), listOf(note("n1", today.minusDays(2))))
        assertEquals(0, stats.noteStreakDays)
    }

    @Test
    fun `同じ日に複数書いても1日として数える`() {
        val stats = calc(
            listOf(book("1")),
            listOf(note("n1", today), note("n2", today), note("n3", today)),
        )
        assertEquals(1, stats.noteStreakDays)
    }

    @Test
    fun `メモが無ければ継続は0`() {
        assertEquals(0, calc(listOf(book("1"))).noteStreakDays)
    }

    @Test
    fun `本が無いときに0除算しない`() {
        val stats = calc(emptyList(), emptyList())
        assertEquals(0.0, stats.notesPerBook, 0.001)
        assertEquals(0, stats.totalBooks)
    }
}
