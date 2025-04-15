import bot.auth.BotAuthUtil
import bot.auth.authorizedCallbackQuery
import bot.auth.authorizedCommand
import bot.env.secret
import bot.filter.DocumentFilter
import bot.source.MazepaTracker
import bot.source.TrackerSource
import bot.state.State
import bot.state.TorrentCategory
import bot.state.UserSessionManager
import bot.util.cacheTorrentFile
import bot.util.extractTagEmoji
import bot.util.followRedirect
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

val trackers = listOf(
    MazepaTracker()
)

val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun main(args: Array<String>) {
    println("Welcome to movtorrentbot!")

    val bot = bot {
        token = secret.botToken
        logLevel = LogLevel.All()

        dispatch {
            command("start") {
                val chatId = message.chat.id
                bot.sendMessage(chatId = ChatId.fromId(chatId), text = "Welcome to movtorrentbot, ${message.chat.username ?: "dude"}!")

                if (!BotAuthUtil.isAuthorized(chatId)) {
                    BotAuthUtil.markPending(chatId)
                    bot.sendMessage(chatId = ChatId.fromId(chatId), text = "🔐 Please enter the password to access the bot.")
                    UserSessionManager.setState(chatId, State.AuthPrompt)
                } else {
                    bot.sendMessage(chatId = ChatId.fromId(chatId), text = "🍻 You are in. Type /menu to access bot features.")
                    UserSessionManager.setState(chatId, State.Idle)
                }
            }

            // Global text handler
            handleUserInput()
            handleUserFileInput()

            authorizedCommand("menu") {
                menu()
            }

            authorizedCallbackQuery("back_to_menu") {
                menu()
            }

            authorizedCallbackQuery("download_movie") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                UserSessionManager.setState(chatId, State.AwaitingMovieName)
                bot.sendMessage(ChatId.fromId(chatId), "🎬 Enter movie name:")
            }

            authorizedCallbackQuery("download_series") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                UserSessionManager.setState(chatId, State.AwaitingTVSeriesName)
                bot.sendMessage(ChatId.fromId(chatId), "📺 Enter TV series name:")
            }

            authorizedCallbackQuery("select_tracker") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                val trackerName = callbackQuery.data.split("::").getOrNull(1) ?: return@authorizedCallbackQuery
                val tracker = trackers.find { it.name == trackerName } ?: run {
                    bot.sendMessage(ChatId.fromId(chatId), "\u274C Tracker not found.")
                    return@authorizedCallbackQuery
                }

                val queryContext = UserSessionManager.getPendingQuery(chatId)
                if (queryContext == null) {
                    bot.sendMessage(ChatId.fromId(chatId), "\u274C No query found. Try again from /menu.")
                    UserSessionManager.setState(chatId, State.Idle)
                    return@authorizedCallbackQuery
                }

                handleSearchInput(queryContext.query, chatId, queryContext.category, tracker)
                UserSessionManager.setState(chatId, State.Idle)
            }

            authorizedCallbackQuery("show_tracker_search_results") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                val results = UserSessionManager.getSearchResults(chatId)

                if (results.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(chatId), "❌ No previous search found.")
                    return@authorizedCallbackQuery
                }

                val url = results.first().searchQueryUsed

                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "🌐 Raw search link:\n<a href=\"$url\">$url</a>",
                    parseMode = ParseMode.HTML
                )
            }

            authorizedCallbackQuery("drop_torrent_link") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                UserSessionManager.setState(chatId, State.AwaitingTorrentFileUrl)
                bot.sendMessage(ChatId.fromId(chatId), "🔗 Please send the URL to the .torrent file:")
            }

            authorizedCallbackQuery("drop_torrent_file") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                UserSessionManager.setState(chatId, State.AwaitingTorrentFileUpload)
                bot.sendMessage(ChatId.fromId(chatId), "📄 Please send the .torrent file:")
            }

            authorizedCallbackQuery("torrent_category::movie", "torrent_category::series") {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                val isMovie = callbackQuery.data.endsWith("movie")
                val category = if (isMovie) TorrentCategory.MOVIE else TorrentCategory.SERIES

                UserSessionManager.setTorrentCategory(chatId, category)
                UserSessionManager.setState(chatId, State.Idle)

                val pending = UserSessionManager.getPendingTorrent(chatId)
                val source = pending?.source ?: "unknown"

                bot.sendMessage(ChatId.fromId(chatId), "✅ Received ${if (isMovie) "movie" else "series"} torrent from ${if (pending?.isFile == true) "file" else "link"}:\n<code>$source</code>", parseMode = ParseMode.HTML)

                val cachedFile = bot.cacheTorrentFile(chatId, source, pending?.isFile == true)

                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "📁 Cached file: <code>${cachedFile.name}</code>",
                    parseMode = ParseMode.HTML
                )

                bot.promptToDownloadTorrent(chatId, cachedFile, category)
            }

            authorizedCallbackQuery(callbackData = null) {
                val chatId = callbackQuery.message?.chat?.id ?: return@authorizedCallbackQuery
                val data = callbackQuery.data ?: return@authorizedCallbackQuery

                when {
                    data.startsWith("select::") -> {
                        val parts = data.removePrefix("select::").split("::")
                        if (parts.size != 2) return@authorizedCallbackQuery

                        val index = parts[0].toIntOrNull() ?: return@authorizedCallbackQuery
                        val category = runCatching { TorrentCategory.valueOf(parts[1]) }.getOrNull() ?: return@authorizedCallbackQuery

                        val results = UserSessionManager.getSearchResults(chatId)
                        val result = results.getOrNull(index) ?: return@authorizedCallbackQuery

                        val tracker = trackers.find { it.name == result.trackerName } ?: return@authorizedCallbackQuery

                        coroutineScope.launch {
                            try {
                                bot.sendMessage(ChatId.fromId(chatId), "📥 Fetching torrent for:\n<b>${result.releaseName}</b>", parseMode = ParseMode.HTML)

                                val rawUrl = tracker.getDownloadUrlFromReleasePage(result.pageUrl)
                                val finalUrl = followRedirect(rawUrl)
                                val file = bot.cacheTorrentFile(chatId, finalUrl, isFile = false)

                                UserSessionManager.setPendingTorrent(chatId, file.absolutePath, isFile = true)
                                UserSessionManager.setTorrentCategory(chatId, category)
                                UserSessionManager.setState(chatId, State.Idle)

                                bot.sendMessage(
                                    ChatId.fromId(chatId),
                                    "✅ Torrent cached as ${category.name.lowercase()}:\n<code>${file.name}</code>",
                                    parseMode = ParseMode.HTML
                                )

                                bot.promptToDownloadTorrent(chatId, file, category)

                            } catch (e: Exception) {
                                e.printStackTrace()
                                bot.sendMessage(ChatId.fromId(chatId), "❌ Failed to handle release: ${e.message}")
                            }
                        }
                    }

                    data.startsWith("confirm_download::") -> {
                        val parts = data.removePrefix("confirm_download::").split("::")
                        if (parts.size != 2) return@authorizedCallbackQuery

                        val fileName = parts[0]
                        val category = runCatching { TorrentCategory.valueOf(parts[1]) }.getOrNull() ?: return@authorizedCallbackQuery

                        val srcFile = File("cache/$chatId/$fileName")
                        if (!srcFile.exists()) {
                            bot.sendMessage(ChatId.fromId(chatId), "❌ Cached file not found: $fileName")
                            return@authorizedCallbackQuery
                        }

                        val destDir = File("queue/${category.name.lowercase()}").apply { mkdirs() }
                        val finalDestFile = File(destDir, fileName)
                        val tempDestFile = File(destDir, "temp_${System.currentTimeMillis()}.torrent")

                        try {
                            // Copy bytes to temp file first
                            tempDestFile.writeBytes(srcFile.readBytes())

                            // Rename after write is complete
                            if (!tempDestFile.renameTo(finalDestFile)) {
                                throw IllegalStateException("Rename failed from ${tempDestFile.name} to ${finalDestFile.name}")
                            }

                            bot.sendMessage(
                                ChatId.fromId(chatId),
                                "📥 Download request added to queue: <code>${finalDestFile.name}</code>",
                                parseMode = ParseMode.HTML
                            )
                            UserSessionManager.setState(chatId, State.Idle)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            bot.sendMessage(ChatId.fromId(chatId), "❌ Failed to queue download: ${e.message}")
                        }
                    }
                }
            }

        }
    }

    bot.startPolling()
}

