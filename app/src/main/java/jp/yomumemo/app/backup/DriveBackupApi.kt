package jp.yomumemo.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val BACKUP_FILE_NAME = "yomumemo-backup.json"
private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

/**
 * Googleドライブ上の「このアプリが作った1個のバックアップファイル」を探す・作る・更新する、
 * 薄いRESTクライアント。drive.fileスコープなので、このアプリが作成したファイル以外は
 * 見えない(他のファイルを一覧したり触ったりはできない)。
 */
class DriveBackupApi(private val client: OkHttpClient) {

    class DriveApiException(message: String) : IOException(message)

    /** 既存のバックアップファイルのIDを探す。無ければ null。 */
    suspend fun findBackupFileId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val url = "$FILES_URL?q=" + java.net.URLEncoder.encode("name = '$BACKUP_FILE_NAME' and trashed = false", "UTF-8") +
            "&spaces=drive&fields=files(id,name)"
        val text = execute(Request.Builder().url(url).get().build(), accessToken)
        val files = (Json.parseToJsonElement(text) as JsonObject)["files"] as? JsonArray ?: return@withContext null
        files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    /** バックアップ内容を保存する。[existingFileId] が無ければ新規作成、あれば中身を上書きする。 */
    suspend fun save(accessToken: String, existingFileId: String?, jsonContent: String): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json".toMediaType()
        if (existingFileId != null) {
            execute(
                Request.Builder()
                    .url("$UPLOAD_URL/$existingFileId?uploadType=media")
                    .patch(jsonContent.toRequestBody(mediaType))
                    .build(),
                accessToken,
            )
            existingFileId
        } else {
            val boundary = "yomumemo-backup-boundary"
            val body = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append("""{"name":"$BACKUP_FILE_NAME"}""")
                append("\r\n--$boundary\r\n")
                append("Content-Type: application/json\r\n\r\n")
                append(jsonContent)
                append("\r\n--$boundary--")
            }
            val text = execute(
                Request.Builder()
                    .url("$UPLOAD_URL?uploadType=multipart")
                    .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                    .build(),
                accessToken,
            )
            (Json.parseToJsonElement(text) as JsonObject)["id"]!!.jsonPrimitive.content
        }
    }

    /** バックアップ内容を読み込む。 */
    suspend fun read(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$FILES_URL/$fileId?alt=media").get().build(), accessToken)
    }

    private fun execute(request: Request, accessToken: String): String {
        val authed = request.newBuilder().header("Authorization", "Bearer $accessToken").build()
        try {
            client.newCall(authed).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw DriveApiException("Googleドライブとの通信に失敗しました (${response.code})")
                return text
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException("通信に失敗しました。ネットワーク接続を確認してください")
        }
    }
}
