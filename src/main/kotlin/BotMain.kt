import bot.auth.BotAuthUtil
import bot.auth.authorizedCommand
import bot.env.secret
import bot.source.MazepaTracker
import bot.source.TrackerSource
import bot.state.State
import bot.state.UserSessionManager
import bot.util.extractTagEmoji
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

            authorizedCommand("menu") {
                menu()
            }

            callbackQuery("back_to_menu") {
                menu()
            }

            callbackQuery("download_movie") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                UserSessionManager.setState(chatId, State.AwaitingMovieName)
                bot.sendMessage(ChatId.fromId(chatId), "🎬 Enter movie name or kinobaza.com.ua link:")
            }

            callbackQuery("download_series") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                UserSessionManager.setState(chatId, State.AwaitingTVSeriesName)
                bot.sendMessage(ChatId.fromId(chatId), "📺 Enter TV series name or kinobaza.com.ua link:")
            }

            callbackQuery("select_tracker") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                val trackerName = callbackQuery.data.split("::").getOrNull(1) ?: return@callbackQuery
                val tracker = trackers.find { it.name == trackerName } ?: run {
                    bot.sendMessage(ChatId.fromId(chatId), "\u274C Tracker not found.")
                    return@callbackQuery
                }

                val queryContext = UserSessionManager.getPendingQuery(chatId)
                if (queryContext == null) {
                    bot.sendMessage(ChatId.fromId(chatId), "\u274C No query found. Try again from /menu.")
                    UserSessionManager.setState(chatId, State.Idle)
                    return@callbackQuery
                }

                handleSearchInput(queryContext.query, chatId, queryContext.savePath, tracker)
                UserSessionManager.setState(chatId, State.Idle)
            }

            callbackQuery("show_tracker_search_results") {
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
                val results = UserSessionManager.getSearchResults(chatId)

                if (results.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(chatId), "❌ No previous search found.")
                    return@callbackQuery
                }

                val url = results.first().searchQueryUsed

                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "🌐 Raw search link:\n<a href=\"$url\">$url</a>",
                    parseMode = ParseMode.HTML
                )
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
        )
    )

    bot.sendMessage(
        chatId = ChatId.fromId(chatId),
        text = "📋 Main Menu:\nWhat would you like to download?",
        replyMarkup = buttons
    )
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
                UserSessionManager.setPendingQuery(chatId, text, savePath = if (state is State.AwaitingMovieName) "/movies" else "/shows")

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
            else -> {}
        }
    }
}

fun CallbackQueryHandlerEnvironment.handleSearchInput(query: String, chatId: Long, savePath: String, tracker: TrackerSource) {
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
                listOf(InlineKeyboardButton.CallbackData("${i + 1}", "select::$i::$savePath"))
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


