package jp.yomumemo.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 表紙の自分撮り登録(v2)のマイグレーションが、既存ユーザーのデータを壊さずに通ることを確かめる。
 * 単体テストの Fake DAO や inMemoryDatabaseBuilder では「実際に入っている旧バージョンのDB」を
 * 用意できないため、実SQLiteに対して検証する。
 */
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        YomuMemoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v1からv2へ_既存の本のデータを保ったままlocalCoverPath列が追加される() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO books (id, isbn13, title, subtitle, authors, publisher, publishedDate, " +
                    "coverUrl, pageCount, description, status, rating, currentPage, addedAt, startedAt, " +
                    "finishedAt, updatedAt, deletedAt) VALUES ('b1', '9784873115658', 'リーダブルコード', " +
                    "NULL, '[\"著者A\"]', NULL, NULL, 'https://example.com/cover.jpg', NULL, NULL, " +
                    "'READING', NULL, 0, 1, NULL, NULL, 1, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, YomuMemoDatabase.MIGRATION_1_2)

        db.query("SELECT title, coverUrl, localCoverPath FROM books WHERE id = 'b1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("リーダブルコード", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("https://example.com/cover.jpg", cursor.getString(cursor.getColumnIndexOrThrow("coverUrl")))
            assertEquals(true, cursor.isNull(cursor.getColumnIndexOrThrow("localCoverPath")))
        }
    }

    @Test
    fun v2からv3へ_既存の本のデータを保ったままfoldersテーブルとfolderId列が追加される() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO books (id, isbn13, title, subtitle, authors, publisher, publishedDate, " +
                    "coverUrl, localCoverPath, pageCount, description, status, rating, currentPage, addedAt, " +
                    "startedAt, finishedAt, updatedAt, deletedAt) VALUES ('b1', '9784873115658', " +
                    "'リーダブルコード', NULL, '[\"著者A\"]', NULL, NULL, 'https://example.com/cover.jpg', " +
                    "NULL, NULL, NULL, 'READING', NULL, 0, 1, NULL, NULL, 1, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, YomuMemoDatabase.MIGRATION_2_3)

        db.query("SELECT title, folderId FROM books WHERE id = 'b1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("リーダブルコード", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(true, cursor.isNull(cursor.getColumnIndexOrThrow("folderId")))
        }

        db.query("SELECT COUNT(*) FROM folders").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
