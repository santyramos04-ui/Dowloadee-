# Mirador

**Mirador** es una app para Android, de uso personal, con dos funciones que funcionan igual de bien:

1. **Ver YouTube sin anuncios y sin cuenta de Google** (usa la librería NewPipeExtractor, como NewPipe).
2. **Descargar videos, audio e imágenes** de YouTube, X, Instagram, TikTok, Facebook y muchos otros sitios (usa yt-dlp + ffmpeg dentro de la app).

Todo queda en tu celular. No hay cuentas, ni servidores, ni publicidad. Es software libre bajo licencia **GPLv3** (archivo `LICENSE`).

> Este documento está escrito para alguien que **no programa** y tiene un **Xiaomi con HyperOS**. Si un paso no se entiende, avísame y lo reescribo.

---

## Índice

1. [Instalar la app en el Xiaomi](#1-instalar-la-app-en-el-xiaomi)
2. [Crear la clave de firma (se hace UNA sola vez)](#2-crear-la-clave-de-firma-se-hace-una-sola-vez)
3. [Cómo se publican las versiones](#3-cómo-se-publican-las-versiones)
4. [Verificación de desarrolladores de Google (para seguir instalando la app por años)](#4-verificación-de-desarrolladores-de-google)
5. [Ajustes de batería en HyperOS](#5-ajustes-de-batería-en-hyperos)
6. [Cómo se usa](#6-cómo-se-usa)
7. [Si YouTube o algún sitio deja de funcionar](#7-si-youtube-o-algún-sitio-deja-de-funcionar)
8. [Qué se probó y qué falta probar en tu celular](#8-qué-se-probó-y-qué-falta-probar-en-tu-celular)
9. [Para quien quiera tocar el código](#9-para-quien-quiera-tocar-el-código)

---

## 1. Instalar la app en el Xiaomi

1. En el celular abre el navegador y entra a la página de versiones del proyecto:
   `https://github.com/santyramos04-ui/Dowloadee-/releases/latest`
2. Baja hasta **Assets** y toca el archivo **`Mirador-X.Y.Z.apk`** (el más reciente). Se descarga.
3. Ábrelo desde la notificación de descarga o desde la app **Archivos → Descargas**.
4. Android te dirá algo como *«Por seguridad, tu teléfono no puede instalar apps desconocidas de esta fuente»*. Toca **Ajustes**, activa **Permitir desde esta fuente** (para el navegador o para Archivos) y vuelve atrás.
5. **Aviso de Google Play Protect** («Aplicación bloqueada» / «Aplicación no verificada»): es normal, porque Mirador no está en Play Store. Toca **Más detalles → Instalar de todos modos**.
6. Si aparece el **análisis de seguridad de Xiaomi** (un escudo con «Aplicación riesgosa» o «Analizando»), toca **Instalar** / **Continuar de todos modos**. Solo ocurre la primera vez.
7. Al abrir Mirador por primera vez te pedirá permiso para **notificaciones**: acéptalo (así ves el progreso de las descargas y los controles de música). Los primeros segundos la app prepara el motor de descargas; es normal.

**Actualizar después:** Mirador te avisa dentro de la app («Hay una versión nueva…»). Tocas el aviso → **Descargar** → **Instalar**. Se instala encima y no pierdes nada, porque **todas las versiones se firman siempre con la misma clave**.

---

## 2. Crear la clave de firma (se hace UNA sola vez)

La clave de firma es el «sello» que garantiza que una actualización viene de ti. **Si la pierdes o la cambias, las actualizaciones ya no se instalan encima y Google tampoco te dejará registrar el paquete otra vez.** Por eso:

* Se crea **una sola vez**.
* Se guarda en **GitHub Secrets** (nunca en el repositorio).
* Tú guardas una **copia de respaldo** (paso 2.6).

Todo se hace desde GitHub (en el celular funciona mejor con el navegador en «Sitio de escritorio»).

### 2.1 Haz público el repositorio

La app lee las versiones nuevas desde GitHub sin contraseña, así que el repositorio debe ser público.
GitHub → tu repositorio → **Settings** → bajar hasta **Danger Zone** → **Change visibility** → **Make public**.
*(Las claves y contraseñas NO se publican: viven en Secrets.)*

### 2.2 Inventa una contraseña larga

Usa el generador de contraseñas de tu celular o administrador de contraseñas y crea una de **30 caracteres o más**. Anótala en un lugar seguro (la necesitarás para respaldos).

### 2.3 Crea 3 Secrets

Repositorio → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Crea estos tres:

| Nombre | Valor |
|---|---|
| `KEYSTORE_PASSWORD` | la contraseña larga |
| `KEY_PASSWORD` | **exactamente la misma** contraseña |
| `KEY_ALIAS` | `mirador` |

### 2.4 Ejecuta «Crear clave de firma»

Repositorio → pestaña **Actions** → en la lista de la izquierda **Crear clave de firma (se hace UNA sola vez)** → **Run workflow**.
Cuando termine (1 minuto) ábrelo y entra al paso **Generar la clave**. Verás:

* La **huella SHA-256** de tu clave (la pide Google más adelante).
* Un bloque largo de texto (`base64`) entre dos líneas de guiones.

### 2.5 Guarda la clave como Secret

Copia **todo el texto largo** (mantén presionado para seleccionar) y crea un cuarto Secret:

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | el texto largo (una sola línea) |

El archivo de la clave está protegido por tu contraseña de 30+ caracteres, por eso no es peligroso que haya quedado en el registro del workflow. Aun así, **no compartas la contraseña con nadie**.

### 2.6 Respaldo (importante)

Guarda en tu administrador de contraseñas (o envíate un correo a ti mismo) estas **tres cosas**: el texto largo `KEYSTORE_BASE64`, la contraseña y el alias `mirador`. Con eso se puede recrear la clave si algún día pierdes el repositorio.

> El workflow «Crear clave de firma» se **niega a ejecutarse** si ya existe `KEYSTORE_BASE64`, para que nadie cree otra clave por accidente.

---

## 3. Cómo se publican las versiones

* **Cada vez que se actualiza la rama `main`**, GitHub Actions compila el APK (solo `arm64-v8a`, el de tu celular), lo firma con tu clave, corre las pruebas y lo publica en **Releases** con versión automática (`1.0.<número de cambios>`).
* **Cada lunes** el workflow *Actualización semanal* busca versiones nuevas de NewPipeExtractor y de youtubedl-android, baja el yt-dlp más reciente y lo mete en el APK, compila, prueba y, si todo sale bien, **publica una versión nueva sola**. Si algo falla, **no publica nada** y abre un aviso (Issue) en GitHub.
* **Dependabot** propone cada semana las demás actualizaciones (librerías de Android y acciones de GitHub). Para aplicarlas, abre el aviso en GitHub y toca **Merge** cuando la compilación esté en verde.
* Si falta alguna de las claves de firma, el workflow **se niega a publicar** (y lo dice claramente en el registro).

---

## 4. Verificación de desarrolladores de Google

*(Información de las páginas oficiales de Google para desarrolladores, consultadas el 9 de octubre de 2026. Google va cambiando los detalles: si algo no coincide, manda la página de Google.)*

### Qué es y cuándo llega

Google exigirá que las apps instaladas en Android «certificado» (los celulares con Google Play, como tu Xiaomi) pertenezcan a un **desarrollador verificado**, aunque no se instalen desde Play Store.

| Fecha | Qué pasa |
|---|---|
| 30 de septiembre de 2026 | Empieza en **Brasil, Indonesia, Singapur y Tailandia**: las apps sin registrar no se pueden instalar desde las tiendas participantes (Google Play, Samsung Galaxy Store, **GetApps de Xiaomi**, etc.). |
| 2027 en adelante | Se extiende a todo el mundo, y está previsto que alcance a más tiendas. |
| **Colombia** | **No aparece en la lista inicial.** Estará dentro de la expansión global de 2027. Revisa la lista de «ubicaciones participantes» de Google de vez en cuando. |

Hoy, instalar un APK «a mano» (como haces con Mirador) **no está afectado** por la fecha de septiembre de 2026, pero conviene registrar el paquete **antes** de que llegue a Colombia.

### La cuenta gratuita para aficionados («distribución limitada»)

* **Gratis** (no se paga la tarifa de 25 USD).
* **Sin documento de identidad.**
* Puedes compartir la app con **hasta 20 dispositivos** que tú autorices (el tuyo y los de tu familia).
* Necesitas: una **cuenta de Google con verificación en 2 pasos**, un **perfil de pagos de Google** (pide tu nombre legal y dirección; no se te cobra nada) y un **correo de contacto**.

### Pasos para registrarte

1. Entra a **https://android.google.com/developerconsole/developers** con tu cuenta de Google y crea la cuenta de **distribución limitada** (la opción para estudiantes y aficionados).
2. En la consola abre **Packages (Paquetes)** y agrega este paquete:
   * **Nombre del paquete:** `com.santyramos.mirador`
   * **Huella SHA-256 del certificado:** la de tu clave. La ves en el paso **Generar la clave** del workflow (sección 2.4) y en el resumen de cada compilación («Comprobar la firma y mostrar la huella SHA-256»). Debe ser **la misma siempre**.
3. El estado cambia a **«En revisión»**. Para demostrar que el paquete es tuyo, la consola te pide **subir un APK firmado con tu clave** y te da un **fragmento de texto** que debe ir dentro del APK, en la carpeta de *assets*.
   * Copia ese fragmento, y en GitHub crea el archivo con el **nombre exacto que indique la consola** dentro de la carpeta `app/src/main/assets/` (**Add file → Create new file**).
   * Espera a que se publique la versión nueva (sección 3), descarga ese APK y súbelo a la consola.
   * *(Si tienes dudas con este paso, pásame el texto y el nombre del archivo y lo dejo listo.)*
4. Te llegará un correo y el estado pasará a **«Registrado»**.
5. Para tus otros dispositivos: la consola genera un **enlace o código QR de invitación**; desde el otro celular lo abres y aceptas.

> Si **pierdes la clave**, no podrás volver a registrar el paquete. Por eso el respaldo de la sección 2.6.

### Plan B: el «flujo avanzado» (si algún día no pudieras registrarte)

Google dejará instalar apps no verificadas con un proceso de una sola vez, pensado para usuarios avanzados:

1. Activa las **Opciones de desarrollador** del celular.
2. Confirma que **nadie te está guiando** o presionando para desactivar las protecciones.
3. Reinicia el celular e inicia sesión de nuevo.
4. **Espera 24 horas** y confirma con tu huella o PIN.
5. Elige permitir apps no verificadas por **7 días o para siempre**. Seguirá apareciendo una advertencia; tocas **Instalar de todos modos**.

Instalar por **ADB** (cable y computador) **no se ve afectado** y no exige la espera de 24 horas.

Fuentes: <https://developer.android.com/developer-verification> · <https://developer.android.com/developer-verification/guides/faq> · <https://developer.android.com/developer-verification/guides/limited-distribution>

---

## 5. Ajustes de batería en HyperOS

HyperOS cierra las apps en segundo plano para ahorrar batería, y eso puede **cortar la música o las descargas con la pantalla apagada**. Haz esto una vez:

1. **Sin restricciones de batería**
   *Ajustes → Aplicaciones → Administrar aplicaciones → Mirador → Ahorro de batería → **Sin restricciones*** (en algunas versiones se llama «Sin limitaciones»).
   También puedes tocar, dentro de Mirador, **Biblioteca → Ajustes → Quitar restricción de batería**.
2. **Inicio automático**
   *Ajustes → Aplicaciones → Permisos → Inicio automático → activa **Mirador***.
3. **Bloquear en recientes**
   Abre la pantalla de apps recientes, mantén presionada la tarjeta de Mirador y toca el **candado**. Así el sistema no la cierra al «limpiar memoria».
4. **Notificaciones**
   *Ajustes → Notificaciones → Mirador*: deja activadas las notificaciones y «Mostrar en pantalla de bloqueo» (para los controles de reproducción).
5. Si usas un «Modo de ahorro de batería» fuerte (Ajustes → Batería), desactívalo mientras descargas archivos grandes.

Si aun así algo se corta, avísame con el nombre exacto de la pantalla de tu HyperOS y lo ajusto.

---

## 6. Cómo se usa

Navegación inferior: **Inicio · Suscripciones · Descargas · Biblioteca**.

### Ver YouTube

* **Inicio**: escribe en el buscador. Filtros: *Todo, Videos, Canales, Listas* y duración.
* Toca un video para abrir el **reproductor**: calidad (Auto/1080/720/480), velocidad, pantalla completa, **doble toque a los lados = ±10 s**, **deslizar a la izquierda = brillo / a la derecha = volumen**.
* Al salir del reproductor el **audio sigue sonando** (barra pequeña abajo, notificación y pantalla de bloqueo). Si estás viendo un video y sales de la app, entra en **imagen en imagen**.
* Cada video tiene el botón **Descargar**.
* También puedes tocar un enlace de YouTube en cualquier app y elegir **Mirador** para verlo sin anuncios.

### Descargar de cualquier app o página

Hay tres formas:

1. **Compartir**: en X, Instagram, TikTok, Facebook, el navegador… toca **Compartir → Mirador**. Se abre una hoja desde abajo (sin salir de la app de origen) con la vista previa y los formatos.
2. **Pegar**: pestaña **Descargas** → pega el enlace → **Buscar**. Si abres Mirador con un enlace copiado, te pregunta **«¿Descargar este enlace?»**.
3. *(Próxima versión)* **Navegador interno** con botón flotante «⬇️ Descargar».

Formatos:

| Opción | Qué obtienes |
|---|---|
| **Mejor calidad** | Hasta 1080p, MP4 con H.264 + AAC (se ve en cualquier celular) |
| **720p / 480p** | MP4 más liviano |
| **Máx. 4K** | Solo si el video lo tiene. Pesa mucho y no todos los celulares lo reproducen |
| **Audio MP3** | 192 kbps con portada y título incrustados |
| **Fotos y videos del post** | En publicaciones de X o Instagram con fotos o carruseles, baja todo |

Antes de descargar ves **miniatura, título, duración y peso estimado** de cada opción.
Las descargas van a una **cola** (2 a la vez; puedes cambiarlo en Ajustes) con progreso, velocidad, tiempo restante, **pausar, reanudar, cancelar y reintentar**. Siguen con la pantalla apagada.
Los archivos se guardan en **Descargas/Mirador/** (subcarpetas *Videos*, *Audio* e *Imagenes*) y se ven en **Archivos** y en la **Galería**. La app no pide permisos de almacenamiento.

Cuando termina, la notificación **«✅ Lista»** abre el archivo. Si falla, **«❌ Error»** dice el motivo (sin internet, privado o +18, enlace no compatible, sin espacio, no disponible…).

---

## 7. Si YouTube o algún sitio deja de funcionar

Los sitios cambian a menudo. Prueba **en este orden**:

1. **Descargar no funciona** → *Biblioteca → Ajustes → Motor de descargas → **Actualizar motor ahora***. (La app ya lo hace sola cada día, pero el botón fuerza el cambio al instante.) Si sigue fallando, prueba el canal **Nightly**: los arreglos llegan antes.
2. **Ver YouTube no funciona** (error al cargar o al reproducir) → actualiza la app: *Ajustes → Aplicación → **Buscar actualización***. Cada lunes se publica una versión con la librería de YouTube más nueva.
3. **Quieres forzar la actualización ya** → en GitHub: **Actions → Actualización semanal → Run workflow**. En unos 15 minutos sale la versión nueva (y la app te avisa).
4. **La actualización semanal falló** → verás un aviso (Issue) en GitHub. Significa que la librería nueva rompió algo; no se publicó nada y tu versión instalada sigue igual. Avísame y lo corrijo.
5. **YouTube dice «comprueba que no eres un robot»**: espera unos minutos, cambia de Wi‑Fi a datos (o al revés) y reintenta.
6. **Contenido privado o +18** (por ejemplo, Instagram privado o X con contenido sensible): requiere iniciar sesión en el sitio. Esa función opcional llega en la Fase 3.

---

## 8. Qué se probó y qué falta probar en tu celular

Las pruebas automáticas corren en GitHub Actions en cada cambio (pestaña **Actions**, resumen de cada ejecución). Ver el apartado «Pruebas» de cada entrega para los resultados exactos.

**Pruebas manuales recomendadas en tu Xiaomi (lo que la nube no puede comprobar):**

* [ ] Abrir un video de YouTube y que se reproduzca con sonido e imagen; cambiar calidad y velocidad.
* [ ] Doble toque a izquierda/derecha (±10 s); deslizar para brillo y volumen; pantalla completa.
* [ ] Apagar la pantalla con un video sonando: debe seguir el audio y verse el control en la pantalla de bloqueo.
* [ ] Salir de la app con un video en pantalla: debe entrar en imagen en imagen.
* [ ] «Compartir → Mirador» desde X, Instagram, TikTok y Facebook: debe aparecer la hoja de descarga encima de esa app.
* [ ] Descargar un video (Mejor), un audio MP3 y una publicación de X con fotos; abrirlos desde «✅ Lista» y verlos en Archivos y Galería.
* [ ] Descargar con la pantalla apagada (que no se corte) y probar pausar/reanudar/cancelar.
* [ ] Ajustes → Actualizar motor ahora, y Buscar actualización.

---

## 9. Para quien quiera tocar el código

* Kotlin + Jetpack Compose + Material 3, `minSdk 26`, `targetSdk 37` (Android 17), AGP 9.4, Gradle 9.8.
* Ver: [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (JitPack) + AndroidX Media3.
* Descargar: [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + Python + ffmpeg + **QuickJS**, que es el runtime de JavaScript que yt-dlp exige para YouTube; la librería lo pasa en cada ejecución con `--js-runtimes quickjs:…`).
* Datos locales con Room y DataStore. Nada sale del celular.
* Pruebas: `./gradlew :app:testDebugUnitTest` (lógica) y `./gradlew :app:testDebugUnitTest --tests '*RedTest*' -PredTests=1` (red). Las de yt-dlp por sitio están en `tests/sitios/`: para añadir o cambiar un enlace de prueba solo hay que editar `sitios.json`.

Créditos: NewPipe Team, yt-dlp, youtubedl-android (yausername y JunkFood02/Seal), AndroidX/Jetpack, ffmpeg. Licencia **GPLv3**.
