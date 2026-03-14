#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-Voycepeh/Handstand-App}"
OUT_DIR="${2:-artifacts}"

API_URL="https://api.github.com/repos/${REPO}/releases/latest"
mkdir -p "$OUT_DIR"

echo "Fetching latest release metadata from ${API_URL}"
RELEASE_JSON="$(curl -fsSL "$API_URL")"

APK_URL="$(printf '%s' "$RELEASE_JSON" | python -c 'import json,sys; data=json.load(sys.stdin); assets=data.get("assets",[]); apk=[a.get("browser_download_url") for a in assets if str(a.get("name","")).endswith(".apk")]; print(apk[0] if apk else "")')"
APK_NAME="$(printf '%s' "$RELEASE_JSON" | python -c 'import json,sys; data=json.load(sys.stdin); assets=data.get("assets",[]); apk=[a.get("name") for a in assets if str(a.get("name","")).endswith(".apk")]; print(apk[0] if apk else "")')"

if [[ -z "$APK_URL" || -z "$APK_NAME" ]]; then
  echo "No APK asset found in latest release for ${REPO}." >&2
  exit 1
fi

DEST_PATH="${OUT_DIR}/${APK_NAME}"
echo "Downloading ${APK_NAME}"
curl -fL "$APK_URL" -o "$DEST_PATH"

echo "Downloaded: ${DEST_PATH}"
