# Tu propio Netflix con una mini PC (Jellyfin + Mirador)

Con esta guía la mini PC guarda tus películas, series y videos personales, y Mirador los muestra
en **Películas → Mi servidor**, en calidad original, en casa y fuera de casa.

Programas que vas a usar (todos gratis):
- **Jellyfin**: el "Netflix" que corre en la mini PC. Ordena tus archivos, baja portadas y
  sinopsis, y recuerda por dónde ibas.
- **Tailscale**: una red privada para ver tus videos fuera de casa sin abrir puertos del router.

---

## 1. La mini PC

Si todavía no la tienes, busca una así:

| Qué | Recomendado | Por qué |
|---|---|---|
| Procesador | Intel N100, N150 o mejor (Core i3/i5 de 8.ª generación en adelante) | Su parte gráfica (Quick Sync) convierte video 4K sin esfuerzo cuando el celular lo necesita |
| Memoria | 8 GB (mejor 16 GB) | Suficiente para Jellyfin y Windows |
| Disco | SSD para el sistema + disco grande (interno o USB) para las películas | Una película 1080p pesa entre 2 y 10 GB; una 4K, entre 15 y 60 GB |
| Red | Puerto Ethernet (cable) | Conéctala al router con cable: así nunca se corta ni se pone lenta |
| Sistema | Windows 11 (suele venir instalado) | Es lo más fácil de manejar |

> Evita los procesadores AMD o Intel muy antiguos sin gráficos integrados si piensas ver 4K fuera de casa.

## 2. Preparar Windows para que la mini PC quede siempre prendida

1. **Inicio → Configuración → Sistema → Energía**.
2. En "Suspender el equipo" elige **Nunca** (con corriente).
3. Si la BIOS lo permite, activa "Encender al volver la luz" (*Restore on AC power loss* → *Power On*).

## 3. Instalar Jellyfin

1. En la mini PC abre <https://jellyfin.org/downloads/server> y descarga **Windows (x64) – Installer**.
2. Instálalo con las opciones por defecto. Marca **"Install as a service"** (servicio): así arranca solo
   aunque nadie inicie sesión en Windows.
3. Al terminar se abre el navegador en `http://localhost:8096`. Sigue el asistente:
   - Idioma: **Español**.
   - Crea tu **usuario y contraseña** (los vas a escribir en Mirador).
   - **Agregar biblioteca** (puedes crear varias):
     - *Películas* → tipo **Películas** → carpeta, por ejemplo `D:\Películas`.
     - *Series* → tipo **Programas** → `D:\Series`.
     - *Videos personales* → tipo **Videos caseros y fotos** → `D:\Videos`.
   - Idioma de los datos: **Español**, país: **Colombia**.
   - Deja marcado **"Permitir conexiones remotas"**.
4. Si Windows pregunta por el Firewall, toca **Permitir** (redes privadas).

### Cómo nombrar los archivos para que salgan las portadas

```
D:\Películas\Coco (2017)\Coco (2017).mkv
D:\Series\La casa de papel\Season 01\La casa de papel S01E01.mkv
D:\Videos\Vacaciones 2024\playa.mp4
```

Con el año entre paréntesis Jellyfin encuentra la película correcta. Los videos personales no necesitan
un nombre especial.

### Activar la conversión rápida (aceleración por hardware)

En Jellyfin: **Panel de control → Reproducción → Transcodificación** → *Aceleración por hardware*:
**Intel QuickSync (QSV)** (o **NVIDIA NVENC** si tu mini PC tiene tarjeta NVIDIA). Marca H.264, HEVC
y "Permitir codificación en HEVC". Guarda.

Casi siempre Mirador reproduce **el archivo original sin convertir** (máxima calidad y arranque rápido).
La conversión solo se usa si el celular no puede leer el formato o si activas "Ahorro de datos".

## 4. Ver fuera de casa con Tailscale

1. En la mini PC: descarga Tailscale de <https://tailscale.com/download>, instálalo e inicia sesión
   (por ejemplo con tu cuenta de Google).
2. En el celular: instala **Tailscale** desde Play Store e inicia sesión **con la misma cuenta**.
3. En la app de Tailscale del celular verás la mini PC con una dirección tipo `100.x.y.z`.
   Esa es la dirección para usar fuera de casa.

No hay que tocar el router ni abrir puertos: solo tus equipos con tu cuenta pueden entrar.

## 5. Conectar Mirador

1. Abre Mirador → **Películas** → **Mi servidor**.
2. En casa, Mirador busca el servidor en el Wi‑Fi y lo muestra solo; tócalo.
   Fuera de casa (con Tailscale encendido) escribe la dirección `100.x.y.z`.
3. Escribe tu usuario y contraseña de Jellyfin → **Conectar**.

Verás "Continuar viendo", lo último que agregaste a cada biblioteca, las series por temporadas y un
buscador. En el menú **⋮** puedes activar **Ahorro de datos (720p)** cuando estés con datos móviles.

> Truco: si en casa usas la dirección de Tailscale (`100.x.y.z`) también funciona, así no tienes que
> cambiar nada al salir. Dentro de casa Tailscale va directo por el Wi‑Fi y no gasta datos.

## Si algo no funciona

| Problema | Qué revisar |
|---|---|
| Mirador no encuentra el servidor en el Wi‑Fi | Que el celular esté en el mismo Wi‑Fi (no en la red de invitados) y que el Firewall de Windows permita Jellyfin en redes privadas. Escribe la dirección a mano: en la mini PC, `ipconfig` muestra la "Dirección IPv4" (ej. `192.168.1.20`). |
| Fuera de casa no conecta | Que Tailscale esté encendido en el celular **y** en la mini PC. |
| Se traba en 4K | Usa el cable de red en la mini PC y Wi‑Fi de 5 GHz en el celular; fuera de casa activa "Ahorro de datos". |
| No salen portadas | Revisa el nombre de la carpeta (título + año) y en Jellyfin toca **⋮ → Actualizar metadatos**. |
