package com.santyramos.mirador

import com.santyramos.mirador.download.ErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenoTest {
    @Test fun elFrenoDeYoutubeNoEsUnVideoBorrado() {
        val m = ErrorMapper.mensaje("ERROR: [youtube] abc: Video unavailable. This content isn't available, try again later.")
        assertEquals(ErrorMapper.FRENO, m)
        assertTrue(ErrorMapper.esTemporal(m) && ErrorMapper.pidePausa(m))
    }

    @Test fun unVideoBorradoNoSeReintenta() {
        val m = ErrorMapper.mensaje("ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader")
        assertTrue(m.startsWith("El contenido ya no está disponible"))
        assertFalse(ErrorMapper.esTemporal(m))
    }
}
