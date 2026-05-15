package com.smoothradio.radio.service

object MetadataUtils {
    fun extractSongTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()

        // 1. Specific logic for LogEvent SONG packets
        if (trimmed.contains("<LogEvent") && trimmed.contains("Type=\"SONG\"")) {
            try {
                val songPattern = Regex(
                    """<LogEvent[^>]*Type="SONG"[^>]*LastStarted="true"[^>]*>.*?<Asset[^>]*Title="([^"]*)"[^>]*Artist1="([^"]*)"[^>]*/>.*?</LogEvent>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                val match = songPattern.find(trimmed)
                if (match != null) {
                    val title = match.groupValues[1].replace("&amp;", "&").trim()
                    val artist = match.groupValues[2].replace("&amp;", "&").trim()
                    if (title.isNotEmpty()) return "$title - $artist"
                } else {
                    val fallbackPattern = Regex("""<Asset[^>]*Title="([^"]*)"[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
                    val fallbackMatch = fallbackPattern.find(trimmed)
                    if (fallbackMatch != null) {
                        return fallbackMatch.groupValues[1].replace("&amp;", "&").trim()
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 2. Generic logic to find Title="..." in any XML-like tag (even if malformed or non-SONG)
        if (trimmed.contains("Title=\"")) {
            val titleMatch = Regex("""Title="([^"]*)"""").find(trimmed)
            if (titleMatch != null) {
                val title = titleMatch.groupValues[1].replace("&amp;", "&").trim()
                if (title.isNotEmpty()) return title
            }
        }

        // 3. Fallback: Strip all tags and clean up whitespace
        // Handles cases where no Title attribute is found but there is plain text
        val cleanTitle = trimmed.replace(Regex("<[^>]*>?"), "") // Added ? to handle unclosed tags
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return if (cleanTitle.isNotEmpty() && cleanTitle != "-") cleanTitle else ""
    }
}