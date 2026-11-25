# Persistencia mínima y claves privadas (ticket 06) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Ejecución nativa en esta sesión: Bruno delegó la implementación y prohibió subagentes para el trabajo Partner. No se usan subagentes.

**Goal:** Que contactos, claves públicas y su estado de verificación sobrevivan al reinicio, y que las claves privadas se generen de forma perezosa en Android Keystore sin salir del dispositivo.

**Architecture:** `RepositorioContactos` conserva su API y delega el almacenamiento en un puerto `AlmacenContactos`; el adaptador `ArchivoContactos` escribe un JSON versionado con reemplazo atómico en `noBackupFilesDir`. Las claves privadas viven detrás de `AlmacenDeClaves`: la interfaz es testeable en JVM con un fake, y `AlmacenDeClavesAndroid` genera un alias por perfil en Keystore. La UI solo muestra y copia la clave pública propia.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.02.01, `androidx.lifecycle:lifecycle-viewmodel:2.9.4`, Gson 2.11.0 (ya en caché), JUnit 4.13.2, `androidx.test` 1.5.2, Android Keystore.

**Spec:** `.partner/MISSIONS.md` (ticket 06), `.partner/DECISIONS.md` (D-017, D-018, D-022, D-026, D-027), `docs/keystore-spike-evidence.md`, `docs/adr/0001-verificacion-no-es-importacion.md`, `docs/adr/0004-compatibilidad-android-keystore.md`.

## Aprobación pendiente al revisar

D-026 dice «sin dependencias nuevas». Este plan propone **Gson 2.11.0** para no escribir a mano un parser JSON: el jar, el pom y las fuentes ya están en la caché de Gradle, y el adaptador queda detrás de `AlmacenContactos`, así que si lo rechazas solo cambia Task 2 (se sustituye por un formato de texto propio con escapado). Sin Gson, el ticket crece unas 120 líneas de parser y sus casos límite.

## Global Constraints

- Rama `bruno/feature/persistencia-claves`, creada desde `main` en `349b234`. Sin push, PR ni merge sin autorización explícita y separada.
- `minSdk = 31`, `targetSdk = 37`, `compileSdk = 37`, `jvmTarget = JVM_17`.
- Nombres en español en API pública; KDoc en español para toda declaración pública nueva.
- Sin tocar `:crypto`, `Envelope`, `Perfil`, `CryptoGateway` ni `ResumenTransporte.kt`.
- Sin secretos, claves privadas, material sensible ni texto plano en logs, `UiState`, archivos o mensajes de error.
- No tocar: `prueba.kt`, `prueba_codec.jar`, `prueba_assembler.kt`, `app/src/test/java/com/securesms/app/messaging/SmsFragmentCodec.main.kt`, `gradle/gradle-daemon-jvm.properties`.
- Comandos Gradle siempre `--no-daemon --offline`. Si Gson no resuelve offline, ejecutar una sola vez sin `--offline` y volver al modo offline.
- Commits locales por slice, solo con autorización; rutas explícitas, nunca `git add -A`.
- El árbol sucio preexistente se conserva intacto.
- `docs/keystore-spike-evidence.md` y los helpers de `androidTest/keystore/` no se modifican: son evidencia del ticket 03.

## Review Focus

- **Archivo corrupto o truncado:** la app debe arrancar vacía, avisar y conservar el original como `.corrupto`; nunca sobrescribirlo en silencio. Task 2.
- **Nombre con comillas, barras o saltos de línea:** el round-trip debe devolver exactamente el mismo texto. Task 2.
- **Guardado fallido (disco lleno o permiso):** el estado en memoria no debe presentarse como persistido. Task 1.
- **Clave privada exportable:** `PrivateKey.getEncoded()` debe ser `null` y la clave no debe aparecer en ningún `toString`, log ni archivo. Task 3.
- **Proceso muerto a mitad de escritura:** el archivo anterior debe seguir legible y completo. Task 2.
- **Verificación perdida tras reiniciar:** una clave `VERIFICADA` debe seguir `VERIFICADA` al recrear el repositorio. Task 1.

---

### Task 1: Puerto de almacenamiento y repositorio persistente

