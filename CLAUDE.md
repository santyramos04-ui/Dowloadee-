# Mirador

App Android (Kotlin + Jetpack Compose, Media3, NewPipeExtractor, yt-dlp vía youtubedl-android). Licencia GPLv3.
El dueño no programa: explicar todo en español sencillo y paso a paso.

## Cómo se trabaja
- Compilar y probar **solo en GitHub Actions** (`.github/workflows/construir.yml`): el entorno local no llega a JitPack ni a Google Maven. Decir "listo" únicamente con el CI en verde.
- Publicar = PR de la rama de trabajo a `main` → esperar CI del PR → merge squash → esperar el run de `main` y confirmar la release (tag `v1.0.N` y APK). Ver la skill `/publicar`. No abrir PRs ni publicar sin que se pida.
- Si la rama de trabajo ya se fusionó, recrearla desde `origin/main` antes de seguir.
- Nunca subir claves: la clave de firma vive en GitHub Secrets; `secrets/` está en `.gitignore`.

## Código
- Interfaz en español. Paquete `com.santyramos.mirador`.
- Diseño: tokens en `app/src/main/java/com/santyramos/mirador/ui/theme/Theme.kt` (`Paleta`, `Acento`, tema claro/oscuro). Usarlos en vez de colores fijos. Fuentes estáticas en `res/font` (no variables).
- Componentes compartidos en `ui/Componentes.kt` (BotonPrimario, BotonSecundario, BotonIcono, Filtro, HojaMirador…).
- Datos: `data/db` (descargas) y `data/lib` (biblioteca: suscripciones, historial, listas). Nunca borrar datos del usuario: cambios de tablas con migraciones.
- Pruebas unitarias en `app/src/test`; las de red (`RedTest`) solo corren en CI con `-PredTests=1`.
- Cambios de diseño grandes: preguntar antes.
