# Evidencia preliminar del spike de Android Keystore (ticket 03)

Este documento registra la investigación del frente Android sobre la viabilidad de RSA-OAEP y ECDH con Android Keystore. La pregunta que originó el spike es si las diferencias de API y de hardware entre dispositivos constituyen un problema grave y si existen alternativas de mitigación.

**Estado: BLOQUE 1 EJECUTADO EN AMBOS DISPOSITIVOS; BLOQUE 2 CON INCOMPATIBILIDAD REPRODUCIBLE EN EL POCO; BLOQUE 3 PENDIENTE.** Las decisiones D-017 a D-021 están resueltas y el alcance en `docs/superpowers/plans/2026-11-22-keystore-spike.md`. La afirmación de que no había evidencia de un problema grave quedó matizada por una ejecución real: MGF1-SHA256 con clave privada en Keystore funciona en el Samsung y falla en el POCO. No se ha cambiado ningún algoritmo, parámetro ni archivo de producción.

## Etiquetas

- **OBSERVADO**: verificado con una fuente primaria (documentación oficial o código AOSP) o con el dispositivo conectado.
- **INFERRED**: conclusión razonable a partir de fuentes primarias, sin ejecución en el dispositivo.
- **PROPUESTO**: curso de acción pendiente de decisión o verificación.

## Inventario verificado

| Dispositivo | API | Fecha de parche | Resultado `pm list features` | Observación |
|---|---:|---|---|---|
| Samsung SM-S926B | 37 | 2026-10-05 | `hardware_keystore=300`, `strongbox_keystore=300`, `keystore.app_attest_key` | StrongBox anunciado. |
| Xiaomi POCO 23122PCD1G (`garnet`) | 36 | 2026-10-01 | `hardware_keystore=100`, `keystore.app_attest_key` | Sin `strongbox_keystore`. |
| Huawei STK-LX3 | 29 | no verificado en esta sesión | no verificado | Ya no está disponible para pruebas. |

**OBSERVADO**: el repositorio tenía `minSdk = 29` y tiene `compileSdk = 37`; no existe imagen de sistema ni plataforma API 29 instalada en el SDK local. **ACEPTADO (D-017)**: `minSdk` se ha elevado a 31 y API 29 queda fuera de cobertura por decisión deliberada, documentada como `NOT TESTED`, no como prueba fallida.

## Hallazgos sobre RSA-OAEP

**OBSERVADO**: en AOSP `android10-release` (API 29), `AndroidKeyStoreRSACipherSpi.java` declara literalmente: `RSA cipher with OAEP encryption padding. Only SHA-1 based MGF1 is supported as MGF.` El código valida `OAEPParameterSpec`, exige MGF1 y no envía ningún tag de digest MGF1 al keymaster.

**OBSERVADO**: en AOSP `android14-release`, el SPI equivalente (`keystore2/AndroidKeyStoreRSACipherSpi.java`) mantiene MGF1-SHA1 como valor por defecto, pero incorpora `mKeymasterMgf1Digest`, el método `setMgf1Digests` y el tag `KM_TAG_RSA_OAEP_MGF_DIGEST`. Es decir, la posibilidad de elegir el digest de MGF1 es una incorporación posterior.

**OBSERVADO**: la documentación de `KeyGenParameterSpec.Builder#setDigests` en AOSP indica que el conjunto de digests debe especificarse para claves RSA usadas con OAEP porque esas operaciones involucran un digest.

**INFERRED**: OAEP con SHA-256 como digest del mensaje y MGF1-SHA1 implícito es la combinación que la SPI de API 29 reconoce. OAEP con MGF1-SHA256 no debe asumirse en API 29; requiere verificación en dispositivo y, previsiblemente, API 31 o superior.

## Hallazgos sobre ECDH

**OBSERVADO**: en AOSP `android10-release` (API 29) y `android11-release` (API 30), `KeyProperties.java` no contiene la constante `PURPOSE_AGREE_KEY`.

