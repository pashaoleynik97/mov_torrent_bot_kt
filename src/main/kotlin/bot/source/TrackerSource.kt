package bot.source

import io.ktor.client.*
import okhttp3.OkHttpClient

interface TrackerSource {

    val name: String

    class TrackerSearchResult private constructor(
        val trackerName: String,
        val releaseName: String,
        val size: String?,
        val pageUrl: String,
        val searchQueryUsed: String
    ) {

        companion object {
            fun create(
                tracker: TrackerSource,
                releaseName: String,
                size: String?,
                pageUrl: String,
                searchQueryUsed: String
            ): TrackerSearchResult = TrackerSearchResult(
                trackerName = tracker.name,
                releaseName = releaseName,
                size = size,
                pageUrl = pageUrl,
                searchQueryUsed = searchQueryUsed
            )
        }

        val displayString: String
            get() = "$releaseName | 🔶 $trackerName | $pageUrl".let {
                if (size != null) "$it | 💾 $size" else it
            }
    }

    suspend fun search(searchQuery: String): List<TrackerSearchResult>

    fun searchRequest(searchQuery: String): String

    suspend fun getDownloadUrlFromReleasePage(pageUrl: String): String

    fun authorizedClient(): HttpClient?

}