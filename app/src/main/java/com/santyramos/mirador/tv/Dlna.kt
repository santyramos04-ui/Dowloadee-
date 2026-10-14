package com.santyramos.mirador.tv

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** Un televisor (o equipo) que puede reproducir lo que le mandemos por DLNA/UPnP. */
data class Televisor(val nombre: String, val control: String, val ubicacion: String)

/**
 * DLNA/UPnP: el sistema que traen casi todos los Smart TV (Samsung, LG, Sony, Hisense, TCL, Android TV
 * con app DLNA…). Se le pasa la dirección del video y el televisor lo reproduce por su cuenta.
 */
object Dlna {
    private const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"

    /** Descubre televisores en el Wi-Fi (SSDP). Hace falta tener un «multicast lock» activo. */
    suspend fun buscar(tiempoMs: Long = 3500): List<Televisor> = withContext(Dispatchers.IO) {
        val ubicaciones = linkedSetOf<String>()
        DatagramSocket().use { s ->
            s.soTimeout = 400
            val destino = InetAddress.getByName("239.255.255.250")
            listOf(AVT, "urn:schemas-upnp-org:device:MediaRenderer:1").forEach { st ->
                val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n".toByteArray()
                repeat(2) { s.send(DatagramPacket(msg, msg.size, destino, 1900)) }
            }
            val fin = System.currentTimeMillis() + tiempoMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < fin) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    ubicacionDeRespuesta(String(p.data, 0, p.length))?.let { ubicaciones += it }
                } catch (_: SocketTimeoutException) { }
            }
        }
        ubicaciones.mapNotNull { u -> runCatching { describir(u) }.getOrNull() }.distinctBy { it.control }
    }

    internal fun ubicacionDeRespuesta(r: String): String? =
        r.lineSequence().firstOrNull { it.startsWith("location:", true) }?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

    private fun describir(ubicacion: String): Televisor? {
        val xml = Http.client.newCall(Request.Builder().url(ubicacion).build()).execute().use { it.body.string() }
        return desdeDescripcion(xml, ubicacion)
    }

    /** Lee el nombre del equipo y la dirección de control de AVTransport de su descripción XML. */
    internal fun desdeDescripcion(xml: String, ubicacion: String): Televisor? {
        val servicio = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1] }.firstOrNull { it.contains("AVTransport") } ?: return null
        val control = Regex("<controlURL>\\s*(.*?)\\s*</controlURL>").find(servicio)?.groupValues?.get(1) ?: return null
        val base = Regex("<URLBase>\\s*(.*?)\\s*</URLBase>").find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: ubicacion
        val nombre = Regex("<friendlyName>(.*?)</friendlyName>").find(xml)?.groupValues?.get(1)?.let(::desescapar) ?: "Televisor"
        return Televisor(nombre, URL(URL(base), control).toString(), ubicacion)
    }

    private fun desescapar(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    internal fun escapar(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun soap(tv: Televisor, accion: String, cuerpo: String) {
        val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$accion xmlns:u=\"$AVT\">$cuerpo</u:$accion></s:Body></s:Envelope>"
        val req = Request.Builder().url(tv.control)
            .header("SOAPACTION", "\"$AVT#$accion\"")
            .post(xml.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType())).build()
        Http.client.newCall(req).execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("El televisor rechazó la orden ($accion: ${r.code})") }
    }

    /** Metadatos DIDL-Lite: título y tipo de archivo (algunos televisores no reproducen sin esto). */
    internal fun metadatos(url: String, titulo: String, mime: String): String {
        val clase = if (mime.startsWith("audio/")) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escapar(titulo)}</dc:title><upnp:class>$clase</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$mime:*\">${escapar(url)}</res></item></DIDL-Lite>"
    }

    /** Manda el video al televisor y lo pone a sonar. */
    suspend fun enviar(tv: Televisor, url: String, titulo: String, mime: String) = withContext(Dispatchers.IO) {
        runCatching { soap(tv, "Stop", "<InstanceID>0</InstanceID>") }
        soap(
            tv, "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>${escapar(url)}</CurrentURI><CurrentURIMetaData>${escapar(metadatos(url, titulo, mime))}</CurrentURIMetaData>",
        )
        soap(tv, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun pausar(tv: Televisor) = withContext(Dispatchers.IO) { soap(tv, "Pause", "<InstanceID>0</InstanceID>") }
    suspend fun reanudar(tv: Televisor) = withContext(Dispatchers.IO) { soap(tv, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>") }
    suspend fun detener(tv: Televisor) = withContext(Dispatchers.IO) { runCatching { soap(tv, "Stop", "<InstanceID>0</InstanceID>") } }
}
