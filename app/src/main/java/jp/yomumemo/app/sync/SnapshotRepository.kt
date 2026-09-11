package jp.yomumemo.app.sync

import jp.yomumemo.app.data.db.BookDao
import jp.yomumemo.app.data.db.NoteDao
import kotlinx.serialization.json.Json

/**
 * 端末のデータとスナップショットの間を取り持つ。
 *
 * 読み込みは「置き換え」ではなく「統合」にしている。
 * 復元のたびに手元の新しい編集が消えると、バックアップが怖くて使えなくなるため。
 */
class SnapshotRepository(
    private val bookDao: BookDao,
    private val noteDao: NoteDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 現在の全データ(論理削除済みを含む)を書き出す。 */
    suspend fun capture(): Snapshot = Snapshot(
        exportedAt = now(),
        // 削除済みも含める。含めないと、相手の端末で削除が伝わらず本が復活する。
        books = bookDao.allIncludingDeleted().map { it.toSnapshot() },
        notes = noteDao.allIncludingDeleted().map { it.toSnapshot() },
    )

    /**
     * 受け取ったスナップショットを手元に統合する。
     * 統合後の内容を返すので、そのまま保管場所へ書き戻せる。
     */
    suspend fun merge(incoming: Snapshot): SnapshotMerger.MergeResult {
        val local = capture()
        val result = SnapshotMerger.merge(local = local, remote = incoming, now = now())

        // 外部キーの都合で本を先に入れる
        bookDao.upsertAll(result.merged.books.map { it.toEntity() })

        // 対応する本が無いメモは取り込まない。外部キー制約で失敗するうえ、
        // 本体の無いメモは画面のどこにも出せず、ただ容量を食うだけになる。
        val knownBookIds = result.merged.books.map { it.id }.toSet()
        noteDao.upsertAll(
            result.merged.notes
                .filter { it.bookId in knownBookIds }
                .map { it.toEntity() },
        )

        return result
    }

    fun encode(snapshot: Snapshot): String = json.encodeToString(Snapshot.serializer(), snapshot)

    /** 壊れたファイルや別アプリのファイルを読まされても落とさない。 */
    fun decode(text: String): Snapshot? =
        runCatching { json.decodeFromString(Snapshot.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.version <= Snapshot.CURRENT_VERSION }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
