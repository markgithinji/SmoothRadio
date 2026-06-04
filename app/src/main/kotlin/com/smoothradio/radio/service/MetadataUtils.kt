package com.smoothradio.radio.service

object MetadataUtils {
    fun extractSongTitle(rawTitle: String): String {
        val trimmed = rawTitle.trim()

        // 1. Specific logic for LogEvent SONG packets (Zetta/RCS format)
        if (trimmed.contains("<LogEvent") && trimmed.contains("Type=\"SONG\"")) {
            try {
                // Find the active SONG LogEvent
                val songPattern = Regex(
                    """<LogEvent[^>]*Type="SONG"[^>]*LastStarted="true"[^>]*>.*?(<Asset[^>]*/>).*?</LogEvent>""",
                    RegexOption.DOT_MATCHES_ALL
                )
                val match = songPattern.find(trimmed)
                val assetTag = match?.groupValues?.get(1) ?: trimmed // Fallback to searching the whole string
                
                val titleMatch = Regex("""Title="([^"]*)"""").find(assetTag)
                val artistMatch = Regex("""Artist1="([^"]*)"""").find(assetTag)
                
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: ""
                val artist = artistMatch?.groupValues?.get(1)?.trim() ?: ""
                
                if (title.isNotEmpty() && artist.isNotEmpty()) return decodeHtmlEntities("$title - $artist")
                if (title.isNotEmpty()) return decodeHtmlEntities(title)
            } catch (e: Exception) {
                // fall through
            }
        }

        // 2. Generic logic to find Title="..." in any XML-like tag (even if malformed or non-SONG)
        if (trimmed.contains("Title=\"")) {
            val titleMatch = Regex("""Title="([^"]*)"""").find(trimmed)
            if (titleMatch != null) {
                val title = titleMatch.groupValues[1].trim()
                if (title.isNotEmpty()) return decodeHtmlEntities(title)
            }
        }

        // 3. Fallback: Strip all tags and clean up whitespace
        // Handles cases where no Title attribute is found but there is plain text
        val cleanTitle = trimmed.replace(Regex("<[^>]*>?"), "") // Added ? to handle unclosed tags
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return if (cleanTitle.isNotEmpty() && cleanTitle != "-") decodeHtmlEntities(cleanTitle) else ""
    }

    private fun decodeHtmlEntities(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&amp", "&")
            .replace("&smp;", "&") // Handle user-reported &smp
            .replace("&smp", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}
