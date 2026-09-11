package jp.yomumemo.app.data.db

import androidx.room.TypeConverter
import jp.yomumemo.app.data.db.entity.NoteType
import jp.yomumemo.app.data.db.entity.ReadingStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room の型変換。
 * 著者は複数いるため JSON 配列として1カラムに保持する
 * (別テーブルに正規化するほどの検索要件がなく、同期スナップショットにもそのまま載せられる)。
 */
class Converters {

    @TypeConverter
    fun authorsToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToAuthors(value: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())

    @TypeConverter
    fun statusToName(value: ReadingStatus): String = value.name

    @TypeConverter
    fun nameToStatus(value: String): ReadingStatus =
        runCatching { ReadingStatus.valueOf(value) }.getOrDefault(ReadingStatus.WANT)

    @TypeConverter
    fun noteTypeToName(value: NoteType): String = value.name

    @TypeConverter
    fun nameToNoteType(value: String): NoteType =
        runCatching { NoteType.valueOf(value) }.getOrDefault(NoteType.THOUGHT)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
