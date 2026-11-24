# CryptoGateway demo (ticket 04) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Ejecución nativa en esta sesión: Bruno delegó la implementación y prohibió subagentes para el trabajo Partner. No se usan subagentes.

**Goal:** Dejar el seam demo `CryptoGateway`, su fake determinista y un ViewModel mínimo cableado en `MainActivity`, con cobertura unitaria, sin introducir la biblioteca de Persona 2.

**Architecture:** `integration/` contiene el contrato nuestro (`Perfil`, `CryptoGateway`) y el fake; ningún tipo de `:crypto` cruza esa frontera. `ui/` contiene un ViewModel con `StateFlow<UiState>` que convierte bytes en resumen usando las funciones existentes `resumen()` y `encode()`. `MainActivity` construye el fake y lo inyecta por constructor; la Compose lee el estado y la etiqueta `esDemo`.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.02.01, `androidx.lifecycle:lifecycle-viewmodel:2.9.4`, JUnit 4.13.2, pruebas JVM locales.

**Spec:** `.partner/DECISIONS.md` (D-002, D-004, D-005, D-007, D-024, D-025) y `.partner/MISSIONS.md` (ticket 04).

## Global Constraints

- Rama `bruno/feature/crypto-gateway-demo`, creada desde `main` en `2cc06d9`. Sin commits, push, PR ni merge sin autorización explícita y separada.
- `minSdk = 31`, `targetSdk = 37`, `compileSdk = 37`, `jvmTarget = JVM_17`.
- Nombres en español en API pública; KDoc en español para toda declaración pública nueva.
- Sin tipos, importaciones ni llamadas a `com.securesms.crypto` ni a la entrega de Persona 2.
- `ResumenTransporte.kt` no se modifica. `DatosDeEnvio(numero, perfil, huella)`; `resumen(datos, bytesEnvelope, cantidadSms)`.
- Sin instrumentación, SMS, dispositivo, descargas ni instalaciones en este ticket.
- No tocar: `prueba.kt`, `prueba_codec.jar`, `prueba_assembler.kt`, `app/src/test/java/com/securesms/app/messaging/SmsFragmentCodec.main.kt`, `gradle/gradle-daemon-jvm.properties`.
- Comandos Gradle siempre `--no-daemon --offline`.
- Ningún texto plano, clave ni material sensible en `UiState`, resúmenes, logs ni mensajes de error de UI.

## Review Focus

- Texto vacío: el fake debe producir bytes no vacíos, porque `encode()` exige un envelope no vacío. Lo cubre Task 1.
- Envelope vacío devuelto por una gateway: el ViewModel no debe llamar a `encode()` ni fabricar métricas. Lo cubre Task 2.
- Envelope demasiado grande: `encode()` falla por encima de 99 fragmentos; el fake debe estar acotado por construcción. Lo cubre Task 1.
- Fallo de la gateway: no se debe filtrar el mensaje de la excepción al resumen. Lo cubre Task 2.
- Texto plano filtrado al estado: ni el resumen ni los bytes del fake pueden contenerlo. Lo cubren Task 1 y Task 2.

---

### Task 1: Seam e implante demo en `integration/`

**Files:**
- Create: `app/src/main/java/com/securesms/app/integration/CryptoGateway.kt`
- Create: `app/src/main/java/com/securesms/app/integration/FakeCryptoGateway.kt`
- Test: `app/src/test/java/com/securesms/app/integration/FakeCryptoGatewayTest.kt`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `enum class Perfil { RSA_2048, RSA_3072, ECC_P256, ECC_P384 }`
  - `interface CryptoGateway { val esDemo: Boolean; fun cifrar(texto: String, perfil: Perfil): Result<ByteArray> }`
  - `class FakeCryptoGateway : CryptoGateway`

- [ ] **Step 1: Write the failing test**

En `FakeCryptoGatewayTest.kt`, con JUnit 4, cinco pruebas:
- `esDemo es verdadero`: `assertTrue(FakeCryptoGateway().esDemo)`.
- `cifrar devuelve bytes no vacíos y deterministas`: dos llamadas con `("Hola SecureSMS", Perfil.RSA_2048)`; `assertTrue(bytes.isNotEmpty())` y `assertArrayEquals(primero, segundo)`.
- `el envelope no contiene el texto plano`: `"Hola SecureSMS".toByteArray(Charsets.UTF_8)` no aparece como subsecuencia contigua en los bytes devueltos.
- `texto vacío también produce bytes no vacíos`: `cifrar("", Perfil.ECC_P256)` da bytes no vacíos.
- `el tamaño está acotado`: para un texto de 10 000 caracteres, `bytes.size` es menor o igual que 36.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.integration.FakeCryptoGatewayTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: FakeCryptoGateway`.

- [ ] **Step 3: Implement the seam and the fake**

En `CryptoGateway.kt`, declara `Perfil` y `CryptoGateway` con la firma exacta de **Interfaces** y KDoc en español: el perfil elige la combinación de algoritmos; la gateway es la frontera demo; `cifrar` devuelve bytes opacos y nunca texto plano.

En `FakeCryptoGateway.kt`, implementa `esDemo = true` y `cifrar` con `runCatching`. Los bytes son `"DEMO".toByteArray(US_ASCII)` seguido de `MessageDigest.getInstance("SHA-256").digest("$perfil|$texto".toByteArray(UTF_8))`: 36 bytes fijos, deterministas y sin el texto en claro. No hay parámetros configurables.

- [ ] **Step 4: Run test to verify it passes**

Run: el comando del Step 2.
Expected: PASS, 5 pruebas.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` exclusivamente los tres archivos de este task; esperar autorización antes de `git commit`.

