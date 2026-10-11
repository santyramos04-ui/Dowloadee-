package com.santyramos.mirador.player

import com.santyramos.mirador.data.db.DownloadEntity
import com.santyramos.mirador.extractor.Elemento

/** Algo que puede sonar: un video de internet o un archivo ya descargado ([archivoUri]). */
data class ElementoCola(
    val url: String,
    val titulo: String,
    val autor: String? = null,
    val miniatura: String? = null,
    val duracionSeg: Long = -1,
    val archivoUri: String? = null,
    val archivoMime: String? = null,
) {
    val esLocal: Boolean get() = archivoUri != null
    val esAudio: Boolean get() = archivoMime?.startsWith("audio/") == true
}

fun Elemento.Video.aCola() = ElementoCola(url = url, titulo = titulo, autor = autor, miniatura = miniatura, duracionSeg = duracionSeg)

fun DownloadEntity.aCola() = ElementoCola(
    url = url, titulo = titulo, autor = autor, miniatura = miniatura, archivoUri = archivoUri, archivoMime = archivoMime,
)

/** Operaciones de la cola sin estado (así se pueden probar sin el reproductor). */
object ColaLogica {
    /** Al final de la cola. Si ya estaba, no se repite. */
    fun agregar(cola: List<ElementoCola>, e: ElementoCola): List<ElementoCola> =
        if (cola.any { it.url == e.url }) cola else cola + e

    /** Justo después de lo que suena (primero de la cola). Si ya estaba más atrás, se mueve adelante. */
    fun aContinuacion(cola: List<ElementoCola>, e: ElementoCola): List<ElementoCola> =
        listOf(e) + cola.filter { it.url != e.url }

    fun quitar(cola: List<ElementoCola>, i: Int): List<ElementoCola> =
        if (i in cola.indices) cola.toMutableList().apply { removeAt(i) } else cola

    /** Mueve un elemento [delta] puestos (−1 = sube uno). */
    fun mover(cola: List<ElementoCola>, i: Int, delta: Int): List<ElementoCola> {
        val j = i + delta
        if (i !in cola.indices || j !in cola.indices) return cola
        return cola.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Saltar al elemento [i]: suena ese y la cola sigue con los que venían después. */
    fun saltarA(cola: List<ElementoCola>, i: Int): Pair<ElementoCola, List<ElementoCola>>? =
        if (i in cola.indices) cola[i] to cola.drop(i + 1) else null
}

/** Repetir: nada, solo lo que suena, o toda la cola (lo que termina vuelve al final). */
enum class Repetir(val etiqueta: String) { NO("Repetir"), UNA("Repetir una"), TODAS("Repetir todo") ;
    fun siguiente(): Repetir = entries[(ordinal + 1) % entries.size]
}

/** Temporizador para dormir: a qué hora se pausa, o «al terminar lo que suena». */
data class Temporizador(val finMs: Long? = null, val alTerminar: Boolean = false)

/** Une el orden original con la cola actual al quitar el aleatorio: respeta el orden original y deja al final lo nuevo. */
fun restaurarOrden(original: List<ElementoCola>, actual: List<ElementoCola>): List<ElementoCola> {
    val enActual = actual.map { it.url }.toSet()
    val base = original.filter { it.url in enActual }
    val usados = base.map { it.url }.toSet()
    return base + actual.filter { it.url !in usados }
}