**OBSERVADO**: en AOSP `android12-release` (API 31/32) aparecen por primera vez `PURPOSE_AGREE_KEY` y el archivo `keystore/java/android/security/keystore2/AndroidKeyStoreKeyAgreementSpi.java`. Ese mismo branch incluye el ejemplo de ECDH con `KeyAgreement.getInstance("ECDH", "AndroidKeyStore")` en `KeyGenParameterSpec.java`.

**OBSERVADO**: la SPI valida que la clave privada provenga de Android Keystore, que la contraparte sea una clave pública y que ambas curvas coincidan.

**INFERRED**: ECDH gestionado por Android Keystore **no está disponible en API 29 ni en API 30**. En esas versiones no existe el propósito de clave ni la SPI que implementa el acuerdo. No es una restricción de fabricante: es una limitación de la plataforma.

## StrongBox

**OBSERVADO**: el Samsung anuncia `strongbox_keystore=300`; el POCO no anuncia StrongBox. Esto confirma heterogeneidad de hardware incluso entre dispositivos recientes.

**OBSERVADO**: `KeyGenParameterSpec.Builder#setIsStrongBoxBacked` existe desde API 28, y la documentación de la plataforma define `StrongBoxUnavailableException` para el caso en que el hardware no esté disponible.

**INFERRED**: StrongBox debe tratarse como una optimización opcional. La ausencia de StrongBox no invalida Android Keystore; la implementación debe degradar a TEE/mkeystore estándar sin cambiar el formato de mensaje ni las interfaces criptográficas.

## Alternativas y mitigaciones

1. **Verificación temprana y explícita**: ejecutar una prueba de capacidades en cada dispositivo antes de inicializar la criptografía real. Debe incluir generación de clave RSA OAEP, cifrado/descifrado de prueba, generación de clave EC con `PURPOSE_AGREE_KEY` y acuerdo ECDH.
2. **Detección de características en tiempo de ejecución**: usar `PackageManager.hasSystemFeature` para registrar StrongBox y versión de Keystore, sin rechazar dispositivos por no tener StrongBox.
3. **Selección controlada de parámetros de OAEP**: verificar MGF1-SHA256 de forma explícita en ambos dispositivos (D-020); no degradar preventivamente a MGF1-SHA1.
4. **Ruta ECDH**: resuelta por D-017 al elevar `minSdk` a 31. No se gestiona la clave efímera fuera de Keystore ni se cambia el mecanismo de intercambio, porque ambos teléfonos están por encima de API 31 y no hay incompatibilidad observada.
5. **Cobertura API 29**: resuelta por D-017. No se busca tercer dispositivo ni se instala imagen 29; la ausencia se registra como limitación de compatibilidad deliberada.

## Respuesta a la pregunta de Bruno

**Sí, hay un problema real, pero no es un callejón sin salida.** El hallazgo importante no es StrongBox ni la heterogeneidad de fabricante: es que **ECDH gestionado por Android Keystore no existe en API 29 ni en API 30**. La evidencia es de código AOSP: `PURPOSE_AGREE_KEY` y `AndroidKeyStoreKeyAgreementSpi` aparecen por primera vez en `android12-release`. Eso convierte el soporte de API 29 en una decisión de diseño, no en un detalle de configuración.

**RSA-OAEP, en cambio, sí parece viable en API 29**, con una salvedad concreta: la SPI de `android10-release` solo admite MGF1 basado en SHA-1. OAEP con SHA-256 como digest del mensaje y MGF1-SHA1 implícito es la combinación coherente con esa versión; MGF1-SHA256 requiere una API posterior. Esto debe verificarse en dispositivo antes de darlo por bueno.

**Sí existen alternativas, y no son una sola.** Para StrongBox, la mitigación es degradar a TEE estándar. Para OAEP, la mitigación es fijar parámetros compatibles. Para ECDH en API 29, las opciones son elevar `minSdk` a 31, gestionar la clave efímera fuera de Keystore solo en API 29, o acordar otro mecanismo con Persona 2. La última palabra no es técnica únicamente: el contrato v0.2 dice que las claves van en Keystore «donde resulte viable» y exige acordar cualquier fallback, así que este punto necesita una decisión explícita.

