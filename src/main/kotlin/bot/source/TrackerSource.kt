package bot.source

interface TrackerSource {

    data class TrackerSearchResult(
        val trackerName: String,
        val releaseName: String,
        val size: String?,
        val pageUrl: String
    ) {
        val displayString: String
            get() = "$releaseName | 🔶 $trackerName | $pageUrl".let {
                if (size != null) "$it | 💾 $size" else it
            }
    }

    suspend fun search(searchQuery: String): List<TrackerSearchResult>

}