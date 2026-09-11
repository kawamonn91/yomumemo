package jp.yomumemo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteFts
import jp.yomumemo.app.data.db.entity.NoteTagCrossRef
import jp.yomumemo.app.data.db.entity.TagEntity

@Database(
    entities = [
        BookEntity::class,
        NoteEntity::class,
        NoteFts::class,
        TagEntity::class,
        NoteTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YomuMemoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "yomumemo.db"

        fun build(context: Context): YomuMemoDatabase =
            Room.databaseBuilder(context.applicationContext, YomuMemoDatabase::class.java, NAME)
                // 外部キーのカスケード削除を有効にする (Room の既定では無効)
                .build()
    }
}