fun CallbackQueryHandlerEnvironment.menu() {
    val chatId = callbackQuery.message?.chat?.id ?: return
    menu(bot, chatId)
}

fun CommandHandlerEnvironment.menu() {
    val chatId = message.chat.id
    menu(bot, chatId)
}

fun menu(bot: Bot, chatId: Long) {
    if (!BotAuthUtil.isAuthorized(chatId)) {
        BotAuthUtil.markPending(chatId)
        bot.sendMessage(ChatId.fromId(chatId), "🔐 Please enter the password to access the bot.")
        UserSessionManager.setState(chatId, State.AuthPrompt)
        return
    }

    // Reset state and previous data
    UserSessionManager.setState(chatId, State.Idle)
    UserSessionManager.clearSearchResults(chatId)

    val buttons = InlineKeyboardMarkup.create(
        listOf(
            InlineKeyboardButton.CallbackData("🎬 Download Movie", "download_movie"),
            InlineKeyboardButton.CallbackData("📺 Download TV Series", "download_series")
        ),
        listOf(
            InlineKeyboardButton.CallbackData("🎯 Drop Torrent File Link", "drop_torrent_link"),
            InlineKeyboardButton.CallbackData("📄 Drop Torrent File", "drop_torrent_file")
        )
    )

    bot.sendMessage(
        chatId = ChatId.fromId(chatId),
        text = "📋 Main Menu:\nWhat would you like to download?",
        replyMarkup = buttons
    )
}

