# Diseño

- `icono_mapache.svg`: ícono original de RACQ (mapache con audífonos). El fondo (`id="fondo"`) se puede
  cambiar de color; el mapache (`id="mapache"`) no se modifica.
- En la app el fondo es el azul marino de la marca (`res/drawable/ic_launcher_background.xml`) y el mapache
  va como imágenes PNG por densidad (`res/drawable-*dpi/ic_launcher_foreground.png`), porque el SVG tiene más
  de mil formas y como vector de Android sería demasiado pesado.
- `mapache_una_tinta.svg`: el logo a una sola tinta, para lo muy pequeño. En la app es el ícono de
  notificación (`res/drawable/ic_stat_mirador.xml`) y el ícono temático de Android 13+
  (`res/drawable/ic_launcher_monochrome.xml`), los dos como vector (se ven nítidos en cualquier tamaño).