---

### Task 2: ViewModel y métricas derivadas

**Files:**
- Modify: `app/build.gradle.kts` (añadir `implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")` junto a las dependencias lifecycle existentes; la versión ya está en caché y `activity:1.8.2` ya la trae de forma transitiva — la línea solo la hace explícita).
- Create: `app/src/main/java/com/securesms/app/ui/ResumenViewModel.kt`
- Test: `app/src/test/java/com/securesms/app/ui/ResumenViewModelTest.kt`

**Interfaces:**
- Consumes: `CryptoGateway`, `Perfil`, `FakeCryptoGateway` (Task 1); `DatosDeEnvio`, `resumen(...)`; `encode(...)`.
- Produces:
  - `data class UiState(val resumen: String = "", val envelope: ByteArray? = null)`
  - `class ResumenViewModel(private val gateway: CryptoGateway) : ViewModel`
  - `val uiState: StateFlow<UiState>`
  - `val esDemo: Boolean` (delegado a `gateway.esDemo`)
  - `fun cifrar(texto: String, perfil: Perfil)`

- [ ] **Step 1: Write the failing test**

En `ResumenViewModelTest.kt`, con JUnit 4, seis pruebas. Usa `FakeCryptoGateway` más un stub local `GatewayQueFalla` (`esDemo = false`; `cifrar` devuelve `Result.failure(IllegalStateException("secreto-del-gateway"))`) y un `GatewaySinBytes` (devuelve `Result.success(ByteArray(0))`).
- `estado inicial sin envío`: `resumen == ""` y `envelope == null`.
- `expone esDemo desde la gateway`: `true` con el fake, `false` con el stub.
- `cifrar construye el resumen con perfil, bytes y sms`: con `("Hola", Perfil.RSA_2048)`, `uiState.value.resumen` es exactamente `"sin destinatario | RSA_2048 | 36 bytes | SMS: 1 | huella: sin verificar"`.
- `el estado no contiene el texto plano`: tras cifrar `"Hola"`, `resumen` no contiene `"Hola"` y `envelope` no contiene la subsecuencia UTF-8 de `"Hola"`.
- `un fallo no fabrica métricas ni filtra secretos`: con `GatewayQueFalla`, `resumen == "error al cifrar"`, `envelope == null` y `resumen` no contiene `"secreto-del-gateway"`.
- `bytes vacíos se tratan como fallo`: con `GatewaySinBytes`, mismo estado de error que la prueba anterior.

Lee el estado sin corrutinas: `viewModel.uiState.value`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.ui.ResumenViewModelTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: ResumenViewModel`.

- [ ] **Step 3: Implement `ResumenViewModel`**

Extiende `androidx.lifecycle.ViewModel`. Expone `MutableStateFlow<UiState>` como `StateFlow<UiState>` vía `asStateFlow()`. `cifrar` usa `Result.fold`: en éxito, si `bytes.isEmpty()` produce el estado de error; si no, construye `resumen(DatosDeEnvio(numero = "sin destinatario", perfil = perfil.name, huella = null), bytes.size, encode(bytes).size)` y guarda los bytes. En fallo, produce `UiState(resumen = "error al cifrar", envelope = null)` sin leer la excepción. `esDemo` es un `get()` delegado. KDoc en español para la clase, `UiState` y los miembros públicos.

- [ ] **Step 4: Run test to verify it passes**

Run: el comando del Step 2.
Expected: PASS, 6 pruebas.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los tres archivos de este task; esperar autorización antes de `git commit`.

---

### Task 3: Cableado en `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/securesms/app/MainActivity.kt:1-24`

**Interfaces:**
- Consumes: `FakeCryptoGateway`, `ResumenViewModel`, `Perfil`, `UiState`, `esDemo`.
- Produces: ninguna API nueva.

- [ ] **Step 1: Create the wiring with no new unit test**

Excepción TDD acotada y ya acordada: esta capa es solo cableado de Compose y no tiene prueba unitaria sin instrumentación. En `onCreate`, crea `ResumenViewModel(FakeCryptoGateway())` y pásalo a un composable `ResumenPantalla(viewModel, modifier)`. El composable observa `uiState.collectAsState()` y muestra: `Text(state.resumen)`, `Text("modo demo")` solo si `viewModel.esDemo`, y un `Button(onClick = { viewModel.cifrar("Hola", Perfil.RSA_2048) })` con texto `Cifrar demo`. Sin texto plano en el estado; sin permisos, SMS ni navegación.

- [ ] **Step 2: Verify compilation and the full unit suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline --rerun-tasks`
Expected: BUILD SUCCESSFUL; 22 pruebas previas + 11 nuevas = 33 pruebas, 0 fallos, 0 errores.

- [ ] **Step 3: Commit (solo con autorización)**

`git add app/src/main/java/com/securesms/app/MainActivity.kt`; esperar autorización antes de `git commit`.

## Self-Review

- Cobertura: D-025 completo (firma, fake, inyección, etiqueta, estado, resumen intacto, pruebas unitarias). D-024 queda documentado fuera de este ticket. Diferido a ticket 08: mapeo de `ErrorCode`, vectores e interoperabilidad.
- Corrección registrada: `bytesEnvelope` y `cantidadSms` son parámetros de `resumen()`, no campos de `DatosDeEnvio`.
- Tipos consistentes entre tasks: `Perfil`, `CryptoGateway`, `Result<ByteArray>`, `UiState`, `esDemo`.
- Sin secretos ni texto plano en estado, resumen ni errores de UI; la excepción se descarta sin leerse.
