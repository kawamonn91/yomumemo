package jp.yomumemo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YomuMemoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "yomumemo.db"

        /** v2: 表紙の自分撮り登録用に、端末内画像パスの列を追加。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN localCoverPath TEXT")
            }
        }

        fun build(context: Context): YomuMemoDatabase =
            Room.databaseBuilder(context.applicationContext, YomuMemoDatabase::class.java, NAME)
                // 外部キーのカスケード削除を有効にする (Room の既定では無効)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
