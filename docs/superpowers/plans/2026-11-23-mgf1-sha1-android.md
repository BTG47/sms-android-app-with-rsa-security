# Adopción de MGF1-SHA1 en el frente Android — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Registrar el acuerdo con Persona 2, fijar MGF1-SHA1 explícito en el arnés Android y preparar la interoperabilidad futura, sin tocar código de producción ni el PDF canónico.

**Architecture:** Las decisiones internas y el ADR-0005 pasan a ser la fuente de verdad del acuerdo. Un helper instrumentado concentra los parámetros OAEP acordados y la distinción API 31–34 frente a API 35+; un test nuevo verifica RSA-2048/3072 en TEE y, cuando se anuncia, en StrongBox. El arnés del spike se conserva intacto porque es la evidencia del fallo con MGF1-SHA256.

**Tech Stack:** Kotlin, JUnit 4, `androidx.test:runner:1.5.2`, `androidx.test.ext:junit:1.1.5`, `KeyGenParameterSpec#setMgf1Digests` (API 35+), `OAEPParameterSpec`, `MGF1ParameterSpec.SHA1`, Android Keystore.

**Spec:** `docs/keystore-spike-evidence.md` (matriz final consolidada) y la confirmación directa de Bruno y Persona 2, registrada como D-022 y D-023 en este plan.

## Global Constraints

- `minSdk = 31`, `compileSdk = 37`; API 29 y 30 son `NOT TESTED` por decisión D-017.
- RSA-OAEP acordado: digest del mensaje `SHA-256`, función de máscara `MGF1`, digest de MGF1 `SHA-1`, cifrado de contenido `AES-256-GCM`, claves RSA de 2048 y 3072 bits.
- ECC P-256 y P-384 sin modificaciones; perfiles, interfaces públicas y formato del envelope intactos (D-019).
- StrongBox sigue siendo opcional y Android Keystore el mecanismo principal; nunca se degrada a claves privadas sin protección (D-018).
- La copia `docs/contract.md` queda **pendiente** por decisión de Bruno: el PDF v0.2 sigue siendo canónico y no se emitirá uno nuevo; el acuerdo directo prevalece para este cambio (D-001, D-023).
- No se modifica `:crypto`; Persona 2 implementa su módulo por su cuenta y su entrega aún no ha llegado.
- Ningún secreto, clave privada ni material derivado se escribe en logs, SMS, archivos ni reportes.
- Comentarios y nombres en español profesional, sin emojis; KDoc en API pública; cambios incrementales.
- Modo A - Recursos: concepto mínimo y ejercicio corto de Bruno antes de ampliar código.
- Sin commits, push, PR ni merge sin autorización separada; rama actual `bruno/feature/keystore-spike`.
- No tocar `prueba.kt`, `prueba_assembler.kt`, `prueba_codec.jar`, `SmsFragmentCodec.main.kt` ni `gradle/gradle-daemon-jvm.properties`.

## Review Focus

- Una clave generada en API 31–34 no puede declarar `setMgf1Digests`; el helper debe aplicar el tag solo en API 35+ y documentar el default de Keystore, sin que parezca una omisión → `generadorRsa` en Task 2 y apartado API en ADR-0005.
- Leer `Cipher.provider` después de un `init` fallido da un proveedor engañoso → el test solo registra proveedor cuando el descifrado termina en `PASS`.
- Un envelope producido con los parámetros v0.2 no es descifrable con los nuevos y no hay campo en el envelope que lo indique → apartado de incompatibilidad en la receta (Task 3).
- StrongBox puede anunciarse y rechazar RSA-3072 → el test registra `NO_DISPONIBLE` y no falla; el mismo caso en TEE sigue siendo válido.
- Una clave recuperada de Keystore puede dejar de ser usable → cada test repite el descifrado tras `keyStore.getKey(alias, null)`.

---

### Task 1: Registro del acuerdo

**Files:**
- Modify: `.partner/DECISIONS.md`
- Modify: `.partner/STATE.md`
- Modify: `.partner/MISSIONS.md`
- Create: `docs/adr/0005-mgf1-sha1-por-keystore.md`

