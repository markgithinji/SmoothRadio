package com.smoothradio.radio.service

object MetadataUtils {
    fun extractSongTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()
        if (trimmed.contains("<LogEvent") && trimmed.contains("Type=\"SONG\"")) {
            return try {
                val songPattern = Regex(
                    """<LogEvent[^>]*Type="SONG"[^>]*LastStarted="true"[^>]*>.*?<Asset[^>]*Title="([^"]*)"[^>]*Artist1="([^"]*)"[^>]*/>.*?</LogEvent>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                val match = songPattern.find(trimmed)
                if (match != null) {
                    val title = match.groupValues[1].replace("&amp;", "&").trim()
                    val artist = match.groupValues[2].replace("&amp;", "&").trim()
                    if (title.isNotEmpty()) "$title - $artist" else ""
                } else {
                    val fallbackPattern =
                        Regex(
                            """<LogEvent[^>]*Type="SONG"[^>]*>.*?<Asset[^>]*Title="([^"]*)"[^>]*/>""",
                            RegexOption.DOT_MATCHES_ALL
                        )
                    val fallbackMatch = fallbackPattern.find(trimmed)
                    fallbackMatch?.groupValues?.get(1)?.replace("&amp;", "&")?.trim() ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }
        val cleanTitle = trimmed.replace(Regex("<[^>]*>"), "")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return if (cleanTitle.isNotEmpty() && cleanTitle != "-") cleanTitle else ""
    }
}