**No puedo afirmar que RSA-OAEP o ECDH funcionen en API 29 sin una ejecución real.** La evidencia documental es fuerte, pero el ticket pide evidencia ejecutable. Además, los dos dispositivos disponibles son API 37 y 36; no hay cobertura API 29.

## Criterio de Bruno sobre la investigación

**ACEPTADO**: no existe, por ahora, evidencia de un problema grave que impida continuar. Las diferencias entre dispositivos deben validarse experimentalmente, pero no justifican modificar algoritmos, protocolo criptográfico ni arquitectura. Las funcionalidades anunciadas por el hardware Keystore no sustituyen una prueba ejecutable. Esta valoración se registró en D-017 a D-021 y en ADR-0004.

Nota de precisión terminológica: **capacidad anunciada** es lo que el módulo de hardware declara mediante `PackageManager`; **resultado observado** es lo que una operación ejecutada realmente logra; **hipótesis** es una conclusión sin ejecutar. La API de Keystore expuesta a la app y el nivel de funcionalidad del hardware son capas distintas: la segunda puede anunciar ECDH aunque la primera solo lo exponga desde API 31. Por eso D-017 resuelve la capa de API y el spike verifica la de hardware.

## Decisiones aplicadas

- **D-017**: `minSdk = 31`; API 29 queda como limitación deliberada, no como fallo.
- **D-018**: StrongBox opcional, con registro del proveedor y nivel de seguridad reales.
- **D-019**: sin cambios en `:crypto`, perfiles, envelope ni responsabilidades de Persona 2.
- **D-020**: MGF1-SHA256 se prueba explícitamente; no se cambia a SHA-1 de forma preventiva.
- **D-021**: sin fallbacks hasta reproducible + causa identificada + alternativa mínima + acuerdo + documentación.

## Resultados ejecutados

### Bloque 1 — Identidad y capacidades anunciadas

| Dispositivo | Ejecución | Registro | Estado |
|---|---|---|---|
| Samsung SM-S926B | `adb shell am instrument -w com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner` → `OK (1 test)` | `dispositivo=SM-S926B fabricante=samsung api=37`; `build=CP2A.260605.016.S926BXXSIEZJ1 parche=2026-10-05`; `hardwareKeystore=300 strongBox=300` | **OBSERVADO** 2026-11-23 00:12:58 |
| POCO 23122PCD1G | `adb shell am instrument -w com.securesms.app.test/androidx.test.runner.AndroidJUnitRunner` → `OK (1 test)` | `dispositivo=23122PCD1G fabricante=Xiaomi api=36`; `build=BP2A.250605.031.A3 parche=2026-10-01`; `hardwareKeystore=100 strongBox=ausente` | **OBSERVADO** 2026-11-23 00:21:32 |

**OBSERVADO**: el bloqueo inicial de instalación en el POCO era de autorización USB de MIUI/HyperOS, no del Keystore. Se resolvió activando «Instalar mediante USB» y aceptando el diálogo del dispositivo; después la prueba pasó.

**OBSERVADO**: los valores de logcat confirman lo que ya se había leído con `pm list features` (hardware 300, StrongBox 300). La prueba se ejecutó dentro del proceso de la app, sin abrir la interfaz y sin enviar SMS.

### Bloque 2 — RSA-OAEP con MGF1-SHA256

La prueba exige lo que el contrato v0.2 establece en las líneas 26 y 94: `RSA-OAEP con SHA-256/MGF1-SHA256` y `PSource vacío`. Usa `setMgf1Digests(DIGEST_SHA256)` al generar la clave (API 35+) y `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)` en cada `Cipher`.

