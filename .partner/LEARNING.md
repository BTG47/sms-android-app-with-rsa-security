# Registro de aprendizaje — SecureSMS / Bruno

## Unidad 1 — ¿Qué son los Android SDK Build Tools? (2026-11-21)
- Objetivo: distinguir Build Tools de SDK Platform, Platform Tools y Command-line Tools, y saber dónde se instalan.
- Modo: recursos (preferencia confirmada por el usuario).
- Evidencia demostrada por el usuario: formuló la pregunta correcta sobre la instalación; instaló además `cmdline-tools` por iniciativa propia (conducta proactiva verificada en disco). Predicción sobre `build-tools 35.0.0` aún no respondida.
- Fuente primaria consultada: documentación oficial de Android Studio, sección «Update your tools with the SDK Manager» (developer.android.com/studio/intro/update).
- Brecha abierta: verificar que sepa identificar cuándo un error de compilación corresponde a Build Tools frente a SDK Platform.
- Próxima práctica: respuesta a la predicción «Gradle pide build-tools 35.0.0, ¿dónde se instala?» (diferida por el usuario) y, luego, primera compilación del esqueleto `:app`.

## Unidad 2 — Bloque 1: sintaxis mínima de Kotlin (en curso, 2026-11-21)
- Objetivo: `val`/`var`, funciones con tipos explícitos, `String` templates, null-safety (`?`, `?.`, `?:`) y `data class`, aplicados al resumen de diagnóstico SMS.
- Fuentes primarias consultadas: `kotlinlang.org/docs/basic-syntax.html` (secciones Functions, Variables, String templates) y `kotlinlang.org/docs/null-safety.html` (Nullable types, Safe call, Elvis).
- Regla de proyecto derivada: prohibido `!!` en código propio; los estados ausentes se modelan con `?` y se resuelven con `?:`.
- Ejercicio entregado por el usuario en `prueba.kt` y corregido en `prueba_corregida.kt` (verificado: compila con el `kotlinc` de Android Studio y los tres `check()` pasan).
- Evidencia demostrada por el usuario: uso correcto de `?:` en dos posiciones, firma de función con tipos explícitos, declaración de objeto con `val`. No evaluado: `data class`, convención de nombres, `val` frente a `var`.
- Brechas detectadas: (1) interpolación `$objeto.propiedad` sin llaves — el compilador no avisa y produjo `my.demo.Destinatario@...numero`; (2) omisión de los prefijos `SMS:` y `huella:`; (3) `class` en vez de `data class`; (4) `var` donde bastaba `val`; (5) falta de casos de prueba.
- Aprendizaje clave para reforzar: un error puede compilar y fallar en runtime; por eso cada bloque termina con `check()` ejecutable.
- Puede esperar: corrutinas, Flow, Compose, clases selladas, genéricos.
- Cierre: el usuario aceptó la corrección; el código pasó a producción como `DatosDeEnvio` con 3 pruebas Gradle en verde. Brecha a reforzar en el siguiente bloque: interpolación con llaves y elección de `val` frente a `var`.
