package com.santyramos.mirador

import com.santyramos.mirador.extractor.StreamSelector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

class AudioTest {
    private fun pista(id: String, tipo: AudioTrackType?, kbps: Int, idioma: String) = AudioStream.Builder()
        .setId("$id-$kbps").setContent("https://x/$id/$kbps", true).setMediaFormat(MediaFormat.M4A)
        .setAverageBitrate(kbps).setAudioTrackId(id).setAudioTrackType(tipo).setAudioLocale(Locale.forLanguageTag(idioma))
        .build()

    private val original = pista("es.4", AudioTrackType.ORIGINAL, 128, "es")
    private val dobladoMejor = pista("hi.3", AudioTrackType.DUBBED, 160, "hi")

    @Test fun prefiereLaOriginalAunqueElDobladoTengaMasCalidad() {
        assertEquals(original, StreamSelector.elegirAudio(listOf(dobladoMejor, original)))
    }

    @Test fun respetaElIdiomaElegido() {
        assertEquals(dobladoMejor, StreamSelector.elegirAudio(listOf(dobladoMejor, original), "hi.3"))
    }

    @Test fun listaDeIdiomasConLaOriginalPrimero() {
        val p = StreamSelector.pistas(listOf(dobladoMejor, original))
        assertEquals(listOf("es.4", "hi.3"), p.map { it.id })
        assertEquals(true, p[0].original)
    }
}