| Dispositivo | Tamaño | Resultado | Registro |
|---|---|---|---|
| Samsung SM-S926B | RSA-2048 | **OBSERVADO — PASS** | `OK (2 tests)`; `RSA-2048 proveedor=AndroidOpenSSL nivel=1 mgf1=SHA-256` |
| Samsung SM-S926B | RSA-3072 | **OBSERVADO — PASS** | misma corrida; `RSA-3072 proveedor=AndroidOpenSSL nivel=1 mgf1=SHA-256` |
| POCO 23122PCD1G | RSA-2048 | **OBSERVADO — FAIL** | `InvalidKeyException: Keystore operation failed` → `KeyStoreException: -79` → `Error::Km(UNSUPPORTED_MGF_DIGEST)` |
| POCO 23122PCD1G | RSA-3072 | **OBSERVADO — FAIL** | misma excepción y código, en `Cipher.init(DECRYPT_MODE, ...)` |

**OBSERVADO**: en el POCO fallan los dos tamaños, en la misma línea (`RsaOaepTest.kt:94`, inicialización del descifrado), con el mismo código `-79` y el mismo `UNSUPPORTED_MGF_DIGEST`. El cifrado con la clave pública de la línea 53 completó antes del fallo. Es una incompatibilidad reproducible, no un fallo intermitente ni de instalación.

**OBSERVADO**: en el Samsung el resultado PASS aparece dos veces en el logcat con marcas de tiempo distintas (00:35:09 y 00:36:21), lo que indica dos corridas con el mismo resultado.

**OBSERVADO**: en AOSP `android15-release`, `KeyProperties.java` define `SECURITY_LEVEL_SOFTWARE = 0`, `SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1` y `SECURITY_LEVEL_STRONGBOX = 2`. Por tanto `nivel=1` en el Samsung significa **TRUSTED_ENVIRONMENT**: las claves RSA de esa prueba están protegidas por TEE, no por software. Falta el `nivel` del POCO porque Bruno no volcó su logcat después del bloque 2.

**OBSERVADO**: el proveedor registrado se leyó con `Cipher.getInstance(...).provider.name` **antes** de `init(clave)`, por lo que no identifica con certeza el proveedor que ejecutó la operación: `Cipher` puede cambiar de proveedor al inicializar con una clave de Keystore. La próxima medición debe hacerse después de `init`.

**INFERRED — hipótesis principal**: AOSP `android15-release`, `AndroidKeyStoreRSACipherSpi.java` línea 168 fija MGF1-SHA1 como digest por defecto; la línea 243 lee el digest de `OAEPParameterSpec`; las líneas 309-318 envían `KM_TAG_RSA_OAEP_MGF_DIGEST` a `begin()` **solo si ese tag está presente en las propiedades de la clave**. Nuestra clave lo tiene porque usamos `setMgf1Digests`. El POCO, con `hardware_keystore=100`, rechaza el tag con `UNSUPPORTED_MGF_DIGEST`; el Samsung, con `hardware_keystore=300`, lo acepta. La diferencia de nivel de hardware es la explicación más probable, pero aún no está demostrada con una prueba que varíe una sola variable.

**INFERRED — riesgo de falsa corrección**: si se omitiera `setMgf1Digests` al generar la clave, el SPI de AOSP no enviaría el tag a `begin()` y la operación podría completarse usando MGF1-SHA1 en hardware. Eso sería un PASS engañoso: satisfaría la ejecución pero no el contrato, que exige MGF1-SHA256. Por eso no se propone ese cambio como solución.

### Criterio aplicado a D-021

La condición de fallo reproducible está cumplida en el POCO. La causa técnica está identificada con un código de error concreto, pero falta comprobar la alternativa mínima antes de hablar de fallback: la única alternativa viable, degradar a MGF1-SHA1, contradice D-020 y el contrato v0.2, así que requiere acuerdo con Persona 2. No se ha implementado ningún fallback.

### Bloque 2b — Diagnóstico controlado de MGF1 (preparado, pendiente de ejecución)