fun Bot.promptToDownloadTorrent(
    chatId: Long,
    torrentFile: File,
    category: TorrentCategory
) {
    val message = buildString {
        appendLine("🎯 Would you like to start downloading this ${category.name.lowercase()}?")
        appendLine()
        append("<code>${torrentFile.name}</code>")
    }

    val buttons = InlineKeyboardMarkup.create(
        listOf(
            InlineKeyboardButton.CallbackData("✅ Yes", "confirm_download::${torrentFile.name}::${category.name}"),
            InlineKeyboardButton.CallbackData("❌ No", "back_to_menu")
        )
    )

    sendMessage(
        chatId = ChatId.fromId(chatId),
        text = message,
        parseMode = ParseMode.HTML,
        replyMarkup = buttons
    )
}

fun Dispatcher.handleUserFileInput() {
    message(DocumentFilter) {
        val chatId = message.chat.id
        val document = message.document ?: return@message

        println("📦 Received document: ${document.fileName} (${document.fileId}) from chat $chatId")

        val session = UserSessionManager.getSession(chatId)
        when (val state = session.state) {
            is State.AwaitingTorrentFileUpload -> {
                val document = message.document
                if (document != null) {

                    if (document.fileName?.endsWith(".torrent") == false) {
                        bot.sendMessage(ChatId.fromId(chatId), "⚠️ This doesn't look like a .torrent file.")
                        return@message
                    }

                    // Save file_id in session for later download
                    UserSessionManager.setPendingTorrent(chatId, source = document.fileId, isFile = true)

                    bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = "📄 Received .torrent file! What is this?",
                        replyMarkup = InlineKeyboardMarkup.create(
                            listOf(
                                InlineKeyboardButton.CallbackData("🎬 Movie", "torrent_category::movie"),
                                InlineKeyboardButton.CallbackData("📺 TV Series", "torrent_category::series")
                            )
                        )
                    )

                    UserSessionManager.setState(chatId, State.AwaitingTorrentCategorySelection)
                }
            }
            else -> {
                bot.sendMessage(ChatId.fromId(chatId), "🙃 Have no idea what you want from me! \nMaybe, let\'s start from /menu again?")
            }
        }

    }

}

