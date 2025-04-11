package bot.util

import okhttp3.OkHttpClient
import okhttp3.Request

private val httpClient = OkHttpClient()

fun followRedirect(url: String): String {
    val request = Request.Builder().url(url).head().build()
    httpClient.newCall(request).execute().use { response ->
        return response.request().url().toString()
    }
}