Bruno autorizó esta comparación sin autorizar cambios al contrato v0.2, a `:crypto` ni un fallback definitivo. Arnés en `app/src/androidTest/java/com/securesms/app/keystore/RsaMgf1DiagnosticoTest.kt`, compilado con BUILD SUCCESSFUL.

| Escenario | Digest del mensaje | `setMgf1Digests` en la clave | Digest MGF1 solicitado al `Cipher` | Alias |
|---|---|---|---|---|
| `con_tag_sha256` | SHA-256 | SHA-256 | SHA-256 | `spike_diag_<bits>_con_tag_sha256` |
| `con_tag_sha1` | SHA-256 | SHA-1 | SHA-1 | `spike_diag_<bits>_con_tag_sha1` |
| `sin_tag_sha256` | SHA-256 | ausente | SHA-256 | `spike_diag_<bits>_sin_tag_sha256` |
| `sin_tag_sha1` | SHA-256 | ausente | SHA-1 | `spike_diag_<bits>_sin_tag_sha1` |

Cada escenario se ejecuta en RSA-2048 y RSA-3072, con clave nueva y alias independiente. Se registran proveedor **después** de `init(clave)`, `KeyInfo.getSecurityLevel()`, `KeyInfo.getDigests()` y la cadena completa de excepciones. El identificador interno de clave se redacta. No se imprimen claves privadas ni material sensible.

**OBSERVADO**: `KeyInfo` no expone el digest de MGF1 (AOSP `android15-release`, método `getKeySpec`), así que el parámetro efectivo no puede leerse directamente. La comparación entre `sin_tag_sha256` y `sin_tag_sha1` permite inferirlo por comportamiento diferencial.

Orden de ejecución (HUMANO): reinstalar el APK de prueba, limpiar logcat, ejecutar `-e class com.securesms.app.keystore.RsaMgf1DiagnosticoTest`, volcar `logcat -d -s KeystoreSpike:I`. Repetir en ambos teléfonos. Comandos exactos en `docs/superpowers/plans/2026-11-22-keystore-spike.md`.

### Resultados del bloque 2b en POCO 23122PCD1G (OBSERVADO 2026-11-23 01:23-01:24)

Ocho escenarios ejecutados, API 36, `nivel=1` (TRUSTED_ENVIRONMENT) en los ocho, `digestsClave=SHA-256` en los ocho, cifrado con clave pública completado en los ocho.

| Escenario | RSA | Descifrado | Proveedor tras `init` | Excepción |
|---|---:|---|---|---|
| `con_tag_sha1` | 2048 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `con_tag_sha1` | 3072 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `sin_tag_sha1` | 2048 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `sin_tag_sha1` | 3072 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `con_tag_sha256` | 2048 | FAIL | no fiable (ver nota) | `InvalidKeyException` → `-79 UNSUPPORTED_MGF_DIGEST` en `begin` |
| `con_tag_sha256` | 3072 | FAIL | no fiable (ver nota) | `InvalidKeyException` → `-79 UNSUPPORTED_MGF_DIGEST` en `begin` |
| `sin_tag_sha256` | 2048 | FAIL | AndroidKeyStoreBCWorkaround | `IllegalBlockSizeException` → `-1000 UNKNOWN_ERROR`, `Finish failed` en `finish` |
| `sin_tag_sha256` | 3072 | FAIL | AndroidKeyStoreBCWorkaround | `IllegalBlockSizeException` → `-1000 UNKNOWN_ERROR`, `Finish failed` en `finish` |

La recuperación de la clave desde Keystore y el segundo descifrado reproducen el resultado en los ocho casos.

**OBSERVADO — corrección de hipótesis**: mi sospecha de que omitir `setMgf1Digests` produciría un PASS falso era incorrecta. `sin_tag_sha256` falla de forma explícita con error de padding en `finish` (`-1000`), no pasa silenciosamente. La variación controlada permite afirmar que el hardware del POCO no aplica MGF1-SHA256 con una clave privada en Keystore: cuando la clave declara el tag lo rechaza en `begin` (`-79`), y cuando no lo declara el descifrado no coincide con el cifrado (`-1000`).

