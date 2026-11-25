#!/usr/bin/env bash
# Instala la revisión actual, ejecuta el benchmark en el POCO y extrae su evidencia.
set -euo pipefail
cd "$(dirname "$0")/.."

serial="${1:-4eeb389e}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
adb="${ADB:-${sdk:+$sdk/platform-tools/adb}}"
adb="${adb:-adb}"

if ! command -v "$adb" >/dev/null; then
    echo "No se encontró ADB. Configura sdk.dir en local.properties o la variable ADB." >&2
    exit 1
fi
if [[ "$("$adb" -s "$serial" get-state)" != "device" ]]; then
    echo "Conecta el POCO por USB y autoriza la depuración antes de ejecutar." >&2
    exit 1
fi
modelo="$("$adb" -s "$serial" shell getprop ro.product.model | tr -d '\r')"
if [[ "$modelo" != "23122PCD1G" ]]; then
    echo "El dispositivo $serial reporta $modelo. Se esperaba el POCO 23122PCD1G." >&2
    exit 1
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

salida="$(mktemp -d "${TMPDIR:-/tmp}/securesms-poco.XXXXXXXX")"
echo "Resultados: $salida"
git rev-parse HEAD > "$salida/commit.txt"
estado=0
"$adb" -s "$serial" shell am instrument -w \
    -e class com.securesms.app.benchmark.SecureSmsBenchmarkTest \
    com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner |
    tee "$salida/instrumentacion.txt" || estado=$?

"$adb" -s "$serial" pull /sdcard/Android/data/com.securesms.app/files/securesms-bench "$salida/"
if (( estado != 0 )) || ! grep -Eq '^OK \(1 test\)' "$salida/instrumentacion.txt"; then
    echo "El benchmark no terminó con OK. Revisa $salida/instrumentacion.txt." >&2
    exit 1
fi
