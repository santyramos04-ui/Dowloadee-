#!/usr/bin/env python3
"""Comprueba que yt-dlp puede EXTRAER información (sin descargar) de enlaces públicos.

Uso: comprobar_sitios.py sitios.json salida.md [--exigir "YouTube (video)" ...]
Sale con código 1 solo si algún sitio exigido no funcionó con ninguno de sus enlaces.
"""
import json
import subprocess
import sys
import time

MOTIVOS = [
    ("Sign in to confirm", "YouTube pide comprobar que no eres un robot (IP de centro de datos)"),
    ("confirm you’re not a bot", "YouTube pide comprobar que no eres un robot (IP de centro de datos)"),
    ("login required", "pide iniciar sesión"),
    ("rate-limit reached or login required", "pide iniciar sesión / límite de peticiones"),
    ("Private video", "video privado"),
    ("age", "contenido +18"),
    ("Video unavailable", "video no disponible"),
    ("unavailable", "video no disponible"),
    ("HTTP Error 404", "enlace muerto (404)"),
    ("HTTP Error 403", "bloqueado (403)"),
    ("HTTP Error 429", "demasiadas peticiones (429)"),
    ("Unsupported URL", "enlace no compatible"),
    ("No video could be found", "el enlace no tiene video"),
]


def motivo(err: str) -> str:
    bajo = err.lower()
    for clave, texto in MOTIVOS:
        if clave.lower() in bajo:
            return texto
    ultima = [l for l in err.strip().splitlines() if l.strip()]
    return (ultima[-1] if ultima else "error desconocido")[:160]


def probar(url: str):
    t0 = time.time()
    p = subprocess.run(
        ["yt-dlp", "-J", "--no-playlist", "--no-warnings", "--js-runtimes", "node",
         "--socket-timeout", "20", "--extractor-retries", "1", url],
        capture_output=True, text=True, timeout=240)
    dt = time.time() - t0
    if p.returncode == 0:
        try:
            info = json.loads(p.stdout)
        except Exception:
            return False, "respuesta no es JSON", dt, {}
        formatos = info.get("formats") or []
        return True, "", dt, {
            "titulo": (info.get("title") or "")[:70],
            "duracion": info.get("duration"),
            "formatos": len(formatos),
            "extractor": info.get("extractor_key"),
        }
    return False, motivo(p.stderr), dt, {}


def main():
    cfg = json.load(open(sys.argv[1], encoding="utf-8"))["sitios"]
    salida = sys.argv[2]
    exigidos = []
    if "--exigir" in sys.argv:
        exigidos = sys.argv[sys.argv.index("--exigir") + 1:]
    ver = subprocess.run(["yt-dlp", "--version"], capture_output=True, text=True).stdout.strip()
    lineas = [f"## Extracción con yt-dlp {ver}\n", "| Sitio | Resultado | Detalle |", "|---|---|---|"]
    detalle = []
    fallos = []
    for sitio, datos in cfg.items():
        ok_sitio = False
        notas = []
        for url in datos["urls"]:
            ok, err, dt, info = probar(url)
            if ok:
                ok_sitio = True
                notas.append(f"OK `{url}` → «{info['titulo']}», {info['formatos']} formatos, {dt:.0f}s")
                break
            notas.append(f"falló `{url}`: {err}")
        lineas.append(f"| {sitio} | {'✅ funciona' if ok_sitio else '❌ no funcionó'} | {notas[-1]} |")
        detalle.append(f"### {sitio}\n" + "\n".join(f"- {n}" for n in notas))
        if not ok_sitio and sitio in exigidos:
            fallos.append(sitio)
    texto = "\n".join(lineas) + "\n\n" + "\n\n".join(detalle) + "\n"
    open(salida, "w", encoding="utf-8").write(texto)
    print(texto)
    if fallos:
        print("SITIOS EXIGIDOS QUE FALLARON:", fallos)
        sys.exit(1)


if __name__ == "__main__":
    main()
