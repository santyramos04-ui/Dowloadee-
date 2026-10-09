package com.santyramos.mirador.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ajustes")

/** Calidades del reproductor. */
enum class PlayerQuality(val etiqueta: String, val alturaMax: Int) {
    AUTO("Auto", 0), P1080("1080p", 1080), P720("720p", 720), P480("480p", 480);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Canal de actualización de yt-dlp. */
enum class YtdlpChannel(val etiqueta: String) {
    NIGHTLY("Nightly (se arregla antes cuando un sitio cambia)"),
    STABLE("Estable (más probado, se arregla más tarde)");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: NIGHTLY
    }
}

class Settings(private val context: Context) {
    private val ds get() = context.dataStore

    private object K {
        val simultaneas = intPreferencesKey("descargas_simultaneas")
        val calidad = stringPreferencesKey("calidad_reproductor")
        val velocidad = stringPreferencesKey("velocidad_reproduccion")
        val sponsorBlock = booleanPreferencesKey("sponsorblock")
        val ytdlpAuto = booleanPreferencesKey("ytdlp_auto")
        val ytdlpCanal = stringPreferencesKey("ytdlp_canal")
        val ytdlpUltimoIntento = longPreferencesKey("ytdlp_ultimo_intento")
        val ytdlpVersion = stringPreferencesKey("ytdlp_version")
        val appUltimaRevision = longPreferencesKey("app_ultima_revision")
        val portapapelesVisto = stringPreferencesKey("portapapeles_visto")
        val usarCookies = booleanPreferencesKey("usar_cookies")
    }

    val simultaneas: Flow<Int> = ds.data.map { it[K.simultaneas] ?: 2 }
    val calidad: Flow<PlayerQuality> = ds.data.map { PlayerQuality.from(it[K.calidad]) }
    val velocidad: Flow<Float> = ds.data.map { it[K.velocidad]?.toFloatOrNull() ?: 1f }
    val sponsorBlock: Flow<Boolean> = ds.data.map { it[K.sponsorBlock] ?: false }
    val ytdlpAuto: Flow<Boolean> = ds.data.map { it[K.ytdlpAuto] ?: true }
    val ytdlpCanal: Flow<YtdlpChannel> = ds.data.map { YtdlpChannel.from(it[K.ytdlpCanal]) }
    val ytdlpVersion: Flow<String?> = ds.data.map { it[K.ytdlpVersion] }
    val usarCookies: Flow<Boolean> = ds.data.map { it[K.usarCookies] ?: false }

    suspend fun setSimultaneas(n: Int) = ds.edit { it[K.simultaneas] = n.coerceIn(1, 5) }
    suspend fun setCalidad(q: PlayerQuality) = ds.edit { it[K.calidad] = q.name }
    suspend fun setVelocidad(v: Float) = ds.edit { it[K.velocidad] = v.toString() }
    suspend fun setSponsorBlock(v: Boolean) = ds.edit { it[K.sponsorBlock] = v }
    suspend fun setYtdlpAuto(v: Boolean) = ds.edit { it[K.ytdlpAuto] = v }
    suspend fun setYtdlpCanal(c: YtdlpChannel) = ds.edit { it[K.ytdlpCanal] = c.name }
    suspend fun setYtdlpVersion(v: String?) = ds.edit { if (v == null) it.remove(K.ytdlpVersion) else it[K.ytdlpVersion] = v }
    suspend fun setUsarCookies(v: Boolean) = ds.edit { it[K.usarCookies] = v }

    suspend fun ytdlpUltimoIntento(): Long = ds.data.first()[K.ytdlpUltimoIntento] ?: 0L
    suspend fun marcarIntentoYtdlp(ahora: Long) = ds.edit { it[K.ytdlpUltimoIntento] = ahora }
    suspend fun appUltimaRevision(): Long = ds.data.first()[K.appUltimaRevision] ?: 0L
    suspend fun marcarRevisionApp(ahora: Long) = ds.edit { it[K.appUltimaRevision] = ahora }
    suspend fun portapapelesVisto(): String? = ds.data.first()[K.portapapelesVisto]
    suspend fun marcarPortapapelesVisto(url: String) = ds.edit { it[K.portapapelesVisto] = url }

    @Suppress("unused")
    suspend fun todo(): Preferences = ds.data.first()
}
