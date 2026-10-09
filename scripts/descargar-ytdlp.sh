#!/usr/bin/env bash
# Descarga el yt-dlp más reciente y lo deja dentro del APK (app/src/main/assets/ytdlp/).
# Así una instalación nueva ya trae el motor al día; después la app lo mantiene sola.
# Uso: scripts/descargar-ytdlp.sh [nightly|stable]
set -euo pipefail
CANAL="${1:-nightly}"
REPO="yt-dlp/yt-dlp-nightly-builds"
[ "$CANAL" = "stable" ] && REPO="yt-dlp/yt-dlp"
DEST="app/src/main/assets/ytdlp"
mkdir -p "$DEST"
AUTH=()
[ -n "${GITHUB_TOKEN:-}" ] && AUTH=(-H "Authorization: Bearer $GITHUB_TOKEN")
TAG=$(curl -fsSL "${AUTH[@]}" "https://api.github.com/repos/$REPO/releases/latest" | python3 -c "import sys,json;print(json.load(sys.stdin)['tag_name'])")
curl -fsSL -o "$DEST/yt-dlp" "https://github.com/$REPO/releases/download/$TAG/yt-dlp"
echo -n "$TAG" > "$DEST/version.txt"
echo "yt-dlp embebido: $TAG ($CANAL), $(du -h "$DEST/yt-dlp" | cut -f1)"