**Interfaces:**
- Consumes: D-001, D-019, D-020, D-021 y la matriz de `docs/keystore-spike-evidence.md`.
- Produces: D-022 (MGF1-SHA1 acordado), D-023 (acuerdo directo sin PDF nuevo) y ADR-0005, referenciados por las tareas 2 y 3.

- [ ] **Step 1: Añadir D-022 y D-023 al final de `.partner/DECISIONS.md`**

D-022: MGF1-SHA1 aceptado por Bruno y Persona 2 el 2026-11-23 para RSA-OAEP; digest del mensaje y AES-256-GCM sin cambios; Persona 2 modifica `:crypto`; ECC, perfiles, interfaces y envelope intactos. Motivo: incompatibilidad reproducible en el POCO, única opción que conserva las privadas en Keystore.

D-023: el acuerdo directo modifica los términos del contrato sin emitir un PDF nuevo; el PDF v0.2 permanece y `docs/contract.md` se actualizará cuando Bruno lo indique. Deja constancia explícita de la excepción a D-001 para este cambio.

- [ ] **Step 2: Crear `docs/adr/0005-mgf1-sha1-por-keystore.md`**

`Status: accepted`. Debe incluir: evidencia del spike; opciones consideradas y por qué se descartaron (MGF1-SHA256 solo en Samsung, negociar por dispositivo, privada fuera de Keystore); el coste de seguridad de MGF1-SHA1 explicado con precisión (MGF1 usa el hash para expandir la máscara, no para colisiones; el digest del mensaje sigue siendo SHA-256; añade una salvedad de cumplimiento); el reparto API 31–34 frente a 35+; y la consecuencia de que `docs/contract.md` quede pendiente.

- [ ] **Step 3: Actualizar handoff y misión**

En `.partner/STATE.md`: acuerdo recibido, contrato pendiente, spike completado. En `.partner/MISSIONS.md`: ticket 03 pasa a `VERIFIED` y se registra la fase post-aprobación; el ticket 08 queda a la espera de la entrega de `:crypto`.

- [ ] **Step 4: Verificar el registro**

Run: `grep -n "D-022\|D-023" .partner/DECISIONS.md && ls docs/adr/0005-mgf1-sha1-por-keystore.md && grep -n "MGF1-SHA256" docs/contract.md`
Expected: decisiones presentes, ADR presente, y `docs/contract.md` todavía con MGF1-SHA256 (cambio pospuesto, no olvidado).

### Task 2: Arnés instrumentado con la configuración acordada

**Files:**
- Create: `app/src/androidTest/java/com/securesms/app/keystore/OaepMgf1Sha1.kt`
- Create: `app/src/androidTest/java/com/securesms/app/keystore/RsaMgf1Sha1Test.kt`

**Interfaces:**
- Consumes: `KeyProperties.ENCRYPTION_PADDING_RSA_OAEP`, `KeyProperties.DIGEST_SHA256`, `KeyProperties.DIGEST_SHA1`, `KeyGenParameterSpec.Builder#setMgf1Digests`, `setIsStrongBoxBacked`, `PackageManager.FEATURE_STRONGBOX_KEYSTORE`, `KeyInfo.securityLevel`.
- Produces:
  - `internal const val TRANSFORMACION_RSA_ACORDADA: String`
  - `internal fun parametrosOaepAcordados(): OAEPParameterSpec` → `OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)`
  - `internal fun generadorRsa(alias: String, bits: Int, strongBox: Boolean): KeyPairGenerator`
  - `RsaMgf1Sha1Test` con `rsa2048Tee`, `rsa3072Tee`, `rsa2048StrongBoxSiAnunciado` y `rsa3072StrongBoxSiAnunciado`.

- [ ] **Step 1: Modo A — concepto y ejercicio (HUMANO)**

Explicar: el digest del mensaje y el digest de MGF1 son dos parámetros distintos de OAEP; cambiamos solo el segundo. Ejercicio antes de escribir código: localizar en el ADR-0005 la frase que justifica por qué SHA-1 es aceptable en la máscara y nombrar los dos campos del `OAEPParameterSpec` que fijan SHA-256 y SHA-1.

