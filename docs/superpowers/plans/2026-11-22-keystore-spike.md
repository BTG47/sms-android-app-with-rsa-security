# Spike de Android Keystore — Plan de pruebas

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Determinar con evidencia ejecutable si el Samsung API 37 y el POCO API 36 admiten las operaciones RSA-OAEP y ECDH que SecureSMS necesita, sin cambiar criptografía, envelope ni arquitectura.

**Architecture:** Una suite instrumentada en `app/src/androidTest` que ejecuta operaciones reales contra `AndroidKeyStore`, registra proveedor, nivel de seguridad, parámetros, resultados y excepciones, y vuelca la matriz en `docs/keystore-spike-evidence.md`. No hay código de producción nuevo: los tests son el experimento.

**Tech Stack:** Kotlin, JUnit 4, `androidx.test:runner:1.5.2`, `androidx.test.ext:junit:1.1.5`, `java.security.KeyStore`, `KeyGenParameterSpec`, `OAEPParameterSpec`, `KeyAgreement`.

**Spec:** `docs/keystore-spike-evidence.md`, `.partner/MISSIONS.md` (ticket 03), `.partner/DECISIONS.md` (D-017 a D-021), `docs/contract.md`, `docs/adr/0004-api-31-y-strongbox-opcional.md`.

## Global Constraints

- `minSdk = 31`, `compileSdk = 37`; API 29 y 30 son `NOT TESTED` por decisión D-017, no por fallo.
- No modificar `:crypto`, interfaces públicas, perfiles RSA-2048/RSA-3072 y ECC P-256/P-384, RSA-OAEP-SHA256 + AES-256-GCM, ECDH + HKDF-SHA256 + AES-256-GCM ni el formato del envelope (D-019).
- No degradar MGF1-SHA256 a MGF1-SHA1 de forma preventiva (D-020).
- No implementar fallbacks sin incompatibilidad reproducible, causa identificada, alternativa mínima, comprobación de contrato y documentación (D-021).
- StrongBox es opcional; si se usa, debe registrarse el proveedor y nivel de seguridad reales (D-018).
- Ningún secreto, clave privada ni material derivado se escribe en logs, SMS, archivos ni reportes.
- Comentarios y nombres en español profesional, sin emojis; KDoc en API pública; cambios incrementales.
- No tocar `prueba.kt`, `prueba_assembler.kt`, `prueba_codec.jar`, `SmsFragmentCodec.main.kt` ni `gradle/gradle-daemon-jvm.properties`.
- Sin commits, push, PR ni merge sin autorización separada; una rama por ticket ya creada: `bruno/feature/keystore-spike`.
- Modo A - Recursos: concepto mínimo y ejercicio corto de Bruno antes de ampliar cada bloque de código.

## Reparto del ticket

- **HUMANO [reservado]**: conectar cada teléfono, ejecutar la suite, decidir qué se reporta y consultar a Persona 2 solo si hay incompatibilidad real.
- **COMPARTIDO**: interpretación de resultados y revisión de la matriz.
- **IA**: comandos, arnés instrumentado, matriz y documentación; nunca inventar resultados de dispositivo.

## Review Focus

- Capacidad anunciada distinto de operación ejecutable: `IdentidadDispositivoTest` registra `FEATURE_HARDWARE_KEYSTORE` y nivel, y la matriz exige resultado de ejecución por separado.
- MGF1-SHA256 rechazado por el proveedor: `RsaOaepTest` pasa `OAEPParameterSpec` explícito y registra la excepción exacta si falla.
- Tamaño RSA-3072 no admitido por StrongBox: `RsaOaepTest` ejecuta 2048 y 3072 y registra el proveedor real de cada clave.
- Curva P-384 no admitida por el TEE: `EcdhTest` ejecuta P-256 y P-384 y registra el proveedor real de cada par.
- Clave recuperada de Keystore que deja de ser usable: los dos tests de operación repiten la operación tras `keyStore.getKey(alias, null)`.

---

### Task 1: Identidad del dispositivo y capacidades anunciadas

**Files:**
- Modify: `app/build.gradle.kts` (`minSdk` y dependencias instrumentadas; ya aplicado)
- Create: `app/src/androidTest/java/com/securesms/app/keystore/IdentidadDispositivoTest.kt`