**Files:**
- Create: `app/src/main/java/com/securesms/app/identity/EstadoContactos.kt`
- Create: `app/src/main/java/com/securesms/app/identity/AlmacenContactos.kt`
- Modify: `app/src/main/java/com/securesms/app/identity/RepositorioContactos.kt`
- Modify: `app/src/main/java/com/securesms/app/ui/ContactosViewModel.kt` (quitar el valor por defecto del repositorio y exponer el aviso de carga)
- Test: `app/src/test/java/com/securesms/app/identity/AlmacenContactosEnMemoria.kt`
- Test: `app/src/test/java/com/securesms/app/identity/RepositorioContactosTest.kt`
- Test: `app/src/test/java/com/securesms/app/ui/ContactosViewModelTest.kt`

**Interfaces:**
- Consumes: `Contacto`, `ClavePublicaImportada`, `EstadoClave`, `Perfil`.
- Produces:
  - `data class EstadoContactos(val contactos: List<Contacto> = emptyList(), val claves: List<ClavePublicaImportada> = emptyList())`
  - `interface AlmacenContactos { fun cargar(): EstadoContactos; fun guardar(estado: EstadoContactos): Result<Unit> }`
  - `class RepositorioContactos(private val almacen: AlmacenContactos)`
  - `val RepositorioContactos.cargaFallida: Boolean`
  - `val RepositorioContactos.ultimoFalloDeGuardado: Boolean`
  - `class AlmacenContactosEnMemoria : AlmacenContactos` (solo test)

- [ ] **Step 1: Write the failing tests**

En `RepositorioContactosTest.kt`, conserva los diez casos actuales construyendo `RepositorioContactos(AlmacenContactosEnMemoria())` y añade:
- `un repositorio nuevo con el mismo almacen recupera contactos y estados`: agrega un contacto, importa y verifica; crea un segundo `RepositorioContactos` sobre el mismo almacén; `estadoDe(numero) == VERIFICADA`, `exportarClave(numero)` coincide y `todos()` conserva el orden.
- `la verificacion sobrevive aunque se reimporte el contacto`: agrega el mismo contacto con otro nombre en un repositorio nuevo; la clave y su estado siguen ahí.
- `si el almacen falla al cargar arranca vacio y lo reporta`: almacén que lanza en `cargar()`; `todos()` vacío y `cargaFallida == true`.
- `un fallo de guardado no pierde el estado en memoria`: almacén que devuelve `Result.failure` en `guardar()`; el contacto existe en `todos()` y `ultimoFalloDeGuardado == true`.

En `AlmacenContactosEnMemoria.kt`, un `EstadoContactos` mutable en memoria que devuelve `Result.success(Unit)` y permite inyectar fallos.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.identity.RepositorioContactosTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: AlmacenContactos`.

- [ ] **Step 3: Implement the port and adapt the repository**

`RepositorioContactos` recibe `almacen` y en `init` hace `runCatching { almacen.cargar() }`: en éxito carga `EstadoContactos` en sus mapas; en fallo deja los mapas vacíos y marca `cargaFallida`. Tras cada mutación (`agregar`, `importarClave` con éxito, `verificarClave` con éxito) llama a `guardarEstado()`, que arma `EstadoContactos(contactos.values.toList(), claves.values.toList())` y registra `ultimoFalloDeGuardado` sin revertir la memoria. El resto de la API no cambia.

`ContactosViewModel` pierde el valor por defecto: `ContactosViewModel(private val repositorio: RepositorioContactos)`. En `init`, si `repositorio.cargaFallida`, publica `aviso = "no se pudieron leer los contactos guardados"`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.identity.RepositorioContactosTest' --tests 'com.securesms.app.ui.ContactosViewModelTest' --no-daemon --offline`
Expected: PASS, 14 pruebas del repositorio y las 7 del ViewModel.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los seis archivos del task y esperar autorización antes de `git commit -m "feat(identity): repositorio de contactos sobre un puerto de almacenamiento"`.

---

### Task 2: Adaptador de archivo JSON versionado

**Files:**
- Create: `app/src/main/java/com/securesms/app/identity/ArchivoContactos.kt`
- Modify: `app/build.gradle.kts` (añadir `implementation("com.google.code.gson:gson:2.11.0")`)
- Test: `app/src/test/java/com/securesms/app/identity/ArchivoContactosTest.kt`

**Interfaces:**
- Consumes: `AlmacenContactos`, `EstadoContactos`, `Contacto`, `ClavePublicaImportada`, `EstadoClave`, `Perfil`.
- Produces: `class ArchivoContactos(private val archivo: File) : AlmacenContactos`, con `const val VERSION = 1` en su `companion object`.

