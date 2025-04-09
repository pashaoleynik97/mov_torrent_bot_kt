import bot.BotAuthUtil
import bot.authorizedCommand
import bot.secret
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.extensions.filters.Filter
import com.github.kotlintelegrambot.logging.LogLevel

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
                } else {
                    bot.sendMessage(chatId = ChatId.fromId(chatId), text = "🍻 You are in. Type /menu to access bot's features.")
                }
            }

            // Global text handler — NOT inside the command
            message(Filter.Text) {
                val chatId = message.chat.id
                val text = message.text ?: return@message

                if (BotAuthUtil.needsPassword(chatId)) {
                    if (BotAuthUtil.authorize(text, chatId)) {
                        bot.sendMessage(ChatId.fromId(chatId), "🍻 You are in! Type /menu to access bot's features.")
                    } else {
                        bot.sendMessage(ChatId.fromId(chatId), "❌ Incorrect password. Try again.")
                    }
                }
            }

            authorizedCommand("menu") {
                val buttons = InlineKeyboardMarkup.create(
                    listOf(
                        InlineKeyboardButton.CallbackData(text = "🎬 Download Movie", callbackData = "download_movie"),
                        InlineKeyboardButton.CallbackData(text = "🎵 Download Music", callbackData = "download_music")
                    )
                )

                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = "Choose an option:",
                    replyMarkup = buttons
                )

                callbackQuery("download_movie") {
                    bot.sendMessage(chatId = ChatId.fromId(callbackQuery.message!!.chat.id), text = "🎬 Movie download selected.")
                }

                callbackQuery("download_music") {
                    bot.sendMessage(chatId = ChatId.fromId(callbackQuery.message!!.chat.id), text = "🎵 Music download selected.")
                }
            }
        }
    }

    bot.startPolling()
}