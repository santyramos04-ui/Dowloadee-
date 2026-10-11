package com.santyramos.mirador

import com.santyramos.mirador.update.AppUpdater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkCompletoTest {
    private fun apkFalso(): File = File.createTempFile("prueba", ".apk").apply {
        ZipOutputStream(outputStream()).use { z -> z.putNextEntry(ZipEntry("AndroidManifest.xml")); z.write(ByteArray(100)); z.closeEntry() }
        deleteOnExit()
    }

    @Test fun apkEnteroSeAcepta() {
        val f = apkFalso()
        assertTrue(AppUpdater.apkCompleto(f, f.length()))
        assertTrue(AppUpdater.apkCompleto(f, 0)) // sin tamaño conocido, basta con que abra
    }

    @Test fun apkCortadoSeRechaza() {
        val f = apkFalso()
        val cortado = File.createTempFile("cortado", ".apk").apply { writeBytes(f.readBytes().copyOf((f.length() / 2).toInt())); deleteOnExit() }
        assertFalse(AppUpdater.apkCompleto(cortado, f.length()))
        assertFalse(AppUpdater.apkCompleto(cortado, 0))
    }
}