fun Dispatcher.handleUserInput() {
    message(Filter.Text) {
        val chatId = message.chat.id
        val text = message.text ?: return@message

        val session = UserSessionManager.getSession(chatId)
        when (val state = session.state) {
            is State.Idle -> {
                bot.sendMessage(ChatId.fromId(chatId), "🙃 Have no idea what you want from me! \nMaybe, let\'s start from /menu again?")
            }
            is State.AuthPrompt -> {
                if (BotAuthUtil.needsPassword(chatId)) {
                    if (BotAuthUtil.authorize(text, chatId)) {
                        bot.sendMessage(ChatId.fromId(chatId), "🍻 You are in! Type /menu to access bot features.")
                        UserSessionManager.setState(chatId, State.Idle)
                    } else {
                        bot.sendMessage(ChatId.fromId(chatId), "❌ Incorrect password. Try again.")
                        UserSessionManager.setState(chatId, State.AuthPrompt)
                    }
                }
            }
            is State.AwaitingMovieName, is State.AwaitingTVSeriesName -> {
                UserSessionManager.setPendingQuery(chatId, text, category = if (state is State.AwaitingMovieName) TorrentCategory.MOVIE else TorrentCategory.SERIES)

                val trackerButtons = trackers.map {
                    listOf(InlineKeyboardButton.CallbackData(it.name, "select_tracker::${it.name}"))
                }

                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "\uD83D\uDD0D Choose a tracker to search:",
                    replyMarkup = InlineKeyboardMarkup.create(trackerButtons)
                )

                UserSessionManager.setState(chatId, State.AwaitingTrackerSelection)
            }
            is State.AwaitingTorrentFileUrl -> {
                val url = text
                if (!url.endsWith(".torrent")) {
                    bot.sendMessage(ChatId.fromId(chatId), "⚠️ Please make sure this is a .torrent file URL.")
                    return@message
                }

                // Save URL in session
                UserSessionManager.setPendingTorrent(chatId, source = url, isFile = false)

                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "🎯 Got the link! What is this?",
                    replyMarkup = InlineKeyboardMarkup.create(
                        listOf(
                            InlineKeyboardButton.CallbackData("🎬 Movie", "torrent_category::movie"),
                            InlineKeyboardButton.CallbackData("📺 TV Series", "torrent_category::series")
                        )
                    )
                )

                UserSessionManager.setState(chatId, State.AwaitingTorrentCategorySelection)
            }
            else -> {
                bot.sendMessage(ChatId.fromId(chatId), "🙃 Have no idea what you want from me! \nMaybe, let\'s start from /menu again?")
            }
        }
    }
}

fun CallbackQueryHandlerEnvironment.handleSearchInput(query: String, chatId: Long, category: TorrentCategory, tracker: TrackerSource) {
    coroutineScope.launch {
        try {
            val results = tracker.search(query)

            if (results.isEmpty()) {
                bot.sendMessage(ChatId.fromId(chatId), "\uD83D\uDE15 Nothing found for: \"$query\"")
                return@launch
            }

            val displayText = buildString {
                append("\uD83D\uDD0E <b>Found results for</b> \"<i>$query</i>\":\n\n")
                results.take(7).forEachIndexed { index, item ->
                    val emoji = extractTagEmoji(item.displayString)
                    val displayLine = item.displayString.replace(item.pageUrl, "<a href=\"${item.pageUrl}\">\uD83C\uDF10 Webpage</a>")
                    append("<b>${index + 1}.</b> $emoji ${displayLine.replace("|", "\n\u2003\u2003<b>•</b>")}\n\n")
                }
            }

            val buttons = results.take(7).mapIndexed { i, _ ->
                listOf(InlineKeyboardButton.CallbackData("${i + 1}", "select::$i::$category"))
            }.toMutableList().apply {
                add(
                    listOf(
                        InlineKeyboardButton.CallbackData("\uD83C\uDF10 Get Raw Search Results", "show_tracker_search_results"),
                        InlineKeyboardButton.CallbackData("\uD83D\uDD19 Back to menu", "back_to_menu")
                    )
                )
            }

            UserSessionManager.setSearchResults(chatId, results)

            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = displayText,
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            bot.sendMessage(ChatId.fromId(chatId), "\u274C Failed to search. ${e.message}")
        }
    }
}


