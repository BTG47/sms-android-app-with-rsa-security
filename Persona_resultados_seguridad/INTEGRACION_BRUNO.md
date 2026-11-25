# Integración con la app Android (para Bruno)

## 1. Agregar el módulo

Opción A (recomendada, mismo repositorio): copiar la carpeta `crypto/` a la raíz del proyecto Android y en
`settings.gradle.kts` agregar `include(":crypto")`. En `app/build.gradle.kts`:

```kotlin
dependencies { implementation(project(":crypto")) }
```

El `crypto/build.gradle.kts` aplica `kotlin("jvm")` **sin versión**, así usa la versión de Kotlin que ya
declara el proyecto Android. Si el proyecto aún no tiene el plugin JVM en el classpath, en el
`build.gradle.kts` raíz: `plugins { kotlin("jvm") version "<misma versión que kotlin-android>" apply false }`.

Opción B: `./gradlew :crypto:jar` y copiar `crypto/build/libs/securesms-crypto-1.0.1.jar` a `app/libs/`
(`implementation(files("libs/securesms-crypto-1.0.1.jar"))`).

Requisito: `minSdk 26` (usa `java.util.Base64`). No necesita permisos ni dependencias extra.

## 2. Flujo

```
Enviar:  texto ─▶ SecureSmsCrypto.encryptText(texto, clavePublicaContacto, perfil) ─▶ bytes
         bytes ─▶ (Bruno) Base64 ─▶ prefijo propio + fragmentación SMS ─▶ SmsManager
Recibir: SMS ─▶ (Bruno) reensamblar TODOS los fragmentos ─▶ Base64 decode ─▶ bytes
         bytes ─▶ SecureSmsCrypto.decryptText(bytes, miKeyPair) ─▶ texto  (o SecureSmsException)
```

* El formato binario es mío; la codificación a texto, el prefijo para reconocer mensajes SecureSMS y la
  fragmentación son tuyos. **No intentes descifrar si falta un fragmento**: si lo haces, recibirás
  `TRUNCATED_ENVELOPE` o `AUTHENTICATION_FAILED`, nunca texto parcial.
* `SecureSmsCrypto.inspect(bytes).profile` te dice qué clave propia usar antes de descifrar.
* El tamaño exacto antes de cifrar: `Envelope.sizeFor(perfil, texto.toByteArray().size)`.
  Segmentos estimados: `SmsEstimator.segmentsForEnvelopeBase64(tamaño)` (el real es el de `divideMessage`).

## 3. Mapeo de errores a estados de la UI

| Código | Estado sugerido |
|---|---|
| `TRUNCATED_ENVELOPE` | incompleto |
| `AUTHENTICATION_FAILED` | error: alterado o clave equivocada |
| `WRONG_RECIPIENT` | error: cifrado para otra clave |
| `KEY_PROFILE_MISMATCH` | error: falta la clave de ese algoritmo |
| `MALFORMED_ENVELOPE`, `UNSUPPORTED_VERSION`, `UNKNOWN_PROFILE`, `INVALID_EPHEMERAL_KEY` | error: formato inválido |
| `INVALID_KEY` (al importar) | clave pública inválida |

Ver `android-snippets/CryptoAdapterExample.kt` con un adaptador completo.

## 4. Claves y Android Keystore

* Las funciones de descifrado aceptan `publicKey` y `privateKey` por separado; la privada puede ser una clave
  **no exportable** de Android Keystore porque solo se usa vía `Cipher` / `KeyAgreement`.
  * RSA: la biblioteca usa OAEP con SHA-256 y **MGF1-SHA-1**, justamente la combinación que Keystore soporta
    en todos los niveles de API. Genera la clave con `KeyGenParameterSpec.Builder(alias, PURPOSE_DECRYPT)`
    `.setKeySize(2048 o 3072).setDigests(DIGEST_SHA256, DIGEST_SHA1).setEncryptionPaddings(ENCRYPTION_PADDING_RSA_OAEP)`.
  * ECC: ECDH dentro de Keystore requiere `PURPOSE_AGREE_KEY` (Android 12, API 31+).
    `KeyGenParameterSpec.Builder(alias, PURPOSE_AGREE_KEY).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))`.
  * La pública se obtiene con `keyStore.getCertificate(alias).publicKey`.
* Alternativa sencilla para cualquier API: generar con `SecureSmsCrypto.generateKeyPair`, exportar con
  `PrivateKeys.exportPkcs8` y guardar esos bytes cifrados con una clave AES de Keystore
  (o `EncryptedSharedPreferences`). Esto también hay que probarlo en el teléfono (tu parte).
* **Nunca** enviar la clave privada por SMS. Para compartir la pública: `PublicKeys.exportCompact(...)`
  y mostrar `PublicKeys.fingerprint(...).short` para que ambos la comparen en persona.

## 4b. Configuración RSA-OAEP acordada (contrato v0.3)

OAEP digest **SHA-256**, MGF1 **SHA-1**, etiqueta vacía (`PSource.PSpecified.DEFAULT`); contenido **AES-256-GCM**; RSA 2048/3072; ECC P-256/P-384 sin cambios. El formato del envelope y la API pública no cambian. La biblioteca ya usaba exactamente esta configuración desde la v1.0.0. En la v1.0.1 se agregó `OaepParametersTest`, que la deja fija y prueba la interoperabilidad en ambos sentidos. En Android, usa siempre el spec explícito al hacer `init`:

```kotlin
val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)
```

Los `vectors.json` siguen siendo válidos: no hubo que regenerarlos.

## 5. Vectores de prueba (`test-vectors/vectors.json`)

4 pares de claves **solo de prueba** (una por perfil, privada incluida a propósito) y 56 envelopes:
por perfil, 4 válidos (vacío, corto, 160 caracteres, Unicode con emoji) y 10 inválidos (ciphertext, tag y
key id alterados, versión 2, perfil 0x7F, magic incorrecto, fragmento faltante, último byte faltante,
destinatario equivocado, y bloque de clave/punto efímero alterado), cada uno con `expected_result`
(`OK` o el código de error) y `expected_plaintext`. Copia el archivo a `app/src/androidTest/assets/` y corre
`android-snippets/VectorsInstrumentedTest.kt`: si pasa, la biblioteca se comporta igual en el teléfono que en PC.

## 6. Benchmark en el teléfono

`android-snippets/CryptoBenchmarkInstrumentedTest.kt` corre `BenchmarkRunner` con la configuración rápida
(15 generaciones de clave, 150 repeticiones por operación) y guarda `timings.csv`, `sizes.csv` y
`environment.txt` (modelo, Android, SoC). Teléfono cargando, pantalla encendida, sin otras apps.
Mándame la carpeta y la proceso con `python3 analysis/make_charts.py results/jvm results/android-<modelo>`
(genera también la figura PC vs teléfono).
