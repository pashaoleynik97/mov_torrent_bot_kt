package bot.util

import bot.env.secret
import com.github.kotlintelegrambot.Bot
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private val httpClient = OkHttpClient()

fun Bot.cacheTorrentFile(chatId: Long, source: String, isFile: Boolean): File {
    val dir = File("cache/$chatId").apply { mkdirs() }
    val hashedName = sha256(source).take(16) + ".torrent"
    val outputFile = File(dir, hashedName)

    if (outputFile.exists()) return outputFile

    val fileUrl = if (isFile) {
        val filePath = getFilePathFromTelegram(source)
        "https://api.telegram.org/file/bot${secret.botToken}/$filePath"
    } else {
        source
    }

    downloadFile(fileUrl, outputFile)
    return outputFile
}

private fun getFilePathFromTelegram(fileId: String): String {
    val url = "https://api.telegram.org/bot${secret.botToken}/getFile?file_id=$fileId"
    val request = Request.Builder().url(url).build()

    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("❌ Failed to get file path from Telegram (HTTP ${response.code()})")
        }

        val body = response.body()?.string() ?: error("❌ Empty response body from Telegram")
        val json = JSONObject(body)

        if (!json.getBoolean("ok")) {
            error("❌ Telegram API returned error: ${json.optString("description")}")
        }

        return json.getJSONObject("result").getString("file_path")
    }
}

fun downloadFile(url: String, destination: File) {
    val request = Request.Builder().url(url).build()
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("❌ Failed to download file: $url (HTTP ${response.code()})")
        }

        response.body()?.byteStream()?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: error("❌ Empty response body when downloading file from: $url")
    }
}

fun sha256(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

fun cleanupOldCache(thresholdHours: Long = 24) {
    val cutoff = System.currentTimeMillis() - thresholdHours * 3600 * 1000
    File("cache").walkTopDown()
        .filter { it.isFile && it.lastModified() < cutoff }
        .forEach { it.delete() }
}
