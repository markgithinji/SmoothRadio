package com.smoothradio.radio.service

import com.google.common.truth.Truth
import org.junit.Test

class MetadataUtilsTest {

    @Test
    fun extractSongTitle_withXmlSongEvent_returnsTitleAndArtist() {
        val raw = """
            <LogEvent Type="SONG" LastStarted="true">
                <Asset Title="Blinding Lights" Artist1="The Weeknd" />
            </LogEvent>
        """.trimIndent()

        val result = MetadataUtils.extractSongTitle(raw)
        Truth.assertThat(result).isEqualTo("Blinding Lights - The Weeknd")
    }

    @Test
    fun extractSongTitle_withXmlAndHtmlEntities_replacesAmpersand() {
        val raw = """
            <LogEvent Type="SONG" LastStarted="true">
                <Asset Title="Rock &amp; Roll" Artist1="Led &amp; Zeppelin" />
            </LogEvent>
        """.trimIndent()

        val result = MetadataUtils.extractSongTitle(raw)
        Truth.assertThat(result).isEqualTo("Rock & Roll - Led & Zeppelin")
    }

    @Test
    fun extractSongTitle_withFallbackXml_returnsTitle() {
        val raw = """
            <LogEvent Type="SONG">
                <Asset Title="Only Title" />
            </LogEvent>
        """.trimIndent()

        val result = MetadataUtils.extractSongTitle(raw)
        Truth.assertThat(result).isEqualTo("Only Title")
    }

    @Test
    fun extractSongTitle_withPlainHtml_stripsTags() {
        val raw = "<html><body><b>Song Name</b> - Artist Name</body></html>"
        val result = MetadataUtils.extractSongTitle(raw)
        Truth.assertThat(result).isEqualTo("Song Name - Artist Name")
    }

    @Test
    fun extractSongTitle_withNewLines_replacesWithSpace() {
        val raw = "Song\nName\r- Artist"
        val result = MetadataUtils.extractSongTitle(raw)
        Truth.assertThat(result).isEqualTo("Song Name - Artist")
    }

    @Test
    fun extractSongTitle_emptyOrDash_returnsEmpty() {
        Truth.assertThat(MetadataUtils.extractSongTitle("")).isEmpty()
        Truth.assertThat(MetadataUtils.extractSongTitle("-")).isEmpty()
        Truth.assertThat(MetadataUtils.extractSongTitle("   ")).isEmpty()
    }
}