package jp.yomumemo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.yomumemo.app.data.db.entity.BookEntity
import jp.yomumemo.app.data.db.entity.FolderEntity
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
        FolderEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YomuMemoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val NAME = "yomumemo.db"

        /** v2: 表紙の自分撮り登録用に、端末内画像パスの列を追加。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN localCoverPath TEXT")
            }
        }

        /**
         * v3: 自分で作るフォルダ(ジャンル分けなど)を追加。
         *
         * books.folderId には folders への外部キーを張るが、SQLite の
         * ALTER TABLE ADD COLUMN では外部キー制約を追加できない
         * (Room のスキーマ検証で「Migration didn't properly handle」として弾かれる)。
         * そのため books テーブルは作り直し(新テーブルへコピー→旧テーブル削除→リネーム)で対応する。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_sortOrder` ON `folders` (`sortOrder`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books_new` (" +
                        "`id` TEXT NOT NULL, `isbn13` TEXT, `title` TEXT NOT NULL, `subtitle` TEXT, " +
                        "`authors` TEXT NOT NULL, `publisher` TEXT, `publishedDate` TEXT, `coverUrl` TEXT, " +
                        "`folderId` TEXT, `localCoverPath` TEXT, `pageCount` INTEGER, `description` TEXT, " +
                        "`status` TEXT NOT NULL, `rating` INTEGER, `currentPage` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, `startedAt` INTEGER, `finishedAt` INTEGER, " +
                        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
                )
                db.execSQL(
                    "INSERT INTO `books_new` (id, isbn13, title, subtitle, authors, publisher, publishedDate, " +
                        "coverUrl, folderId, localCoverPath, pageCount, description, status, rating, currentPage, " +
                        "addedAt, startedAt, finishedAt, updatedAt, deletedAt) " +
                        "SELECT id, isbn13, title, subtitle, authors, publisher, publishedDate, " +
                        "coverUrl, NULL, localCoverPath, pageCount, description, status, rating, currentPage, " +
                        "addedAt, startedAt, finishedAt, updatedAt, deletedAt FROM `books`",
                )
                db.execSQL("DROP TABLE `books`")
                db.execSQL("ALTER TABLE `books_new` RENAME TO `books`")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_isbn13` ON `books` (`isbn13`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_status` ON `books` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_updatedAt` ON `books` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_folderId` ON `books` (`folderId`)")
            }
        }

        fun build(context: Context): YomuMemoDatabase =
            Room.databaseBuilder(context.applicationContext, YomuMemoDatabase::class.java, NAME)
                // 外部キーのカスケード削除を有効にする (Room の既定では無効)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
