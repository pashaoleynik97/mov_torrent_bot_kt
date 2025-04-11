package bot.filter

import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.extensions.filters.Filter

object DocumentFilter : Filter {
    override fun Message.predicate(): Boolean = document != null
}