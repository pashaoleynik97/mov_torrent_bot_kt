package bot.state

import bot.source.TrackerSource

object UserSessionManager {
    private val sessions = mutableMapOf<Long, UserSession>()
    private val searchResultsMap = mutableMapOf<Long, List<TrackerSource.TrackerSearchResult>>()
    private val pendingQueryMap = mutableMapOf<Long, UserInputContext>()
    private val pendingTorrents = mutableMapOf<Long, PendingTorrent>()

    fun getSession(chatId: Long): UserSession =
        sessions.getOrPut(chatId) { UserSession(State.Idle) }

    fun setState(chatId: Long, state: State) {
        sessions.getOrPut(chatId) { UserSession(state) }.state = state
    }

    fun clear(chatId: Long) {
        sessions.remove(chatId)
        searchResultsMap.remove(chatId)
        pendingQueryMap.remove(chatId)
    }

    fun setSearchResults(chatId: Long, results: List<TrackerSource.TrackerSearchResult>) {
        searchResultsMap[chatId] = results
    }

    fun getSearchResults(chatId: Long): List<TrackerSource.TrackerSearchResult> {
        return searchResultsMap[chatId] ?: emptyList()
    }

    fun setPendingQuery(chatId: Long, query: String, savePath: String) {
        pendingQueryMap[chatId] = UserInputContext(query, savePath)
    }

    fun getPendingQuery(chatId: Long): UserInputContext? {
        return pendingQueryMap[chatId]
    }

    fun clearSearchResults(chatId: Long) {
        searchResultsMap.remove(chatId)
    }

    fun setPendingTorrent(chatId: Long, source: String, isFile: Boolean) {
        pendingTorrents[chatId] = PendingTorrent(
            source = source,
            isFile = isFile,
            category = null
        )
    }

    fun getPendingTorrent(chatId: Long): PendingTorrent? = pendingTorrents[chatId]

    fun setTorrentCategory(chatId: Long, category: TorrentCategory) {
        val existing = pendingTorrents[chatId]
        if (existing != null) {
            pendingTorrents[chatId] = existing.copy(category = category)
        }
    }

    fun clearPendingTorrent(chatId: Long) {
        pendingTorrents.remove(chatId)
    }
}

data class UserSession(var state: State)

sealed class State {
    data object AuthPrompt : State()
    data object Idle : State()
    data object AwaitingMovieName : State()
    data object AwaitingTVSeriesName : State()
    data object AwaitingTrackerSelection : State()
    data object AwaitingTorrentFileUrl : State()
    data object AwaitingTorrentFileUpload : State()
    data object AwaitingTorrentCategorySelection : State()
}

data class UserInputContext(
    val query: String,
    val savePath: String
)

data class PendingTorrent(
    val source: String,              // file path or URL
    val isFile: Boolean,             // true = file path, false = URL
    val category: TorrentCategory?   // user-defined: Movie or Series
)

enum class TorrentCategory {
    MOVIE, SERIES
}