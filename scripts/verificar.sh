#!/usr/bin/env bash
# Verificación canónica del frente Android.
#
# Ejecuta compilación, pruebas unitarias y ensamblado de las instrumentadas con los flags del
# proyecto. Si falla, imprime el bloque FAILURE completo y los errores de Kotlin; si pasa, solo
# el resumen. Uso: scripts/verificar.sh [--rerun]
set -uo pipefail

cd "$(dirname "$0")/.."
: "${JAVA_HOME:=/home/brunotarangogaray/android-studio/jbr}"
export JAVA_HOME

EXTRA=()
if [[ "${1:-}" == "--rerun" ]]; then
  EXTRA+=(--rerun-tasks)
fi

SALIDA="$(mktemp)"
trap 'rm -f "$SALIDA"' EXIT

if ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest \
    --no-daemon --offline "${EXTRA[@]}" >"$SALIDA" 2>&1; then
  grep -E "^BUILD" "$SALIDA" || true
  python3 - "$PWD" <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

patron = os.path.join(sys.argv[1], "app/build/test-results/testDebugUnitTest/*.xml")
total = fallos = errores = omitidas = 0
for ruta in glob.glob(patron):
    raiz = ET.parse(ruta).getroot()
    total += int(raiz.get("tests", 0))
    fallos += int(raiz.get("failures", 0))
    errores += int(raiz.get("errors", 0))
    omitidas += int(raiz.get("skipped", 0))
print(f"pruebas={total} fallos={fallos} errores={errores} omitidas={omitidas}")
PY
else
  echo "--- FAILURE ---"
  sed -n '/^FAILURE:/,$p' "$SALIDA"
  echo "--- errores de compilacion ---"
  grep -E "^e: " "$SALIDA" | head -40 || true
  exit 1
fi