**Interfaces:**
- Consumes: `PackageManager.FEATURE_HARDWARE_KEYSTORE`, `Build.VERSION.SDK_INT`, `Build.MODEL`.
- Produces: registro en `Log.i("KeystoreSpike", ...)` con modelo, API, build, parche y nivel de funcionalidad Keystore/StrongBox; ninguna API pública nueva.

- [x] **Step 1: Escribir la prueba de identidad**

```kotlin
@Test
fun registraIdentidadYAnuncioDeKeystore() { ... }
```

Afirma que `FEATURE_HARDWARE_KEYSTORE` está presente y que `SDK_INT >= 31`; registra modelo, fabricante, build, parche de seguridad y `FeatureInfo.version` de Keystore y StrongBox.

- [x] **Step 2: Verificar que compila**

Run: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Ejecutar en dispositivo (HUMANO)**

Run: con **un solo** teléfono conectado, `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest --no-daemon` (sin `--offline`; ver incidente al final)
Expected: PASS en Samsung y en POCO; volcar el `Logcat` en la matriz.

Nota: `--tests` filtra pruebas unitarias locales, no instrumentadas (documentación oficial de Android). Por ahora solo existe esta clase instrumentada, así que ejecutar la suite completa equivale a ejecutarla. Para filtrar por clase usando `adb`:

```
/home/brunotarangogaray/Android/Sdk/platform-tools/adb shell am instrument -w -e class com.securesms.app.keystore.IdentidadDispositivoTest com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
```

### Task 2: RSA-OAEP con MGF1-SHA256 y claves persistentes

**Files:**
- Create: `app/src/androidTest/java/com/securesms/app/keystore/RsaOaepTest.kt`

**Interfaces:**
- Consumes: `KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")`, `KeyGenParameterSpec.Builder`, `Cipher.getInstance("RSA/ECB/OAEPPadding")`, `OAEPParameterSpec`, `MGF1ParameterSpec.SHA256`, `PSource.PSpecified.DEFAULT`.
- Produces: resultados por tamaño 2048/3072 con proveedor, excepción y nivel de seguridad; sin material clave en salida.

- [x] **Step 1: Escribir la prueba observacional**

Dos métodos independientes en `RsaOaepTest.kt`: `rsa2048CifraYDescifraConMgf1Sha256` y `rsa3072CifraYDescifraConMgf1Sha256`. Cada uno genera la clave con `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `ENCRYPTION_PADDING_RSA_OAEP`, `DIGEST_SHA256` y, en API 35+, `setMgf1Digests(DIGEST_SHA256)`; usa `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)` con `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`; descifra, recupera la privada con `keyStore.getKey(alias, null)` y repite. Registra proveedor y `KeyInfo.securityLevel`; no imprime secretos.

- [x] **Step 2: Verificar compilación**

Run: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline` → BUILD SUCCESSFUL.

- [x] **Step 3: Ejecutar en ambos dispositivos (HUMANO)**

Resultado 2026-11-23: Samsung `OK (2 tests)`, RSA-2048 y RSA-3072 PASS. POCO `Tests run: 2, Failures: 2`, ambos con `UNSUPPORTED_MGF_DIGEST` (`KeyStoreException: -79`) en `Cipher.init(DECRYPT_MODE, ...)`. Detalle y etiquetas en `docs/keystore-spike-evidence.md`.

Reinstalar solo el APK de prueba y filtrar por clase:

```
ADB=/home/brunotarangogaray/Android/Sdk/platform-tools/adb
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s R5CX80AMTKN shell am instrument -w -e class com.securesms.app.keystore.RsaOaepTest com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s R5CX80AMTKN logcat -d -s KeystoreSpike:I
```

Repetir con `4eeb389e`. Volcar resultados o excepciones a la matriz.

### Task 2b: Diagnóstico controlado de MGF1 (autorizado por Bruno)

**Files:**
- Create: `app/src/androidTest/java/com/securesms/app/keystore/RsaMgf1DiagnosticoTest.kt`

**Interfaces:**
- Consumes: `KeyGenParameterSpec#setMgf1Digests`, `OAEPParameterSpec`, `MGF1ParameterSpec`, `KeyInfo.getSecurityLevel`, `Cipher.provider`.
- Produces: ocho líneas `DIAG` y ocho `DIAG_EX` en logcat con etiqueta `KeystoreSpike`. Sin secretos ni claves privadas en salida; el identificador interno de clave se redacta.

