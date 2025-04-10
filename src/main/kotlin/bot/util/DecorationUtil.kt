package bot.util

fun extractTagEmoji(display: String): String {
    return when {
        "BDRemux" in display -> "💿"
        "WEB-DL" in display -> "🌐"
        "H.265" in display -> "⚙️"
        "HDR" in display -> "🌈"
        "SATRip" in display -> "📺"
        else -> "🎬"
    }
}