package com.santyramos.mirador.download

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.data.db.AppDatabase
import com.santyramos.mirador.data.db.DownloadEntity
import com.santyramos.mirador.data.db.DownloadStatus
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cola de descargas. Vive mientras el servicio en primer plano ([DownloadService]) esté activo,
 * así que las descargas no se cortan con la pantalla apagada.
 */
object DownloadCenter {
    private const val TAG = "DownloadCenter"
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trabajos = ConcurrentHashMap<Long, Job>()
    private val candado = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val db get() = AppDatabase.get(app)
    private val dao get() = db.descargas()
    val settings get() = Settings(app)

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch {
            // Si la app se cerró en medio de una descarga, esas descargas vuelven a la cola.
            dao.devolverEnCursoALaCola()
            if (dao.siguienteEnCola() != null) arrancarServicio()
        }
    }

    fun observarTodas() = dao.observarTodas()

    // ---------- Acciones del usuario ----------

    suspend fun encolar(info: MediaInfo, preset: Preset, sinLista: Boolean = true): Long {
        val directo = info.directos.isNotEmpty()
        val d = DownloadEntity(
            url = info.url,
            titulo = info.titulo,
            miniatura = info.miniatura,
            autor = info.autor,
            preset = (if (directo) Preset.IMAGENES else preset).name,
            sinLista = sinLista,
            extraJson = if (directo) json.encodeToString(info.directos) else null,
        )
        val id = dao.insertar(d)
        arrancarServicio()
        return id
    }

    suspend fun pausar(id: Long) {
        dao.cambiarEstado(id, DownloadStatus.PAUSED.name)
        detener(id)
    }

    suspend fun reanudar(id: Long) { dao.cambiarEstado(id, DownloadStatus.QUEUED.name); arrancarServicio() }

    suspend fun cancelar(id: Long) {
        dao.cambiarEstado(id, DownloadStatus.CANCELED.name)
        detener(id)
        File(app.cacheDir, "dl/$id").deleteRecursively()
        Notifications.cancelarResultado(app, id)
    }

    suspend fun reintentar(id: Long) {
        val d = dao.buscar(id) ?: return
        dao.actualizar(d.copy(estado = DownloadStatus.QUEUED.name, error = null, progreso = 0f, etaSeg = 0, velocidadBps = 0))
        Notifications.cancelarResultado(app, id)
        arrancarServicio()
    }

    suspend fun borrar(id: Long) {
        cancelar(id)
        dao.borrar(id)
    }

    suspend fun limpiarTerminadas() = dao.limpiarTerminadas()

    private fun detener(id: Long) {
        YtDlpEngine.detener(id)
        trabajos[id]?.cancel()
    }

    // ---------- Servicio y cola ----------

    fun arrancarServicio() {
        try {
            ContextCompat.startForegroundService(app, android.content.Intent(app, DownloadService::class.java))
        } catch (e: Throwable) {
            // Android no permite arrancarlo desde segundo plano en algunos casos; se reintenta al abrir la app.
            Log.w(TAG, "No se pudo iniciar el servicio de descargas", e)
        }
    }

    /** Lanza descargas de la cola hasta llenar el límite de simultáneas. Lo llama el servicio. */
    fun bombear() {
        scope.launch {
            candado.withLock {
                val limite = settings.simultaneas.first()
                while (trabajos.size < limite) {
                    val siguiente = dao.siguienteEnCola() ?: break
                    dao.cambiarEstado(siguiente.id, DownloadStatus.RUNNING.name)
                    trabajos[siguiente.id] = scope.launch { ejecutar(siguiente.copy(estado = DownloadStatus.RUNNING.name)) }
                }
            }
        }
    }

    fun hayTrabajo(): Boolean = trabajos.isNotEmpty()

    // ---------- Ejecución ----------

    private suspend fun ejecutar(d: DownloadEntity) {
        val dir = File(app.cacheDir, "dl/${d.id}")
        var ultimoGuardado = 0L
        var actual = d
        suspend fun guardarProgreso(av: Avance?, procesando: Boolean) {
            val ahora = System.currentTimeMillis()
            if (ahora - ultimoGuardado < 700 && !procesando) return
            ultimoGuardado = ahora
            val previo = dao.buscar(d.id) ?: return
            if (previo.status != DownloadStatus.RUNNING) return
            actual = if (procesando) previo.copy(progreso = 99f, velocidadBps = 0, etaSeg = 0)
            else previo.copy(
                progreso = av!!.porcentaje, bytesDescargados = av.descargados, bytesTotales = av.totales,
                velocidadBps = av.velocidadBps, etaSeg = av.etaSeg,
            )
            dao.actualizar(actual)
        }

        try {
            val archivos: List<File> = if (d.extraJson != null) {
                descargarDirectos(d, dir) { frac -> guardarProgreso(Avance((frac * 1000).toLong(), 1000, 0, 0), false) }
            } else {
                // yt-dlp nos avisa desde otro hilo; lo pasamos a una corrutina.
                YtDlpEngine.descargar(d, dir, Cookies.archivo(app)) { av, proc -> scope.launch { guardarProgreso(av, proc) } }
            }
            if (archivos.isEmpty()) throw IllegalStateException("No hay video en este enlace")
            var primero: MediaStoreSaver.Guardado? = null
            for (f in archivos) {
                val g = MediaStoreSaver.guardar(app, f)
                if (primero == null) primero = g
            }
            val fin = (dao.buscar(d.id) ?: d).copy(
                estado = DownloadStatus.DONE.name, progreso = 100f, velocidadBps = 0, etaSeg = 0, error = null,
                archivoUri = primero!!.uri.toString(), archivoMime = primero.mime, terminadaEn = System.currentTimeMillis(),
            )
            dao.actualizar(fin)
            Notifications.lista(app, fin, primero.uri, primero.mime)
        } catch (e: Throwable) {
            val estadoActual = dao.buscar(d.id)?.status
            if (e is YtDlpEngine.Cancelada || e is CancellationException || estadoActual == DownloadStatus.PAUSED || estadoActual == DownloadStatus.CANCELED) {
                // Pausada o cancelada por el usuario: el estado ya quedó guardado.
                if (estadoActual == DownloadStatus.CANCELED) dir.deleteRecursively()
            } else {
                Log.w(TAG, "Descarga ${d.id} falló", e)
                val motivo = ErrorMapper.mensaje(e)
                val falla = (dao.buscar(d.id) ?: d).copy(estado = DownloadStatus.ERROR.name, error = motivo, velocidadBps = 0, etaSeg = 0)
                dao.actualizar(falla)
                Notifications.error(app, falla, motivo)
            }
        } finally {
            val terminada = dao.buscar(d.id)?.status
            if (terminada == DownloadStatus.DONE || terminada == DownloadStatus.ERROR) dir.deleteRecursively()
            trabajos.remove(d.id)
            bombear()
        }
    }

    private suspend fun descargarDirectos(d: DownloadEntity, dir: File, alAvanzar: suspend (Float) -> Unit): List<File> =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val lista = json.decodeFromString<List<ArchivoDirecto>>(d.extraJson!!)
            val salida = mutableListOf<File>()
            lista.forEachIndexed { i, a ->
                val rb = Request.Builder().url(a.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36")
                a.referer?.let { rb.header("Referer", it) }
                Http.client.newCall(rb.build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP Error ${r.code}")
                    val total = r.body.contentLength()
                    val f = File(dir, UrlTools.nombreSeguro(a.nombre, 120))
                    f.outputStream().use { out ->
                        r.body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var leidos = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                leidos += n
                                if (total > 0) alAvanzar((i + leidos.toFloat() / total) / lista.size)
                            }
                        }
                    }
                    salida += f
                }
                alAvanzar((i + 1f) / lista.size)
            }
            salida
        }
}