- [ ] **Step 1: Write the failing tests**

En `ArchivoContactosTest.kt`, con `Files.createTempDirectory("archivo-contactos")` por prueba:
- `ida y vuelta conserva todo`: dos contactos, uno con clave `VERIFICADA` y otro `IMPORTADA_SIN_VERIFICAR`; `guardar` y `cargar` devuelven listas iguales, mismo orden y mismo `Perfil`.
- `un archivo inexistente devuelve estado vacio`: sin escribir nada, `cargar()` no lanza y devuelve `EstadoContactos()`.
- `el archivo declara la version 1`: tras `guardar`, `archivo.readText()` contiene `"version":1`.
- `escapa nombres con comillas, barras y saltos`: nombre `Ana "la jefa" \\ linea\nnueva`; el round-trip devuelve exactamente ese texto.
- `un archivo corrupto falla y conserva el original`: escribe `{"version":1,` truncado; `cargar()` lanza `IllegalStateException`, y existe `contactos.json.corrupto` con el contenido original.
- `una version desconocida falla`: escribe `{"version":99}`; `cargar()` lanza `IllegalStateException` con mensaje que incluye `99`.
- `guardar no deja archivo temporal`: tras `guardar`, no existe `contactos.json.tmp` y el contenido es JSON válido.
- `guardar reemplaza por completo`: guarda dos contactos y después uno; `cargar()` devuelve un solo contacto.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.identity.ArchivoContactosTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: ArchivoContactos`.

- [ ] **Step 3: Implement `ArchivoContactos`**

DTOs privados planos (`ArchivoDto`, `ContactoDto`, `ClaveDto`) con todos los campos anulables y `TypeToken`; nunca se deserializa directamente a los `data class` de dominio, para que `require(...)` siga validando al reconstruir. `ClaveDto` guarda `numero` y se reconstruye con el contacto ya cargado; si el contacto no existe, la clave se descarta. `cargar()`: si el archivo no existe devuelve `EstadoContactos()`; si el JSON falla o `version != 1`, renombra a `<nombre>.corrupto` (mejor esfuerzo) y lanza `IllegalStateException`. `guardar()`: escribe en `<nombre>.tmp` y mueve con `Files.move(tmp, destino, ATOMIC_MOVE, REPLACE_EXISTING)`; si el sistema de archivos rechaza la combinación, reintenta solo con `REPLACE_EXISTING`. Devuelve `Result.failure` si algo falla, sin borrar el archivo anterior.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2.
Expected: PASS, 8 pruebas.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los tres archivos y esperar autorización antes de `git commit -m "feat(identity): archivo JSON versionado con reemplazo atomico"`.

---

### Task 3: Almacén de claves privadas en Android Keystore

**Files:**
- Create: `app/src/main/java/com/securesms/app/identity/AlmacenDeClaves.kt`
- Create: `app/src/main/java/com/securesms/app/identity/AlmacenDeClavesAndroid.kt`
- Test: `app/src/androidTest/java/com/securesms/app/identity/AlmacenDeClavesAndroidTest.kt`

**Interfaces:**
- Consumes: `Perfil`.
- Produces:
  - `interface AlmacenDeClaves { fun tieneClave(perfil: Perfil): Boolean; fun generarSiFalta(perfil: Perfil): Result<Unit>; fun clavePublicaBase64(perfil: Perfil): Result<String> }`
  - `class AlmacenDeClavesAndroid(context: Context) : AlmacenDeClaves`
  - Alias: `securesms.<perfil en minúsculas>.v1`.

- [ ] **Step 1: Write the failing instrumented tests**

En `AlmacenDeClavesAndroidTest.kt`, con `InstrumentationRegistry.getInstrumentation().targetContext` y limpieza de alias en `@After`:
- `genera RSA-2048 y la privada no es exportable`: `generarSiFalta(RSA_2048)` es éxito, `tieneClave` es `true`, `clavePublicaBase64` decodifica a bytes no vacíos y `keyStore.getKey(alias, null).encoded` es `null`.
- `no regenera si ya existe`: captura la clave pública, llama otra vez a `generarSiFalta` y compara `assertArrayEquals`.
- `genera P-256 y P-384 con proposito de acuerdo`: ambas generan y `tieneClave` es `true` para cada curva.
- `los alias son estables y distintos por perfil`: los cuatro alias son diferentes y contienen el nombre del perfil en minúsculas.
- `strongbox solo si se anuncia`: si `packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)` es falso, la generación igual pasa; si es verdadero, registra proveedor y `KeyInfo.securityLevel` sin fallar la prueba.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: AlmacenDeClavesAndroid`.

