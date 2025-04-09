package bot

class BotSecret private constructor(
    private val mBotToken: String,
    private val mBotPassword: String
) {

    val botToken: String
        get() = mBotToken

    val botPassword: String
        get() = mBotPassword

    companion object {

        private const val TG_BOT_TOKEN = "TG_BOT_TOKEN"
        private const val TG_BOT_PASSWORD = "TG_BOT_PASSWORD"

        private var INSTANCE : BotSecret? = null

        fun getInstance(): BotSecret {
            return INSTANCE ?: let {
                INSTANCE = BotSecret(
                    mBotToken = System.getenv(TG_BOT_TOKEN) ?: error("Please set TG_BOT_TOKEN env variable"),
                    mBotPassword = System.getenv(TG_BOT_PASSWORD) ?: error("Please set TG_BOT_PASSWORD env variable")
                )
                INSTANCE!!
            }
        }
    }

}

val secret: BotSecret
    get() = BotSecret.getInstance()