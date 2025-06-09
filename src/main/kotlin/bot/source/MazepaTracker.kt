package bot.source

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class MazepaTracker : TrackerSource {

    companion object {
        private val MAX_AUTH_SESSION_TIME = TimeUnit.HOURS.toMillis(6)
    }

    private val client: HttpClient = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }

        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        }
    }

    private var _authorized = false
    private var _authorizedAt: Long = 0L

    private var authorized: Boolean
        get() {
            return _authorized && (System.currentTimeMillis() - _authorizedAt <= MAX_AUTH_SESSION_TIME)
        }
        set(value) {
            _authorized = value
            _authorizedAt = System.currentTimeMillis()
        }

    override val name: String
        get() = "Mazepa"

    override suspend fun search(searchQuery: String): List<TrackerSource.TrackerSearchResult> {
        if (!authorized) login()

        suspend fun trySearch(): String {
            return searchByName(searchQuery)
        }

        val result: String = try {
            trySearch()
        } catch (e: IllegalStateException) {
            login()
            trySearch()
        }

        return result.parseResults(searchQuery)
    }

    override fun searchRequest(searchQuery: String): String {
        return "https://mazepa.to/search.php?nm=$searchQuery"
    }

    override suspend fun getDownloadUrlFromReleasePage(pageUrl: String): String {
        val html = client.get(pageUrl).bodyAsText()
        val doc = Jsoup.parse(html)
        val downloadLink = doc.selectFirst("a[href^=dl.php?id=]")?.attr("href")
            ?: error("❌ Download link not found on release page.")
        return "https://mazepa.to/$downloadLink"
    }

    override fun authorizedClient(): HttpClient {
        return client
    }

    private fun String.parseResults(searchQuery: String): List<TrackerSource.TrackerSearchResult> {
        val doc = Jsoup.parse(this)
        val rows = doc.select("#forum_table tr[id^=tor_]")

        return rows.mapNotNull { row ->
            val titleLink = row.selectFirst("td:nth-child(4) a") ?: return@mapNotNull null
            val releaseName = titleLink.text().trim()
            val relativeUrl = titleLink.attr("href")
            val pageUrl = "https://mazepa.to/$relativeUrl"

            val sizeCell = row.selectFirst("td:nth-child(6)")
            val size = sizeCell?.text()?.trim()

            TrackerSource.TrackerSearchResult.create(
                tracker = this@MazepaTracker,
                releaseName = releaseName,
                size = size,
                pageUrl = pageUrl,
                searchQueryUsed = searchRequest(searchQuery)
            )
        }
    }

    @Throws(IllegalStateException::class)
    private suspend fun searchByName(searchQuery: String): String {
        val response = client.submitForm(
            url = "https://mazepa.to/tracker.php",
            formParameters = Parameters.build {
                append("nm", searchQuery)
                append("max", "1")
                append("to", "1")
            }
        ) {
            method = HttpMethod.Post
            headers {
                append("Referer", "https://mazepa.to/")
                append("Origin", "https://mazepa.to")
                append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                append(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            }
        }

        val html = response.bodyAsText()

        val title = Jsoup.parse(html).title()
        println("🔍 Page title: $title")

        if (title.startsWith("Увійти")) {
            println("⚠️ Detected login page — session expired.")
            authorized = false
            throw IllegalStateException("Authorization expired, login required again.")
        }

        return html
    }

    private suspend fun login(): Boolean {
        val loginResponse = client.submitForm(
            url = "https://mazepa.to/login.php",
            formParameters = Parameters.build {
                append("login_username", System.getenv("MAZEPA_USER") ?: "user")
                append("login_password", System.getenv("MAZEPA_PASS") ?: "pass")
                append("autologin", "1") // checkbox values are usually "1"
                append("login", "Увійти") // this must match button value
            }
        )

        return (loginResponse.status.value in 200..399).also {
            authorized = it
            println("Mazepa Authorized!")
        }
    }

}