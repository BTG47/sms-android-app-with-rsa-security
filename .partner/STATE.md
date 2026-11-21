# Estado — SecureSMS / frente Android

Actualizado: 2026-11-21. Breve aceptado por el usuario; contrato v0.2 leído íntegramente; bloque 1 completado.

## Listo
- OBSERVADO: repositorio real inventariado. Solo `ANDROID_IMPLEMENTATION.md`, 4 PDF, `prueba.kt` y `prueba_corregida.kt`; no hay `docs/`, módulos Gradle ni repo Git inicializado.
- OBSERVADO: contrato v0.2 leído de `SecureSMS_Contrato_Integracion_v02_Detallado-1.pdf`; reglas volcadas en `PROJECT.md`.
- RESUELTO: el usuario instaló Android Studio y avisó. Verificado en `/home/brunotarangogaray/Android/Sdk`: build-tools `36.0.0`, platform `android-37.0`, platform-tools `adb 37.0.3`, emulator e imagen `android-37.0`, y `cmdline-tools/latest/bin/sdkmanager` (el usuario lo añadió por iniciativa propia). La IA no instala nada. JDK 17 (Temurin) disponible.
- OBSERVADO: compilador Kotlin disponible en `/home/brunotarangogaray/android-studio/plugins/Kotlin/kotlinc/bin/kotlinc` (versión 2.4.0), útil para pruebas rápidas sin Gradle. Hay Gradle 9.6.0 en caché; no hay archivos Gradle en el repo.
- OBSERVADO: dispositivo físico `FKTVB20A27000469` conectado: HUAWEI STK-LX3, Android 10 / API 29. SIM `LOADED`, operador TELCEL (MCC 334 / MNC 020), registrado HOME, dominio CS con servicio `SMS`. Funciones `android.hardware.telephony`, `.gsm` e `.ims`. No hay app `securesms` instalada. AVD `Medium_Phone_API_37.0` disponible. => Las pruebas SMS físicas son potencialmente posibles con un solo equipo; la prueba extremo a extremo con dos teléfonos sigue sin cubrirse.
- OBSERVADO: arquitectura propuesta persistida en `ARCHITECTURE.md` y decisiones con ID en `DECISIONS.md`, para conservar el contexto ante compactación.
- RESUELTO: modo de acompañamiento elegido = **A - Recursos** (concepto mínimo Kotlin/Compose + ejercicio corto antes de cada bloque de código).
- OBSERVADO: bloque 1 ejecutado. `prueba.kt` es el intento del usuario (conservado sin modificar); `prueba_corregida.kt` es la versión revisada, compilada con `kotlinc` y verificada con tres `check()`.

## Decisiones de la ronda 1 de grill (2026-11-21)
- Q1: usuario respondió «El contrato ya fue condicionado»; texto ambiguo, se aclara en ronda 2 si significa congelado por ambos o norma interna con copia controlada pendiente.
- Q2: aceptados los términos Envelope, Fragmento, SMS, Mensaje y la separación de estados de transporte. `GLOSSARY.md` creado.
- Q3: aceptada la separación `Contacto` / `Clave pública importada` y la verificación tripartita. ADR-0001 creado.
- Q4: aceptado `CryptoGateway` con `FakeCryptoGateway` aislado y etiquetado demo.
- Q5: aceptado `GLOSSARY.md` en raíz, ADRs en `docs/adr/`, `.partner/` como handoff.
- Q6: el usuario rechazó el alcance recortado por fecha; quiere la app completa, dispone de dos teléfonos propios y el Huawei conectado puede no usarse. Se reformula como decisión de alcance en ronda 2.

## Implementación del bloque 1 (2026-11-21)
- OBSERVADO: esqueleto Gradle creado. Versiones en caché y verificadas: Gradle 9.6.0, AGP 9.5.2, Kotlin Gradle Plugin/Compose 2.2.10, Compose BOM 2026.02.01, material3 1.4.0, JUnit 4.13.2. Se compila con el JBR 25 de Android Studio (`JAVA_HOME=/home/brunotarangogaray/android-studio/jbr`).
- OBSERVADO: AGP 9.5.2 registra la extensión `kotlin`; aplicar además `org.jetbrains.kotlin.android` falla. Configuración válida: `com.android.application` + `org.jetbrains.kotlin.plugin.compose`.
- OBSERVADO: `local.properties` apunta a `/home/brunotarangogaray/Android/Sdk`; `.gitignore` lo excluye. `gradlew` generado en la raíz.
- OBSERVADO: primer código de producción en `app/src/main/java/com/securesms/app/ui/ResumenTransporte.kt` (como `DatosDeEnvio`, proyección de pantalla que no reemplaza a los conceptos del glosario). Pruebas en `app/src/test/java/com/securesms/app/ui/ResumenTransporteTest.kt`.
- VERIFICADO: `JAVA_HOME=.../jbr ./gradlew :app:testDebugUnitTest --no-daemon` → BUILD SUCCESSFUL, 3 pruebas, 0 fallos (XML de resultado).

## Riesgos / pendiente
- OBSERVADO: el PDF se llama «propuesta para aprobación conjunta» aunque el usuario lo dio por definido. Confirmar congelamiento v1 con Persona 2 y crear `docs/contract.md` antes de cerrar el parser SMS.
- OBSERVADO: cronograma del contrato ubica el 21/11 en «Día 3» (entrega Día 7, 25/11 23:59). El alcance debe ajustarse a que el proyecto arranca recién hoy.
- PENDIENTE: sintaxis exacta del fragmento SMS (ancho fijo, expiración) y compatibilidad Keystore con RSA-OAEP/ECDH: acordar con Persona 2.

## Próxima acción mínima
- Bloque 2: fijar el codec de transporte SMS (`SS1`, anchos 12/2/2, payload 130, 99 fragmentos) con pruebas unitarias de ida y vuelta, desorden, duplicados, conflictos y expiración (ADR-0002). Sin tocar `:crypto`.
- Mantener pendiente la integración real hasta que Persona 2 entregue `:crypto` (aprox. un día, según el usuario).
