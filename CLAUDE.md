# RACQ (antes Mirador)

Nombre visible: **RACQ** (`version.properties` → `nombre`). Su significado, «Raccoon Audio Quest», solo aparece en Ajustes junto a la versión. El código y el paquete siguen como `mirador`.

App Android (Kotlin + Jetpack Compose, Media3, NewPipeExtractor, yt-dlp vía youtubedl-android). Licencia GPLv3.
El dueño no programa: explicar todo en español sencillo y paso a paso.

## Cómo se trabaja
- Compilar y probar **solo en GitHub Actions** (`.github/workflows/construir.yml`): el entorno local no llega a JitPack ni a Google Maven. Decir "listo" únicamente con el CI en verde.
- Publicar = PR de la rama de trabajo a `main` → esperar CI del PR → merge squash → esperar el run de `main` y confirmar la release (tag `v1.0.N` y APK). Ver la skill `/publicar`. No abrir PRs ni publicar sin que se pida.
- Si la rama de trabajo ya se fusionó, recrearla desde `origin/main` antes de seguir.
- Volver atrás si una versión falla: workflow `volver-atras.yml` (republica el código de una versión anterior con número más alto; nunca pedir desinstalar). No subir la versión de una base de datos sin migración, o ese botón queda bloqueado.
- Nunca subir claves: la clave de firma vive en GitHub Secrets; `secrets/` está en `.gitignore`.

## En pausa
- **Windows (`escritorio/`)**: olvidado hasta que el dueño lo pida expresamente. No proponerlo, no modificarlo; el job `windows` del CI está apagado (`if: false`).
- **«Mi servidor» (Jellyfin)**: escondido con `MOSTRAR_SERVIDOR = false` en `PeliculasScreen.kt` hasta que lo pida.

## Código
- Interfaz en español. Paquete `com.santyramos.mirador`.
- Diseño: tokens en `app/src/main/java/com/santyramos/mirador/ui/theme/Theme.kt` (`Paleta`, `Acento`, tema claro/oscuro). Usarlos en vez de colores fijos. Fuentes estáticas en `res/font` (no variables): Bricolage Grotesque para títulos y Atkinson Hyperlegible Next para el texto.
- Componentes compartidos en `ui/Componentes.kt` (BotonPrimario, BotonSecundario, BotonIcono, Filtro, HojaMirador…).
- Datos: `data/db` (descargas) y `data/lib` (biblioteca: suscripciones, historial, listas). Nunca borrar datos del usuario: cambios de tablas con migraciones.
- Pruebas unitarias en `app/src/test`; las de red (`RedTest`) solo corren en CI con `-PredTests=1`.
- Cambios de diseño grandes: preguntar antes.

## Mapa del código (`app/src/main/java/com/santyramos/mirador/`)
- `extractor/Youtube.kt`: búsqueda, canales, listas, comentarios (NewPipe). Español forzado por extractor (`Localization("es","419")` + país CO); NewPipe solo acepta en-GB como idioma global.
- `data/lib/Recomendador.kt` + `ui/screens/HomeScreen.kt`: «Para ti» infinito (`FeedParaTi`), temas, mezcla de canales; filas Lives y Shorts.
- `player/VideoController.kt`: único dueño del reproductor (cola, repetir, subtítulos, capítulos). `PlayerHolder` crea el ExoPlayer con sesión de audio fija.
- `player/PlaybackService.kt` (MediaLibraryService) + `BibliotecaAuto.kt` + `ControlesSesion.kt`: notificación, pantalla de bloqueo y Android Auto. Lo que pide el carro entra por `setMediaItems` → cola de la app.
- `player/Subtitulos.kt`, `player/Sonido.kt` (ecualizador y DynamicsProcessing), `extractor/Letras.kt` + `ui/LetraSheet.kt` (LRCLIB).
- `ui/ShortsScreen.kt` (objeto `Shorts`; capa encima de todo en `ui/App.kt`), `ui/WidgetRacq.kt` (widget), `ui/CabeceraPlegable.kt`.
- `download/YtDlpEngine.kt` (opciones de yt-dlp, metadatos de música con `--parse-metadata`, portada cuadrada), `DownloadCenter.kt` (cola, solo Wi‑Fi, pausas por freno de YouTube, reintentos), `ErrorMapper.kt`.
- `util/Fallos.kt`: informe de cierre inesperado → Descargas/RACQ/Informes.
- Navegación: pila `Destino` en `ui/App.kt`; el reproductor y Shorts son capas aparte.

## Cosas aprendidas
- YouTube bloquea a veces las IP de GitHub: `pruebas-ver` con «Got HTML document»/«Sign in to confirm» es **no concluyente**, no un fallo del código.
- En esta nube `gh` sin GraphQL: usar `gh api repos/...` (REST) o `curl` a las URLs de releases; PR con `gh api .../pulls`.
- Un push nuevo a la rama cancela el CI anterior de esa rama: esperar el resultado antes de empujar otra cosa.
- Android no instala una versión más baja encima de una más alta («paquete no válido»): por eso el enlace viejo `Mirador-prueba.apk` también se actualiza en cada prueba.
- Listas en Compose: usar `distinctBy { it.url }` antes de `items(key=…)` (ya causó un cierre por clave repetida).
- Leer el `AndroidManifest.xml` binario de un APK: script propio en Python (no hay aapt local).

## Pendiente / ideas sin hacer
- Sin respuesta del dueño: ¿las listas con canciones ajenas eran «Mix» de YouTube (`list=RD…`)? Si sí, avisar antes de descargarlas.
- Nada de lo de la 1.0.45 (Shorts, Auto, widget, letras, sonido, subtítulos, solo Wi‑Fi, MP3) se ha probado aún en el celular: ver LEEME §8.
- Rechazado por legalidad: buscadores de anime/películas piratas (p. ej. Seekee) y extraer Pluto TV (solo abrir su enlace).
- Historial de versiones para el dueño: `docs/CAMBIOS.md` (agregar una línea por versión publicada).

