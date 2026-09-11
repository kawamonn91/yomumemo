package jp.yomumemo.app.domain

import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.ReadingStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReadingStats(
    val totalBooks: Int = 0,
    val reading: Int = 0,
    val finished: Int = 0,
    val want: Int = 0,
    val totalNotes: Int = 0,
    val pagesRead: Int = 0,
    val finishedThisYear: Int = 0,
    val finishedThisMonth: Int = 0,
    /** メモを書いた日が連続している日数。 */
    val noteStreakDays: Int = 0,
    /** 1冊あたりのメモ数。 */
    val notesPerBook: Double = 0.0,
)

/**
 * 読書統計の算出。
 *
 * 時計と時間帯を引数で受け取る純粋な処理にしてある。
 * 継続日数のような日付にまたがる判定は、実行日に依存するとテストできないため。
 */
object ReadingStatsCalculator {

    fun calculate(
        books: List<BookEntity>,
        notes: List<NoteEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): ReadingStats {
        val active = books.filter { it.deletedAt == null }
        val activeNotes = notes.filter { it.deletedAt == null }
        val finishedBooks = active.filter { it.status == ReadingStatus.DONE }

        return ReadingStats(
            totalBooks = active.size,
            reading = active.count { it.status == ReadingStatus.READING },
            finished = finishedBooks.size,
            want = active.count { it.status == ReadingStatus.WANT },
            totalNotes = activeNotes.size,
            pagesRead = pagesRead(active),
            finishedThisYear = finishedBooks.count { book ->
                book.finishedAt?.toLocalDate(zone)?.year == today.year
            },
            finishedThisMonth = finishedBooks.count { book ->
                val date = book.finishedAt?.toLocalDate(zone)
                date != null && date.year == today.year && date.month == today.month
            },
            noteStreakDays = noteStreak(activeNotes, zone, today),
            notesPerBook = if (active.isEmpty()) 0.0 else activeNotes.size.toDouble() / active.size,
        )
    }

    /**
     * 読んだページ数。
     * 読了した本はページ数の全体を、読書中の本は現在のページまでを数える。
     * ページ数が分からない本は 0 として扱う。openBD はページ数を1割程度しか
     * 持たないため、これは例外ではなく普通に起きる。
     */
    private fun pagesRead(books: List<BookEntity>): Int = books.sumOf { book ->
        when (book.status) {
            ReadingStatus.DONE -> book.pageCount ?: book.currentPage
            else -> book.currentPage
        }
    }

    /**
     * メモを書いた日の連続日数。
     *
     * 今日まだ書いていなくても、昨日書いていれば連続は途切れていないとみなす。
     * 今日の分を書く前に「0日」と表示されると、続けている実感を損なうため。
     */
    private fun noteStreak(notes: List<NoteEntity>, zone: ZoneId, today: LocalDate): Int {
        if (notes.isEmpty()) return 0
        val days = notes.map { it.createdAt.toLocalDate(zone) }.toSet()

        val start = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        var cursor = start
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