**OBSERVADO**: el POCO también reporta `nivel=1` (TEE), igual que el Samsung. La diferencia entre dispositivos no es software frente a TEE, sino la capacidad del módulo anunciado (`hardware_keystore=100` frente a `300`) para el tag `KM_TAG_RSA_OAEP_MGF_DIGEST`.

**OBSERVADO — limitación de medición**: en los casos donde `init` falla, `cipher.provider` devuelve el proveedor de la última combinación intentada por `Cipher`, no el que ejecutó la operación. Por eso `con_tag_sha256` muestra `AndroidOpenSSL`. El proveedor solo se considera evidencia en los casos con descifrado exitoso. El cifrado usa `AndroidOpenSSL` en los ocho casos porque opera con la clave pública.

**INFERRED**: para que un envelope sea descifrable, emisor y receptor deben usar el mismo digest de MGF1. Si el frente Android usara MGF1-SHA1 en el POCO, `:crypto` tendría que usar el mismo valor; de lo contrario el contrato v0.2 quedaría roto en la interoperabilidad, aunque cada lado funcione por separado.

### Resultados del bloque 2b en Samsung SM-S926B (OBSERVADO 2026-11-23 01:36)

Ocho escenarios ejecutados, API 37, `nivel=1` (TRUSTED_ENVIRONMENT) en los ocho, `digestsClave=SHA-256` en los ocho, cifrado con clave pública completado en los ocho.

| Escenario | RSA | Descifrado | Proveedor tras `init` | Excepción |
|---|---:|---|---|---|
| `con_tag_sha256` | 2048 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `con_tag_sha256` | 3072 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `con_tag_sha1` | 2048 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `con_tag_sha1` | 3072 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `sin_tag_sha1` | 2048 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `sin_tag_sha1` | 3072 | PASS | AndroidKeyStoreBCWorkaround | ninguna |
| `sin_tag_sha256` | 2048 | FAIL | no fiable | `InvalidKeyException` → `-78 INCOMPATIBLE_MGF_DIGEST` en `begin` |
| `sin_tag_sha256` | 3072 | FAIL | no fiable | `InvalidKeyException` → `-78 INCOMPATIBLE_MGF_DIGEST` en `begin` |

La redacción del identificador de clave funcionó: el logcat muestra `KeyIdGuard(REDACTADO)`.

### Matriz comparativa cruzada

| Escenario | Samsung API 37, hardware 300 | POCO API 36, hardware 100 | Lectura |
|---|---|---|---|
| `con_tag_sha256` | PASS (2048 y 3072) | FAIL `-79 UNSUPPORTED_MGF_DIGEST` (2048 y 3072) | El módulo del POCO no conoce MGF1-SHA256; el del Samsung sí |
| `sin_tag_sha256` | FAIL `-78 INCOMPATIBLE_MGF_DIGEST` en `begin` | FAIL `-1000 UNKNOWN_ERROR` en `finish` | Ambos rechazan pedir SHA-256 si la clave solo autoriza SHA-1; Samsung lo detecta antes y con código distinto |
| `con_tag_sha1` | PASS (2048 y 3072) | PASS (2048 y 3072) | MGF1-SHA1 declarado funciona en ambos |
| `sin_tag_sha1` | PASS (2048 y 3072) | PASS (2048 y 3072) | MGF1-SHA1 por defecto funciona en ambos |

**OBSERVADO**: 6 de 8 escenarios pasan en el Samsung y 4 de 8 en el POCO. La única configuración que satisface el contrato v0.2 —`con_tag_sha256`— pasa en el Samsung y falla en el POCO. Las cuatro combinaciones con MGF1-SHA1 pasan en ambos dispositivos.

