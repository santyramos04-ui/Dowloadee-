package com.santyramos.mirador.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.santyramos.mirador.BuildConfig
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.File

data class VersionNueva(val version: String, val notas: String, val urlApk: String, val tamano: Long)

/** Avisa de versiones nuevas leyendo GitHub Releases y permite descargarlas e instalarlas. */
object AppUpdater {
    sealed interface Estado {
        data object Nada : Estado
        data object Buscando : Estado
        data class AlDia(val version: String) : Estado
        data class Disponible(val nueva: VersionNueva) : Estado
        data class Descargando(val nueva: VersionNueva, val fraccion: Float) : Estado
        data class ListaParaInstalar(val nueva: VersionNueva, val archivo: File) : Estado
        data class Error(val mensaje: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Nada)
    val estado: StateFlow<Estado> = _estado.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }

    /** "v1.0.57" -> [1,0,57]. Sirve para comparar versiones. */
    internal fun partes(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').mapNotNull { it.toIntOrNull() }

    internal fun esMasNueva(remota: String, local: String): Boolean {
        val a = partes(remota); val b = partes(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    internal fun interpretar(texto: String, versionLocal: String): VersionNueva? {
        val o = json.parseToJsonElement(texto).jsonObject
        val tag = (o["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (!esMasNueva(tag, versionLocal)) return null
        val assets = o["assets"] as? JsonArray ?: return null
        val apk = assets.mapNotNull { it as? JsonObject }.firstOrNull { ((it["name"] as? JsonPrimitive)?.contentOrNull ?: "").endsWith(".apk") } ?: return null
        return VersionNueva(
            version = tag.removePrefix("v"),
            notas = (o["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            urlApk = (apk["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return null,
            tamano = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    }

    suspend fun buscar(context: Context, silencioso: Boolean = false) {
        if (!silencioso) _estado.value = Estado.Buscando
        try {
            val nueva = withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                    .header("Accept", "application/vnd.github+json").build()
                Http.client.newCall(req).execute().use { r ->
                    when {
                        r.code == 404 -> throw IllegalStateException("No encontré versiones publicadas. (Si el repositorio es privado, la app no puede leerlo: debe ser público.)")
                        !r.isSuccessful -> throw IllegalStateException("GitHub respondió ${r.code}")
                        else -> interpretar(r.body.string(), BuildConfig.VERSION_NAME)
                    }
                }
            }
            _estado.value = if (nueva != null) Estado.Disponible(nueva) else Estado.AlDia(BuildConfig.VERSION_NAME)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!silencioso) _estado.value = Estado.Error(e.message ?: "No se pudo comprobar si hay una versión nueva")
            else if (_estado.value is Estado.Buscando) _estado.value = Estado.Nada
        }
    }

    suspend fun descargar(context: Context, nueva: VersionNueva) = withContext(Dispatchers.IO) {
        try {
            _estado.value = Estado.Descargando(nueva, 0f)
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val destino = File(dir, "Mirador-${nueva.version}.apk")
            Http.client.newCall(Request.Builder().url(nueva.urlApk).build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("La descarga falló (${r.code})")
                val total = r.body.contentLength().takeIf { it > 0 } ?: nueva.tamano
                destino.outputStream().use { out ->
                    r.body.byteStream().use { input ->
                        val buf = ByteArray(128 * 1024); var leidos = 0L; var ultimo = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); leidos += n
                            val ahora = System.currentTimeMillis()
                            if (total > 0 && ahora - ultimo > 300) { ultimo = ahora; _estado.value = Estado.Descargando(nueva, leidos.toFloat() / total) }
                        }
                    }
                }
            }
            _estado.value = Estado.ListaParaInstalar(nueva, destino)
        } catch (e: Throwable) {
            _estado.value = Estado.Error(com.santyramos.mirador.download.ErrorMapper.mensaje(e))
        }
    }

    /** Abre el instalador de Android. La clave de firma es la misma, así que se instala encima. */
    fun instalar(context: Context, archivo: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