- [ ] **Step 3: Implement the interface and the Android adapter**

`AlmacenDeClavesAndroid` abre `KeyStore.getInstance("AndroidKeyStore").apply { load(null) }`. RSA: `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `setKeySize(2048/3072)`, `ENCRYPTION_PADDING_RSA_OAEP`, `setDigests(DIGEST_SHA256)` y `setMgf1Digests(DIGEST_SHA1)` solo si `Build.VERSION.SDK_INT >= 35` (D-022). EC: `PURPOSE_AGREE_KEY` con `ECGenParameterSpec("secp256r1" | "secp384r1")`. StrongBox: intenta `setIsStrongBoxBacked(true)` solo si el dispositivo anuncia la característica y reintenta sin ella al capturar `StrongBoxUnavailableException` (D-018). `clavePublicaBase64` codifica `keyStore.getCertificate(alias).publicKey.encoded` en Base64 y devuelve `Result.failure` si no hay certificado. Documenta en KDoc que la generación es bloqueante y no debe llamarse desde el hilo principal.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: BUILD SUCCESSFUL. La ejecución en dispositivo es HUMANO y se hace en Task 5.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los tres archivos y esperar autorización antes de `git commit -m "feat(identity): claves privadas perezosas en Android Keystore"`.

---

### Task 4: Mi clave pública en la pantalla de contactos

**Files:**
- Modify: `app/src/main/java/com/securesms/app/ui/ContactosViewModel.kt`
- Modify: `app/src/main/java/com/securesms/app/ui/PantallaContactos.kt`
- Test: `app/src/test/java/com/securesms/app/identity/AlmacenDeClavesFalso.kt`
- Test: `app/src/test/java/com/securesms/app/ui/ContactosViewModelTest.kt`

**Interfaces:**
- Consumes: `AlmacenDeClaves` (Task 3), `Huella.calcular`.
- Produces:
  - `data class MiClaveUi(val perfil: Perfil, val base64: String, val huella: String)`
  - `ContactosUiState.miClave: MiClaveUi?`
  - `suspend fun ContactosViewModel.generarMiClave()`
  - `class AlmacenDeClavesFalso : AlmacenDeClaves` (solo test)

- [ ] **Step 1: Write the failing tests**

En `ContactosViewModelTest.kt`, construyendo el ViewModel con `AlmacenDeClavesFalso`:
- `sin clave no hay miClave`: `uiState.value.miClave == null`.
- `generar publica base64 y huella`: `runBlocking { viewModel.generarMiClave() }`; `miClave!!.base64` es el Base64 del fake y `huella` es `Huella.calcular(base64)`.
- `al iniciar carga la clave existente`: fake con RSA-2048 ya generada; el ViewModel la publica en `init`.
- `un fallo de generacion deja aviso y no publica clave`: fake que devuelve `Result.failure`; `miClave == null` y `aviso` menciona que no se pudo generar.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.ui.ContactosViewModelTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: AlmacenDeClavesFalso`.

- [ ] **Step 3: Implement the ViewModel and the UI**