**OBSERVADO**: los dos dispositivos reportan `nivel=1`, por lo que la diferencia no es software frente a TEE. Los códigos de error divergen (`-78 INCOMPATIBLE_MGF_DIGEST` frente a `-79 UNSUPPORTED_MGF_DIGEST` y `-1000`), lo que apunta a implementaciones distintas del módulo Keystore.

**INFERRED**: la capacidad del módulo anunciado (`hardware_keystore=300` frente a `100`) es la explicación más probable de la divergencia. No está aislada experimentalmente de la diferencia de API, porque no hay dos dispositivos con la misma API y distinto hardware. El contraste documental sigue siendo coherente: `KM_TAG_RSA_OAEP_MGF_DIGEST` es una incorporación posterior a KeyMint 1.0.

**INFERRED — consecuencia de interoperabilidad**: cualquier cambio a MGF1-SHA1 debe aplicarse también en `:crypto`. Si solo cambiara el frente Android, el envelope cifrado por la JVM no sería descifrable en el POCO, aunque las pruebas individuales de cada lado pasaran.

### Alternativas viables identificadas (sin implementar)

| Alternativa | Ventaja | Limitación | Efecto en el contrato v0.2 |
|---|---|---|---|
| Mantener MGF1-SHA256 y limitar el despliegue al Samsung | Criptografía y parámetros intactos | El POCO queda fuera; contradice D-009 (dos teléfonos propios para pruebas) y reduce cobertura | Sin cambio de parámetros; sí de alcance de dispositivos |
| MGF1-SHA1 en Android y en `:crypto` | Funciona en los dos teléfonos; conserva SHA-256 como digest del mensaje | Requiere acuerdo con Persona 2 y regenerar vectores de prueba | Modifica líneas 26 y 94; contradice D-020 salvo autorización explícita |
| Negociar el digest de MGF1 por dispositivo durante el intercambio de claves | Conserva SHA-256 donde el hardware lo admite | Añade estado al protocolo y aumenta la superficie de prueba | Cambia el envelope o el intercambio (líneas 83-87); D-019 lo prohíbe sin acuerdo |
| Clave privada fuera de Keystore en el POCO | Mantiene MGF1-SHA256 en el mensaje | Degrada la protección de la clave privada; el contrato prohíbe exportarla | Cambio de seguridad, no de formato; requiere acuerdo |

### Bloque 3 — ECDH con P-256 y P-384 (OBSERVADO 2026-11-23 01:41-01:42)

| Dispositivo | Curva | Ejecución | Proveedor | Nivel | Longitud del secreto |
|---|---|---|---|---:|---:|
| Samsung SM-S926B | P-256 | `OK (2 tests)` / PASS | AndroidKeyStore | 1 | 32 bytes |
| Samsung SM-S926B | P-384 | PASS | AndroidKeyStore | 1 | 48 bytes |
| POCO 23122PCD1G | P-256 | `OK (2 tests)` / PASS | AndroidKeyStore | 1 | 32 bytes |
| POCO 23122PCD1G | P-384 | PASS | AndroidKeyStore | 1 | 48 bytes |

Cada prueba genera dos pares persistentes con `PURPOSE_AGREE_KEY`, ejecuta el acuerdo en ambos sentidos, repite tras recuperar las privadas de Keystore y afirma que los secretos coinciden. El contenido del secreto nunca se registró: solo su longitud.

**OBSERVADO**: ECDH con claves gestionadas por Android Keystore funciona en ambos dispositivos para las dos curvas del contrato, con proveedor `AndroidKeyStore` y nivel TEE (`1`). No hay incompatibilidad que reportar en este bloque.

**Límite de alcance**: el spike verifica el acuerdo, no HKDF-SHA256 ni AES-256-GCM. Esa derivación y el formato del envelope siguen siendo responsabilidad de `:crypto` (D-019).

### Bloque 4 — StrongBox opcional (OBSERVADO 2026-11-23 01:46-01:47)