**Restricciones de la autorización**: SHA-256 permanece como digest del mensaje; se prueban MGF1-SHA256 con declaración explícita en la clave, MGF1-SHA1 con alias independiente y variantes sin `setMgf1Digests`; RSA-2048 y RSA-3072; proveedor leído después de `init(clave)`; nivel de seguridad y excepciones completas. No se modifica el contrato, `:crypto` ni se implementa fallback.

- [x] **Step 1: Escribir las ocho pruebas observacionales**

`con_tag_sha256`, `con_tag_sha1`, `sin_tag_sha256` y `sin_tag_sha1`, cada una en RSA-2048 y RSA-3072. Cada escenario usa un alias propio y una clave recién generada. Las pruebas registran resultados en lugar de fallar cuando la operación es rechazada, para obtener la matriz completa en una corrida.

- [x] **Step 2: Verificar compilación**

`JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline` → BUILD SUCCESSFUL.

- [x] **Step 3: Ejecutar en ambos dispositivos (HUMANO)**

Resultado 2026-11-23: Samsung 6/8 escenarios PASS; POCO 4/8. `con_tag_sha256` (único que cumple el contrato) pasa solo en Samsung. Matriz y alternativas en `docs/keystore-spike-evidence.md`.

```
ADB=/home/brunotarangogaray/Android/Sdk/platform-tools/adb
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s R5CX80AMTKN logcat -c
$ADB -s R5CX80AMTKN shell am instrument -w -e class com.securesms.app.keystore.RsaMgf1DiagnosticoTest com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s R5CX80AMTKN logcat -d -s KeystoreSpike:I
```

Repetir con `4eeb389e`. Limpiar el logcat entre dispositivos para no mezclar corridas.

- [ ] **Step 4: Completar la tabla comparativa y valorar alternativas (COMPARTIDO)**

No se implementa la alternativa que funcione sin una decisión explícita de Bruno y acuerdo con Persona 2.

### Task 3: ECDH con curvas P-256 y P-384

**Files:**
- Create: `app/src/androidTest/java/com/securesms/app/keystore/EcdhTest.kt`

**Interfaces:**
- Consumes: `KeyProperties.PURPOSE_AGREE_KEY`, `ECGenParameterSpec`, `KeyAgreement.getInstance("ECDH", "AndroidKeyStore")`.
- Produces: comparación de secretos entre dos pares por curva, con proveedor y nivel de seguridad.

- [x] **Step 1: Escribir la prueba**

`EcdhTest.kt` contiene `ecdhP256DosParesObtienenElMismoSecreto` y `ecdhP384DosParesObtienenElMismoSecreto`. Cada una genera dos pares persistentes con `PURPOSE_AGREE_KEY` y `setAlgorithmParameterSpec(ECGenParameterSpec(...))`, ejecuta `init` + `doPhase` + `generateSecret` en ambos sentidos, repite tras recuperar las privadas de Keystore y afirma que los secretos coinciden. Registra proveedor, nivel de seguridad y longitud del secreto; nunca lo imprime. La curva se fija con `setAlgorithmParameterSpec`, según el ejemplo oficial de `KeyGenParameterSpec` en AOSP.

- [x] **Step 2: Verificar compilación**

`JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline` → BUILD SUCCESSFUL.

- [x] **Step 3: Ejecutar en ambos dispositivos (HUMANO)**

Resultado 2026-11-23: `OK (2 tests)` en Samsung y POCO. P-256 y P-384 PASS en ambos, proveedor `AndroidKeyStore`, nivel 1, secretos de 32 y 48 bytes respectivamente. Registro en `docs/keystore-spike-evidence.md`.

```
ADB=/home/brunotarangogaray/Android/Sdk/platform-tools/adb
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s R5CX80AMTKN logcat -c
$ADB -s R5CX80AMTKN shell am instrument -w -e class com.securesms.app.keystore.EcdhTest com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s R5CX80AMTKN logcat -d -s KeystoreSpike:I
```

Repetir con `4eeb389e`. Volcar los resultados a la matriz.

### Task 4: StrongBox opcional y matriz final

