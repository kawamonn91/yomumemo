package jp.yomumemo.app.sync

/**
 * 2つのスナップショットを突き合わせる。
 *
 * 端末間の同期はサーバーを持たず、各端末がユーザー自身の保管場所へ
 * スナップショットを置いて読み書きする方式にしている。
 * 中央で順序を決める仕組みが無いので、レコードごとに updatedAt が新しい方を採る
 * (last-write-wins)。
 *
 * 前提として ID は端末側で生成する UUID にしてある。自動採番だと
 * 別々の端末が同じ番号を別のレコードに割り当て、統合時に取り違える。
 *
 * 削除は論理削除として扱う。行を消してしまうと「相手が消した」のか
 * 「相手がまだ知らない」のか区別できず、消したはずの本が復活する。
 */
object SnapshotMerger {

    data class MergeResult(
        val merged: Snapshot,
        val booksUpdatedLocally: Int,
        val notesUpdatedLocally: Int,
    )

    /**
     * [local] と [remote] を統合する。
     * 戻り値の merged は両者を含み、各レコードは新しい方の内容になる。
     */
    fun merge(local: Snapshot, remote: Snapshot, now: Long): MergeResult {
        val localBooks = local.books.associateBy { it.id }
        val remoteBooks = remote.books.associateBy { it.id }
        val localNotes = local.notes.associateBy { it.id }
        val remoteNotes = remote.notes.associateBy { it.id }

        var booksChanged = 0
        val mergedBooks = (localBooks.keys + remoteBooks.keys).map { id ->
            val mine = localBooks[id]
            val theirs = remoteBooks[id]
            val winner = pick(mine, theirs, { it.updatedAt })
            if (winner !== mine) booksChanged++
            winner
        }

        var notesChanged = 0
        val mergedNotes = (localNotes.keys + remoteNotes.keys).map { id ->
            val mine = localNotes[id]
            val theirs = remoteNotes[id]
            val winner = pick(mine, theirs, { it.updatedAt })
            if (winner !== mine) notesChanged++
            winner
        }

        return MergeResult(
            merged = Snapshot(
                version = Snapshot.CURRENT_VERSION,
                exportedAt = now,
                books = mergedBooks.sortedBy { it.addedAt },
                notes = mergedNotes.sortedBy { it.createdAt },
            ),
            booksUpdatedLocally = booksChanged,
            notesUpdatedLocally = notesChanged,
        )
    }

    /**
     * 新しい方を選ぶ。
     *
     * 更新時刻が同じ場合は手元の方を残す。端末の時計は完全には揃っておらず、
     * 同着を相手優先にすると、往復のたびに内容が入れ替わって落ち着かなくなる。
     */
    private fun <T : Any> pick(mine: T?, theirs: T?, updatedAt: (T) -> Long): T {
        if (mine == null) return theirs!!
        if (theirs == null) return mine
        return if (updatedAt(theirs) > updatedAt(mine)) theirs else mine
    }
}