| Dispositivo | Feature anunciado | Operación | Resultado | Nivel | Proveedor |
|---|---|---|---|---:|---|
| Samsung SM-S926B | `true` | RSA-2048 OAEP MGF1-SHA256 | PASS, plano coincidente | 2 (STRONGBOX) | AndroidKeyStoreBCWorkaround |
| Samsung SM-S926B | `true` | ECDH P-256 | PASS, 32 bytes coincidentes | 2 (STRONGBOX) | — |
| POCO 23122PCD1G | `false` | RSA y EC | `NOT_TESTED motivo=funcionalidad_no_anunciada` | — | — |

`OK (2 tests)` en ambos dispositivos. El POCO no anunció `FEATURE_STRONGBOX_KEYSTORE` y el arnés no intentó generar claves, conforme a D-018.

**OBSERVADO**: el StrongBox del Samsung admite MGF1-SHA256 (nivel 2), igual que su TEE (nivel 1). No hay degradación silenciosa ni claves sin protección: el nivel reportado es el real en cada caso.

**OBSERVADO**: StrongBox no resuelve la incompatibilidad del POCO, porque ese dispositivo no anuncia la funcionalidad. La unica configuracion que cumple el contrato y funciona en los dos telefonos sigue sin existir.

### Matriz final consolidada

| Bloque | Operacion | Samsung API 37 / HW 300 / StrongBox | POCO API 36 / HW 100 / sin StrongBox |
|---|---|---|---|
| 1 | Identidad y capacidades anunciadas | PASS | PASS |
| 2 | RSA-OAEP MGF1-SHA256 en TEE | PASS (2048 y 3072) | FAIL `-79 UNSUPPORTED_MGF_DIGEST` |
| 2b | Diagnostico `con_tag_sha256` en TEE | PASS (2048 y 3072) | FAIL `-79` en `begin` |
| 2b | Diagnostico `sin_tag_sha256` en TEE | FAIL `-78 INCOMPATIBLE_MGF_DIGEST` | FAIL `-1000 UNKNOWN_ERROR` en `finish` |
| 2b | MGF1-SHA1, con y sin tag, en TEE | PASS (2048 y 3072) | PASS (2048 y 3072) |
| 3 | ECDH P-256 y P-384 en TEE | PASS (32 y 48 bytes) | PASS (32 y 48 bytes) |
| 4 | RSA-OAEP MGF1-SHA256 en StrongBox | PASS, nivel 2 | NOT TESTED (feature ausente) |
| 4 | ECDH P-256 en StrongBox | PASS, nivel 2 | NOT TESTED (feature ausente) |

**OBSERVADO**: ECDH y AES-GCM-related keypaths no presentan incompatibilidad. La unica divergencia reproducible es RSA-OAEP con MGF1-SHA256 usando la clave privada en el Keystore del POCO.

**INFERRED**: la causa es la capacidad del modulo Keystore del POCO (`hardware_keystore=100`, KeyMint anterior al soporte de `KM_TAG_RSA_OAEP_MGF_DIGEST`), no el nivel de API ni el uso de TEE, porque ambos dispositivos reportan `nivel=1` en TEE y el Samsung admite el mismo tag tanto en TEE como en StrongBox. No esta aislada de la diferencia de API por falta de un tercer dispositivo; se solicito uno y se descarto (D-017).

**PROPUESTO — ninguna alternativa implementada**: las cuatro opciones de la seccion anterior siguen abiertas a decision de Bruno. Cualquier cambio a MGF1-SHA1 debe acordarse con Persona 2 y aplicarse simultaneamente en `:crypto`.

## Pendiente de decision (no de ejecucion)

- Someter la matriz y las cuatro alternativas a valoracion COMPARTIDA con Bruno (HUMANO/COMPARTIDO).
- Si se decide cambiar el digest de MGF1, solicitar acuerdo explicito de Persona 2 antes de tocar `:crypto` o el contrato v0.2.
- No se ha implementado ningun fallback, degradation ni cambio de parametros criptograficos.