- [ ] **Step 2: Escribir las cuatro pruebas, aún sin helper**

Cada prueba usa `parametrosOaepAcordados()` y `generadorRsa(...)`, cifra con clave pública, descifra con la privada de Keystore, afirma que el texto recuperado coincide, repite tras `getKey(alias, null)` y registra nivel de seguridad y proveedor solo en caso de éxito. Las dos de StrongBox comprueban el feature y, si no está anunciado, registran `NOT_TESTED` y retornan.

- [ ] **Step 3: Verificar que falla (rojo)**

Run: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: FAIL con `Unresolved reference` para el helper.

- [ ] **Step 4: Implementar el helper**

`generadorRsa` construye `KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")` con `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `setEncryptionPaddings(ENCRYPTION_PADDING_RSA_OAEP)`, `setDigests(DIGEST_SHA256)`, `setMgf1Digests(DIGEST_SHA1)` solo si `Build.VERSION.SDK_INT >= 35`, y `setIsStrongBoxBacked(true)` solo si `strongBox`. El KDoc del helper explica el default de MGF1-SHA1 en API 31–34 y que nunca se exporta la privada.

- [ ] **Step 5: Verificar que compila y no hay regresión (verde)**

Run: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: BUILD SUCCESSFUL.

Run: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:testDebugUnitTest --no-daemon --offline --rerun-tasks`
Expected: 22 pruebas, 0 fallos, 0 errores.

- [ ] **Step 6: Entregar comandos HUMANO para los dos teléfonos**

Reinstalar el APK de prueba, limpiar logcat, ejecutar `-e class com.securesms.app.keystore.RsaMgf1Sha1Test` en `R5CX80AMTKN` y `4eeb389e`, y volcar `logcat -d -s KeystoreSpike:I`. La IA no ejecuta dispositivos ni inventa resultados.

### Task 3: Receta de producción y preparación de interoperabilidad

**Files:**
- Create: `docs/android-rsa-oaep-receta.md`
- Create: `docs/interop-vectores.md`
- Modify: `.partner/MISSIONS.md` (detalle del ticket 08)

**Interfaces:**
- Consumes: `parametrosOaepAcordados()` y `generadorRsa(...)` de Task 2, ADR-0005.
- Produces: documentación de la receta para los tickets 04, 06 y 08; esquema de vectores de interoperabilidad. Sin código de producción.

- [ ] **Step 1: Escribir la receta Android**

`docs/android-rsa-oaep-receta.md` debe mostrar los dos fragmentos exactos (parámetros del `Cipher` y generación de clave), el reparto por API, la comprobación de StrongBox, la regla de registrar proveedor solo tras `init` exitoso y la advertencia de que un envelope v0.2 no es descifrable con estos parámetros. Indicar que los tickets 04, 06 y 08 deben consumir este patrón en cuanto exista código de producción.

- [ ] **Step 2: Definir el esquema de vectores pendientes**

`docs/interop-vectores.md` debe enumerar los casos obligatorios —RSA-2048 y RSA-3072, cifrar en JVM y descifrar en Android, y a la inversa; plaintext vacío, ASCII y Unicode; clave recuperada de Keystore— y el formato del vector (perfil, tamaño, parámetros OAEP declarados, plaintext en hexadecimal, ciphertext en hexadecimal, clave pública de prueba). Dejar explícito que los valores los entregará Persona 2 y que no se inventan.

- [ ] **Step 3: Actualizar el ticket 08**

Añadir a la entrega del ticket 08 la dependencia de `docs/interop-vectores.md` y la condición de que la verificación JVM ↔ Android se ejecuta solo con vectores entregados por Persona 2.

- [ ] **Step 4: Verificar la documentación**

Run: `grep -n "MGF1ParameterSpec.SHA1\|SDK_INT >= 35" docs/android-rsa-oaep-receta.md && grep -n "RSA-3072\|Persona 2" docs/interop-vectores.md`
Expected: ambas coincidencias presentes.
