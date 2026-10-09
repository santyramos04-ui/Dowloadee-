package com.santyramos.mirador

import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.extractor.StreamSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamSelectorTest {
    // En la app real el alto, el códec y el bitrate vienen de la respuesta de YouTube; aquí los ponemos a mano.
    private fun itag(id: Int): ItagItem {
        val item = ItagItem(ItagItem.getItag(id))
        val alto = item.resolutionString?.filter { it.isDigit() }?.toIntOrNull()
        if (alto != null) {
            item.height = alto
            item.bitrate = alto * 3000
            item.codec = if (id == 248) "vp09.00.40.08" else "avc1.640028"
        }
        return item
    }

    private fun video(itag: Int, soloVideo: Boolean): VideoStream {
        val item = itag(itag)
        return VideoStream.Builder().setId(itag.toString()).setContent("https://v/$itag", true)
            .setMediaFormat(item.mediaFormat).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(soloVideo).setResolution(item.resolutionString ?: "").setItagItem(item).build()
    }

    private fun audio(itag: Int): AudioStream {
        val item = itag(itag)
        return AudioStream.Builder().setId(itag.toString()).setContent("https://a/$itag", true)
            .setMediaFormat(item.mediaFormat).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(item.averageBitrate).setItagItem(item).build()
    }

    // 137: 1080p H.264 | 248: 1080p VP9 | 136: 720p H.264 | 135: 480p H.264 | 22: 720p con audio | 18: 360p con audio
    private val solo = listOf(video(137, true), video(248, true), video(136, true), video(135, true))
    private val mezclado = listOf(video(22, false), video(18, false))
    private val audios = listOf(audio(251), audio(140))

    @Test fun autoConWifiElige1080ConH264() {
        val s = StreamSelector.seleccionar(mezclado, solo, audios, PlayerQuality.AUTO, conWifi = true)!!
        assertEquals(1080, s.altura)
        assertEquals("137", s.video.id)
        assertNotNull(s.audio)
        assertEquals("140", s.audio!!.id) // prefiere m4a
    }

    @Test fun autoConDatosElige720() {
        val s = StreamSelector.seleccionar(mezclado, solo, audios, PlayerQuality.AUTO, conWifi = false)!!
        assertEquals(720, s.altura)
    }

    @Test fun elige480() {
        val s = StreamSelector.seleccionar(mezclado, solo, audios, PlayerQuality.P480, conWifi = true)!!
        assertEquals(480, s.altura)
    }

    @Test fun siSoloHayMezcladoNoPideAudioAparte() {
        val s = StreamSelector.seleccionar(mezclado, emptyList(), audios, PlayerQuality.P720, conWifi = true)!!
        assertEquals("22", s.video.id)
        assertNull(s.audio)
    }

    @Test fun sinStreamsDevuelveNull() {
        assertNull(StreamSelector.seleccionar(emptyList(), emptyList(), audios, PlayerQuality.AUTO, true))
    }

    @Test fun alturasDisponibles() {
        assertEquals(listOf(1080, 720, 480, 360), StreamSelector.alturasDisponibles(solo + mezclado))
    }
}
