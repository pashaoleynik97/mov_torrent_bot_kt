package bot.auth

import bot.env.secret
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.entities.ChatId

object BotAuthUtil {
    private val authorizedUsers = mutableSetOf<Long>()
    private val pendingAuth = mutableSetOf<Long>()

    fun markPending(chatId: Long) {
        pendingAuth.add(chatId)
    }

    fun unmarkPending(chatId: Long) {
        pendingAuth.remove(chatId)
    }

    fun needsPassword(chatId: Long): Boolean = chatId in pendingAuth

    fun authorize(password: String, chatId: Long): Boolean {
        val expected = secret.botPassword
        return if (password == expected) {
            authorizedUsers.add(chatId)
            unmarkPending(chatId)
            true
        } else {
            false
        }
    }

    fun isAuthorized(chatId: Long): Boolean = chatId in authorizedUsers
}

fun Dispatcher.authorizedCommand(
    command: String,
    onAuthFailed: CommandHandlerEnvironment.() -> Unit = {
        bot.sendMessage(
            chatId = ChatId.fromId(message.chat.id),
            text = "⛔ Who are you? o_O\nType /start and enter password"
        )
    },
    handleCommand: CommandHandlerEnvironment.() -> Unit
) {
    command(command) {
        val chatId = message.chat.id
        if (BotAuthUtil.isAuthorized(chatId)) {
            handleCommand()
        } else {
            onAuthFailed()
        }
    }
}

fun Dispatcher.authorizedCallbackQuery(
    callbackData: String? = null,
    callbackAnswerText: String? = null,
    callbackAnswerShowAlert: Boolean? = null,
    callbackAnswerUrl: String? = null,
    callbackAnswerCacheTime: Int? = null,
    onAuthFailed: CallbackQueryHandlerEnvironment.() -> Unit = {
        val chatId = callbackQuery.message?.chat?.id?.let { cId ->
            bot.sendMessage(
                chatId =ChatId.fromId(cId),
                text = "⛔ Who are you? o_O\nType /start and enter password"
            )
        }
    },
    handleCallbackQuery: HandleCallbackQuery,
) {
    callbackQuery(callbackData, callbackAnswerText, callbackAnswerShowAlert, callbackAnswerUrl, callbackAnswerCacheTime) {
        val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
        if (BotAuthUtil.isAuthorized(chatId)) {
            handleCallbackQuery()
        } else {
            onAuthFailed()
        }
    }
}