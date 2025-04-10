package bot.state

object UserSessionManager {
    private val sessions = mutableMapOf<Long, UserSession>()

    fun getSession(chatId: Long): UserSession =
        sessions.getOrPut(chatId) { UserSession(State.Idle) }

    fun setState(chatId: Long, state: State) {
        sessions.getOrPut(chatId) { UserSession(state) }.state = state
    }

    fun clear(chatId: Long) {
        sessions.remove(chatId)
    }
}

data class UserSession(var state: State)

sealed class State {
    data object AuthPrompt : State()
    data object Idle : State()
    data object AwaitingMovieName : State()
    data object AwaitingTVSeriesName : State()
}
