#!/usr/bin/env python3
"""Sube NewPipeExtractor y youtubedl-android a su última versión estable en gradle/libs.versions.toml.

Escribe en $GITHUB_OUTPUT:  cambios=true|false  y  resumen=<texto>
(yt-dlp no se sube aquí: lo descarga scripts/descargar-ytdlp.sh al compilar y la app lo
actualiza sola.)
"""
import json
import os
import re
import sys
import urllib.request

TOML = "gradle/libs.versions.toml"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "mirador-ci"})
    tok = os.environ.get("GITHUB_TOKEN")
    if tok and "api.github.com" in url:
        req.add_header("Authorization", f"Bearer {tok}")
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode()


def ultimo_newpipe():
    return json.loads(get("https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest"))["tag_name"]


def ultimo_youtubedl():
    xml = get("https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/library/maven-metadata.xml")
    return re.search(r"<release>([^<]+)</release>", xml).group(1)


def main():
    texto = open(TOML, encoding="utf-8").read()
    nuevo = {"newpipeExtractor": ultimo_newpipe(), "youtubedlAndroid": ultimo_youtubedl()}
    cambios = []
    for clave, valor in nuevo.items():
        m = re.search(rf'^({clave}\s*=\s*")([^"]+)(")', texto, re.M)
        if not m:
            print(f"No encontré {clave} en {TOML}", file=sys.stderr)
            sys.exit(1)
        if m.group(2) != valor:
            cambios.append(f"{clave}: {m.group(2)} -> {valor}")
            texto = texto[: m.start(2)] + valor + texto[m.end(2):]
    open(TOML, "w", encoding="utf-8").write(texto)
    resumen = "; ".join(cambios) if cambios else "sin cambios"
    print(resumen)
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a") as f:
            f.write(f"cambios={'true' if cambios else 'false'}\nresumen={resumen}\n")


if __name__ == "__main__":
    main()
