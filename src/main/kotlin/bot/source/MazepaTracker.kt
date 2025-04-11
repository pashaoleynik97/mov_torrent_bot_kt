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

class MazepaTracker : TrackerSource {

    private val client: HttpClient = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }

        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        }
    }

    private var authorized = false

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

    private fun String.parseResults(searchQuery: String): List<TrackerSource.TrackerSearchResult> {
        val doc = Jsoup.parse(this)
        val rows = doc.select("table.forumline tr.tCenter")

        return rows.mapNotNull { row ->
            val titleLink = row.selectFirst("a.topictitle")
            val releaseName = titleLink?.text()?.trim() ?: return@mapNotNull null
            val relativeUrl = titleLink.attr("href")
            val pageUrl = "https://mazepa.to/$relativeUrl"

            TrackerSource.TrackerSearchResult.create(
                tracker = this@MazepaTracker,
                releaseName = releaseName,
                size = null,
                pageUrl = pageUrl,
                searchQueryUsed = searchRequest(searchQuery)
            )
        }
    }

    @Throws(IllegalStateException::class)
    private suspend fun searchByName(searchQuery: String): String {
        val response = client.submitForm(
            url = "https://mazepa.to/search.php",
            formParameters = Parameters.build {
                append("nm", searchQuery)
                append("allw", "1")
                append("pn", "")
                append("f[]", "0")
                append("tm", "0")
                append("dm", "0")
                append("o", "1")
                append("s", "0")
                append("submit", "\u00A0\u00A0Пошук\u00A0\u00A0") // non-breaking spaces
            }
        ) {
            headers {
                append("Referer", "https://mazepa.to/search.php")
                append("Origin", "https://mazepa.to")
                append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
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