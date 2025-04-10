import bot.auth.BotAuthUtil
import bot.auth.authorizedCommand
import bot.env.secret
import bot.source.MazepaTracker
import bot.state.State
import bot.state.UserSessionManager
import bot.util.extractTagEmoji
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
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

val tracker = MazepaTracker()

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
                val chatId = message.chat.id
                val buttons = InlineKeyboardMarkup.create(
                    listOf(
                        InlineKeyboardButton.CallbackData("🎬 Download Movie", "download_movie"),
                        InlineKeyboardButton.CallbackData("📺 Download TV Series", "download_series")
                    )
                )
                bot.sendMessage(chatId = ChatId.fromId(chatId), text = "What would you like to download?", replyMarkup = buttons)
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
        }
    }

    bot.startPolling()
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
            is State.AwaitingMovieName -> {
                handleSearchInput(text, chatId, savePath = "/movies")
                UserSessionManager.setState(chatId, State.Idle)
            }
            is State.AwaitingTVSeriesName -> {
                handleSearchInput(text, chatId, savePath = "/shows")
                UserSessionManager.setState(chatId, State.Idle)
            }
        }
    }
}

fun MessageHandlerEnvironment.handleSearchInput(query: String, chatId: Long, savePath: String) {
    coroutineScope.launch {
        try {
            val results = tracker.search(query)

            if (results.isEmpty()) {
                bot.sendMessage(ChatId.fromId(chatId), "😕 Nothing found for: \"$query\"")
                return@launch
            }

            val displayText = buildString {
                append("🔎 <b>Found results for</b> \"<i>$query</i>\":\n\n")
                results.take(10).forEachIndexed { index, item ->
                    val emoji = extractTagEmoji(item.displayString)
                    // Replace the URL with an <a href="...">Webpage</a>
                    val displayLine = item.displayString.replace(item.pageUrl, "<a href=\"${item.pageUrl}\">🌐 Webpage</a>")
                    append("<b>${index + 1}.</b> $emoji ${displayLine.replace("|", "\n  <b>•</b>")}\n\n")
                }
            }

            val buttons = results.take(10).mapIndexed { i, _ ->
                listOf(InlineKeyboardButton.CallbackData("${i + 1}", "select::$i::$savePath"))
            }

            // Save for later selection
//            UserSessionManager.setSearchResults(chatId, results)

            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = displayText,
                parseMode = ParseMode.HTML,
                replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            bot.sendMessage(ChatId.fromId(chatId), "❌ Failed to search. ${e.message}")
        }
    }
}


