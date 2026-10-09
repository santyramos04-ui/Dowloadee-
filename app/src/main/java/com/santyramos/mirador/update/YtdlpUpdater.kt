package com.santyramos.mirador.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.download.YtDlpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Actualiza el motor yt-dlp desde la propia app (sin esperar un APK nuevo):
 * automático una vez al día y bajo demanda desde Ajustes.
 */
object YtdlpUpdater {
    sealed interface Estado {
        data object Inactivo : Estado
        data object Actualizando : Estado
        data class Resultado(val mensaje: String, val ok: Boolean) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Inactivo)
    val estado: StateFlow<Estado> = _estado.asStateFlow()
    private const val DIA_MS = 20L * 60 * 60 * 1000 // 20 h: así cae "una vez al día" aunque Android retrase la tarea

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    suspend fun refrescarVersion(context: Context) { _version.value = YtDlpEngine.versionInstalada(context) }

    suspend fun actualizarAhora(context: Context): Boolean {
        if (_estado.value is Estado.Actualizando) return false
        _estado.value = Estado.Actualizando
        val ajustes = Settings(context.applicationContext)
        val r = YtDlpEngine.actualizar(context, ajustes.ytdlpCanal.first())
        ajustes.marcarIntentoYtdlp(System.currentTimeMillis())
        return when (r) {
            is YtDlpEngine.ResultadoActualizacion.Actualizado -> {
                _version.value = r.version
                _estado.value = Estado.Resultado("Motor actualizado a la versión ${r.version ?: "más reciente"}.", true); true
            }
            is YtDlpEngine.ResultadoActualizacion.YaAlDia -> {
                _version.value = r.version
                _estado.value = Estado.Resultado("Ya tienes la versión más reciente (${r.version ?: "—"}).", true); true
            }
            is YtDlpEngine.ResultadoActualizacion.Error -> {
                _estado.value = Estado.Resultado("No se pudo actualizar: ${r.mensaje}", false); false
            }
        }
    }

    /** Al abrir la app: si está activado el modo automático y pasó casi un día, actualiza. */
    suspend fun actualizarSiToca(context: Context) {
        val ajustes = Settings(context.applicationContext)
        if (!ajustes.ytdlpAuto.first()) return
        val ultimo = ajustes.ytdlpUltimoIntento()
        val nunca = ultimo == 0L
        if (nunca || System.currentTimeMillis() - ultimo > DIA_MS) actualizarAhora(context)
    }

    /** Respaldo: una tarea periódica del sistema por si la app no se abre en días. */
    fun programar(context: Context) {
        val req = PeriodicWorkRequestBuilder<Tarea>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ytdlp-actualizar", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    class Tarea(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
        override suspend fun doWork(): Result {
            YtDlpEngine.iniciar(applicationContext)
            val ajustes = Settings(applicationContext)
            if (!ajustes.ytdlpAuto.first()) return Result.success()
            return if (actualizarAhora(applicationContext)) Result.success() else Result.retry()
        }
    }
}