**Files:**
- Create: `app/src/androidTest/java/com/securesms/app/keystore/StrongBoxTest.kt`
- Modify: `docs/keystore-spike-evidence.md`

**Interfaces:**
- Consumes: `context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)`, `setIsStrongBoxBacked(true)`, `StrongBoxUnavailableException`.
- Produces: matriz PASS / FAIL / NOT TESTED por dispositivo y operación, con OBSERVADO / INFERRED / PROPUESTO.

- [x] **Step 1: Escribir la prueba opcional**

`StrongBoxTest.kt` contiene `strongBoxRsaOaepMgf1Sha256` y `strongBoxEcP256`. Cada una comprueba en tiempo de ejecución `hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)`: si no está anunciada, registra `NOT_TESTED motivo=funcionalidad_no_anunciada` y no intenta la operación (caso del POCO). Si está anunciada, genera la clave con `setIsStrongBoxBacked(true)`; `StrongBoxUnavailableException` se registra como `NO_DISPONIBLE` y cualquier otra excepción como `FAIL` con la cadena redactada. Ninguna variante falla el ticket. Se registran nivel de seguridad real (`KeyInfo.securityLevel`) y proveedor del `Cipher` después de `init(clave)`. No se degrada a claves sin protección.

- [x] **Step 2: Comandos de ejecución (HUMANO)**

```
ADB=/home/brunotarangogaray/Android/Sdk/platform-tools/adb
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s R5CX80AMTKN logcat -c
$ADB -s R5CX80AMTKN shell am instrument -w -e class com.securesms.app.keystore.StrongBoxTest com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s R5CX80AMTKN logcat -d -s KeystoreSpike:I
```

Repetir con `4eeb389e`. Esperado: Samsung `OK (2 tests)` con un anuncio y dos resultados; POCO `OK (2 tests)` con `anunciado=false` y `NOT_TESTED`.

- [x] **Step 3: Completar la matriz** con dispositivo, Android, algoritmo, configuración, proveedor, resultado, excepción y nivel de seguridad.

Resultado 2026-11-23: Samsung `OK (2 tests)`, RSA y EC PASS en nivel 2 (STRONGBOX), proveedor `AndroidKeyStoreBCWorkaround` para RSA; POCO `OK (2 tests)` con `anunciado=false` y `NOT_TESTED`. Matriz final consolidada en `docs/keystore-spike-evidence.md`.

- [ ] **Step 4: Revisión COMPARTIDA** de la matriz; decidir si hay incompatibilidad reproducible antes de proponer cualquier fallback.

## Ejecución y verificación

- Compilación sin dispositivo: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`.
- Ejecución HUMANA por dispositivo: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest --no-daemon` (**sin `--offline`**).

**Incidente 2026-11-23**: `connectedDebugAndroidTest --offline` falló porque la tarea necesita `com.android.tools.utp:gradle-work-action:32.5.2`, que no está en caché; solo está `gradle-work-action-api:32.5.2`. `assembleDebugAndroidTest --offline` sí funciona porque no usa ese artefacto. Opciones: ejecutar sin `--offline` (Gradle descarga ese único artefacto) o usar la ruta adb directa con los APK ya compilados:

```
ADB=/home/brunotarangogaray/Android/Sdk/platform-tools/adb
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s R5CX80AMTKN install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s R5CX80AMTKN shell am instrument -w com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner
```
- Regresión local: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks`; Expected: 22 pruebas, 0 fallos.
- `adb` está en `/home/brunotarangogaray/Android/Sdk/platform-tools/adb`; verificar con `adb devices -l` antes de cada ejecución.
- Si los dos teléfonos están conectados a la vez, elegir uno con `ANDROID_SERIAL=<serial>` delante de `./gradlew`, o desconectar el otro. Seriales conocidos: Samsung `R5CX80AMTKN`, POCO `4eeb389e`. `ANDROID_SERIAL` es la variable documentada por Android para seleccionar dispositivo en comandos `adb`; AGP la respeta al ejecutar tareas `connected*`.
- Resultados: consola y `app/build/reports/androidTests/connected/index.html`; XML en `app/build/outputs/androidTest-results/connected/`. Los `Log.i` con etiqueta `KeystoreSpike` se ven con `adb logcat -s KeystoreSpike:I`.