El ViewModel recibe `private val claves: AlmacenDeClaves` y en `init` llama a `cargarMiClave()`: si `tieneClave(RSA_2048)`, `clavePublicaBase64` y `Huella.calcular`; si falla, deja `miClave = null` sin ruido. `generarMiClave()` es `suspend`: `withContext(Dispatchers.IO) { claves.generarSiFalta(RSA_2048) }` y después publica o avisa. En `PantallaContactos`, una tarjeta superior «Mi clave pública (RSA-2048)» con botón «Generar» cuando falta y, cuando existe, `BloqueCopiable` con la huella y el Base64 reutilizando el componente del ticket 05. El botón usa `rememberCoroutineScope()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2.
Expected: PASS, 11 pruebas del ViewModel.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los cuatro archivos y esperar autorización antes de `git commit -m "feat(ui): mostrar y copiar la clave publica propia"`.

---

### Task 5: Cableado, verificación y cierre

**Files:**
- Modify: `app/src/main/java/com/securesms/app/MainActivity.kt`
- Delete: `app/src/test/java/com/securesms/app/practica/ArchivoPracticaTest.kt`
- Modify: `.partner/MISSIONS.md`, `.partner/STATE.md` (registros locales, fuera de Git)

**Interfaces:**
- Consumes: `ArchivoContactos`, `AlmacenDeClavesAndroid`, `ContactosViewModel`.
- Produces: app cableada; sin API nueva.

- [ ] **Step 1: Wire the dependencies**

En `MainActivity.onCreate`, antes de `setContent`:
`val archivo = File(applicationContext.noBackupFilesDir, "contactos.json")`; `val contactos = ContactosViewModel(RepositorioContactos(ArchivoContactos(archivo)), AlmacenDeClavesAndroid(applicationContext))`. No se genera ninguna clave al arrancar (D-027).

- [ ] **Step 2: Delete the practice exercise and run the full suite**

Borra `ArchivoPracticaTest.kt` y ejecuta:
`./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline --rerun-tasks`
Expected: BUILD SUCCESSFUL; 59 pruebas previas + las nuevas, 0 fallos.

- [ ] **Step 3: Build the instrumented APK**

Run: `./gradlew :app:assembleDebugAndroidTest --no-daemon --offline`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: HUMANO — ejecutar en los dos teléfonos**

Bruno instala y ejecuta `AlmacenDeClavesAndroidTest` con los comandos `adb` de `docs/superpowers/plans/2026-11-23-mgf1-sha1-android.md` en `R5CX80AMTKN` (Samsung, API 37) y `4eeb389e` (POCO, API 36). Evidencia: `OK (5 tests)` por dispositivo y el `logcat` del nivel de seguridad. No se inventan resultados.

- [ ] **Step 5: HUMANO — comprobar persistencia en el teléfono**

Con el APK instalado: agregar un contacto, importar una clave, verificarla, cerrar la app por completo y volver a abrirla. La clave debe seguir `VERIFICADA` y la huella debe coincidir. Después, pulsar «Generar» en «Mi clave pública» y comprobar que la huella se copia.

- [ ] **Step 6: HUMANO — revisar que la clave privada no sale**

Leer `AlmacenDeClavesAndroid.kt` y `AlmacenDeClavesAndroidTest.kt` y confirmar: no hay `getKey(...).encoded`, no hay logs del material privado, la clave pública es lo único exportado y la prueba comprueba `encoded == null`. Es la carga HUMANO del ticket.

- [ ] **Step 7: Actualizar registros y proponer commits**

Actualizar `MISSIONS.md` (ticket 06 con evidencia y estado) y `STATE.md` (commits, evidencia, límites). Proponer los commits pendientes con rutas explícitas y esperar autorización. Push, PR y merge quedan para una confirmación separada.

---

## Límites conscientes

- `identity` sigue dependiendo de `integration.Perfil`; la revisión de capas queda para el ticket 08.
- `AlmacenDeClavesAndroid` duplica ~15 líneas de especificación que ya existen en `androidTest/keystore/OaepMgf1Sha1.kt`; los helpers del spike no se tocan porque son evidencia del ticket 03.
- No se cifra ni se envía nada: el consumo real de las claves es de los tickets 07 y 08.
- El JSON no se cifra; solo contiene material público, huellas y estados.
- La app no rota ni versiona el alias si cambian los parámetros: un cambio exige `.v2` y una decisión nueva.

## Desviaciones al ejecutar (2026-11-24)

- **Tasks 1 y 2 se implementaron juntas.** `MainActivity` construye el `ContactosViewModel` y no compila sin un `AlmacenContactos` real, así que el adaptador de archivo entró en la misma tanda. Cada prueba se vio fallar antes de su implementación.
- **Gson 2.11.0 en lugar de un parser escrito a mano** (D-028). Se excluye `com.google.errorprone:error_prone_annotations` porque su jar no está en caché para `--offline` y solo aporta anotaciones de compilación.
- **Dos casos de integridad añadidos en la revisión:** un error de lectura de E/S ya no aparta el archivo como corrupto, y al cargar se recalcula la huella desde el material para descartar entradas manipuladas.
- **Un caso de carrera añadido:** si dos toques en «Generar» provocan que el segundo encuentre el alias ya creado, el ViewModel publica la clave existente en lugar de mostrar un fallo.
- **Límite de cobertura:** la tarjeta «Mi clave pública» es declarativa y no tiene prueba de UI Compose; su lógica de estado sí está cubierta en `ContactosViewModelTest`. `PantallaContactosTest` sigue probando solo funciones puras.
