package com.santyramos.mirador

import com.santyramos.mirador.extractor.Youtube
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeService

class IdiomaTest {
    @Test fun eligeUnEspanolQueYoutubeReconoce() {
        val soportados = YoutubeService(0).supportedLocalizations
        val elegido = Youtube.espanol(soportados)
        assertEquals("es", elegido.languageCode)
        assertEquals(true, elegido in soportados)
    }

    @Test fun sinListaUsaEspanol() {
        assertEquals("es", Youtube.espanol(emptyList()).languageCode)
        assertEquals("es-419", Youtube.espanol(listOf(Localization("en", "GB"), Localization("es"), Localization("es", "419"))).localizationCode)
    }
}
