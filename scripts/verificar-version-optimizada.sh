#!/usr/bin/env bash
# Instala en el emulador la versión optimizada con R8 (igual a la de uso) y comprueba que
# arranca sin cerrarse. Se usa en la CI.
set -euo pipefail
apk=app/build/outputs/apk/verificacion/app-verificacion.apk
paquete=com.hemocheck.verificacion
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$paquete/com.hemocheck.MainActivity"
sleep 10
adb logcat -d > logcat-verificacion.txt
if grep -q "FATAL EXCEPTION" logcat-verificacion.txt || [ -z "$(adb shell pidof "$paquete" | tr -d '\r')" ]; then
  echo "ERROR: la versión optimizada se cerró al abrir"
  grep -A40 "FATAL EXCEPTION" logcat-verificacion.txt || true
  exit 1
fi
echo "La versión optimizada arrancó bien"
