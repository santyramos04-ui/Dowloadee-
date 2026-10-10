---
name: publicar
description: "Publica una versión nueva de Mirador: PR de la rama de trabajo a main, espera el CI, fusiona y confirma la release con su APK. Usar solo cuando el dueño diga «publícala» o pida publicar."
effort: medium
allowed-tools: Read Grep
disable-model-invocation: true
---

# Publicar una versión

Tiene efectos visibles (sale una versión para todos), por eso solo se ejecuta cuando el usuario lo pide.

## Pasos

1. **Rama al día.** `git status` limpio y la rama de trabajo empujada. Si la rama ya estaba fusionada, recrearla desde `origin/main` y reaplicar los commits pendientes.
   Listo cuando: `git log origin/main..HEAD` muestra los commits a publicar.
2. **CI de la rama en verde.** El último run de `construir.yml` en la rama terminó con `construir`, `pruebas-ver` y `pruebas-descargar` en `success`.
   Si falla: leer el registro (resumen «ERRORES DE COMPILACIÓN»), corregir, empujar y volver a este paso.
3. **Crear el PR** a `main` con un resumen en español de lo que cambia y qué falta probar en el celular.
   Listo cuando: existe el PR y su run (`event=pull_request`) terminó en verde.
4. **Fusionar** con squash.
5. **Release.** Esperar el run de `main` del commit fusionado y confirmar en `releases/latest` el tag nuevo (`v1.0.N`) y el archivo `Mirador-1.0.N.apk`.
   Listo cuando: el tag y el APK existen. Si el paso «Publicar en GitHub Releases» se saltó, revisar que los 4 secretos de firma existan.
6. **Avisar al usuario**: versión, cómo instalarla (Ajustes → Aplicación → Buscar actualización) y qué probar primero.

## Si algo falla
- CI rojo por código: es nuestro; corregir antes de publicar.
- YouTube bloquea la IP de GitHub en `pruebas-ver`: no concluyente, no bloquea la publicación (decirlo).
- Nunca publicar con el CI rojo ni forzar el push a `main`.
