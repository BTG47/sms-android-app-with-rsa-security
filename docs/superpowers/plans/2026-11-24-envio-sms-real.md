# Envío y recepción SMS real con estados (ticket 07) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Ejecución nativa en esta sesión: Bruno delegó la implementación completa en Q6 y revisará/ejecutará en dispositivo. El trabajo Partner no usa subagentes.

**Goal:** Enviar y recibir Fragmentos SS1 por SMS real, reconstruir el Envelope y mostrar en una pestaña nueva los estados `Aceptado para envío`, `Incompleto`, `Recibido y reconstruido` y `Descifrado`, con errores comprensibles y sin presentar el modo demo como cifrado real.

**Architecture:** Un puerto `TransporteSms` envuelve `SmsManager`/`TelephonyManager` y un puerto `RegistroSms` envuelve el `BroadcastReceiver`; ambos tienen implementación Android y dobles de prueba, de modo que `MensajesViewModel` se prueba entero en JVM. El ViewModel orquesta `CryptoGateway` (fake reversible etiquetado demo), `SmsFragmentCodec` y `FragmentAssembler`; la pantalla solo pinta estado. El perfil de envío lo dicta la Clave pública verificada del Contacto.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.02.01, `androidx.lifecycle:lifecycle-viewmodel:2.9.4`, `androidx.activity:activity-compose:1.8.2`, JUnit 4.13.2, Android `SmsManager`/`TelephonyManager`, `java.util.Base64`.

**Spec:** `.partner/MISSIONS.md` (ticket 07), `.partner/DECISIONS.md` (D-029 a D-036), `docs/contract.md` §5 y §8, `GLOSSARY.md` (estados del transporte), `docs/adr/0002-fragmentos-incompletos-expiran.md`, `docs/adr/0003-la-inactividad-reinicia-la-expiracion.md`, `ANDROID_IMPLEMENTATION.md` §5.4 y §7.3.

## Aprobación pendiente al revisar

Este plan toca dos archivos ya verificados:

1. `integration/FakeCryptoGateway.kt` pasa de hash irreversible a **ofuscación reversible con prefijo `DEMO`**, para que el flujo completo llegue a «Descifrado» sin integrar `:crypto` (Q1, D-029). Sigue siendo demo y la UI lo dirá; el ticket 08 lo sustituye. Cambia una expectativa de `ResumenViewModelTest` (36 → 9 bytes para «Hola»).
2. `messaging/FragmentAssembler.kt` gana `progreso()` de forma aditiva y con pruebas (Q9, D-034). No cambia el comportamiento existente.

Si prefieres no tocar el fake existente, la alternativa es un segundo fake reversible solo para mensajes; duplicaría el implante y la etiqueta demo. Recomiendo el cambio descrito.

## Global Constraints

- Rama `bruno/feature/sms-real`, ya creada y activa en `6f12099`, igual que `main`. Sin push, PR ni merge sin autorización explícita y separada.
- `minSdk = 31`, `targetSdk = 37`, `compileSdk = 37`, `jvmTarget = JVM_17`.
- Sin dependencias nuevas de Gradle; todo debe resolver con `--offline`.
- Nombres en español en API pública; KDoc en español. Los strings de UI siguen el estilo existente, sin acentos.
- No tocar `:crypto`, `Persona_resultados_seguridad/`, `Envelope`, `Perfil` (salvo usar el enum existente), `SmsFragmentCodec.kt`, `ResumenTransporte.kt` ni los helpers de `androidTest/keystore/`.
- No tocar: `prueba.kt`, `prueba_codec.jar`, `prueba_assembler.kt`, `app/src/test/java/com/securesms/app/messaging/SmsFragmentCodec.main.kt`, `gradle/gradle-daemon-jvm.properties`.
- Sin secretos, claves privadas, texto de mensajes entrantes sin descifrar ni cuerpos de SMS en logs, avisos o mensajes de error. Los errores visibles son genéricos.
- El modo demo se muestra siempre con un banner; nunca se presenta como cifrado real.
- Comandos Gradle siempre `--no-daemon --offline`. Commits locales por slice, solo con autorización y rutas explícitas; nunca `git add -A`.
- El árbol sucio preexistente se conserva intacto.
- Entrega académica: 2026-11-25 23:59. El alcance está en D-036; el QR queda fuera salvo que sobre tiempo.

## Review Focus

- **SMS ordinario o con prefijo parecido:** solo un cuerpo que empiece exactamente por `SS1:` puede crear o modificar una entrada; el resto se ignora sin avisos. Task 6.
- **Duplicado después de completar:** no debe crear otra entrada ni volver a descifrar; el `messageId` consumido se recuerda hasta que el proceso muera. Task 6.
- **Envelope demo alterado o ajeno:** debe terminar en `ERROR` genérico, sin texto parcial y sin filtrar la excepción. Tasks 1 y 6.
- **Rotación o recreación de la actividad:** los mensajes no se pierden y el receptor no se registra dos veces; el ViewModel sobrevive y `RegistroSmsAndroid` es idempotente. Task 7 y comprobación manual en Task 8.
- **Permiso revocado entre la comprobación y `sendTextMessage`:** `SecurityException` debe convertirse en `ERROR`, nunca en crash. Tasks 4 y 5.

---

### Task 1: `CryptoGateway.descifrar` y fake reversible

**Files:**
- Modify: `app/src/main/java/com/securesms/app/integration/CryptoGateway.kt`
- Modify: `app/src/main/java/com/securesms/app/integration/FakeCryptoGateway.kt`
- Modify: `app/src/test/java/com/securesms/app/integration/FakeCryptoGatewayTest.kt`
- Modify: `app/src/test/java/com/securesms/app/ui/ResumenViewModelTest.kt`

**Interfaces:**
- Consumes: `Perfil`.
- Produces:
  - `data class MensajeDescifrado(val texto: String, val perfil: Perfil)`
  - `interface CryptoGateway { val esDemo: Boolean; fun cifrar(texto: String, perfil: Perfil): Result<ByteArray>; fun descifrar(envelope: ByteArray): Result<MensajeDescifrado> }`
  - `FakeCryptoGateway` con envelope `DEMO` + ordinal de perfil + XOR del texto con la clave fija `"SecureSMS-demo"`.

- [ ] **Step 1: Write the failing tests**

En `FakeCryptoGatewayTest.kt`, conserva los casos actuales salvo `el tamano esta acotado`, que se sustituye, y añade:

```kotlin
@Test fun `el envelope demo se identifica por su prefijo`() {
    val bytes = gateway.cifrar("Hola", Perfil.ECC_P256).getOrThrow()
    assertArrayEquals("DEMO".toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 4))
}

@Test fun `descifrar recupera el texto y el perfil`() {
    for (perfil in Perfil.entries) {
        val bytes = gateway.cifrar("Hola ñ 你好 😀", perfil).getOrThrow()
        val claro = gateway.descifrar(bytes).getOrThrow()
        assertEquals("Hola ñ 你好 😀", claro.texto)
        assertEquals(perfil, claro.perfil)
    }
}

@Test fun `texto vacio ida y vuelta`() {
    val claro = gateway.descifrar(gateway.cifrar("", Perfil.RSA_3072).getOrThrow()).getOrThrow()
    assertEquals("", claro.texto)
}

@Test fun `descifrar rechaza bytes que no son demo`() {
    assertTrue(gateway.descifrar("SSMS".toByteArray()).isFailure)
    assertTrue(gateway.descifrar(ByteArray(0)).isFailure)
}

@Test fun `un texto largo se recupera intacto`() {
    val texto = "x".repeat(10_000)
    val bytes = gateway.cifrar(texto, Perfil.ECC_P384).getOrThrow()
    assertEquals(texto, gateway.descifrar(bytes).getOrThrow().texto)
}
```

En `ResumenViewModelTest.kt`, los dos fakes de `CryptoGateway` añaden
`override fun descifrar(envelope: ByteArray): Result<MensajeDescifrado> = Result.failure(IllegalStateException("no usado"))`
y la expectativa de `cifrar construye el resumen...` pasa a `9 bytes`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.integration.FakeCryptoGatewayTest' --tests 'com.securesms.app.ui.ResumenViewModelTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: descifrar` y `MensajeDescifrado`.

- [ ] **Step 3: Implement the new interface and reversible fake**

`FakeCryptoGateway` usa `MAGIC = "DEMO".toByteArray(US_ASCII)`, un byte con
`Perfil.entries.indexOf(perfil)` y `xor` byte a byte con `CLAVE = "SecureSMS-demo".toByteArray(US_ASCII)`.
`descifrar` valida longitud mínima 5, magic, ordinal en `Perfil.entries.indices` y UTF-8 con
`Charsets.UTF_8.newDecoder()` en modo `REPORT`; cualquier fallo devuelve `Result.failure`.
El KDoc dice explícitamente que es una ofuscación reversible de demostración y no cifrado.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2.
Expected: PASS.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los cuatro archivos y esperar autorización antes de
`git commit -m "feat(crypto-gateway): descifrado demo reversible para el transporte"`.

### Task 2: Progreso observable del ensamblado

**Files:**
- Modify: `app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt`
- Modify: `app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt`

**Interfaces:**
- Consumes: `ClaveEnsamblado`, `parsear`, `RelojFalso`.
- Produces: `data class ProgresoEnsamblado(val recibidos: Int, val total: Int)` y
  `fun FragmentAssembler.progreso(remitente: String, messageId: String): ProgresoEnsamblado?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `progreso informa partes distintas y total`() {
    val fragmentos = encode(ByteArray(300)) // 400 caracteres Base64 -> 4 fragmentos
    val id = fragmentos.first().split(':')[1]
    val assembler = FragmentAssembler({ 0L })

    assembler.agregar("5551", fragmentos[0])
    assembler.agregar("5551", fragmentos[0])
    assembler.agregar("5551", fragmentos[1])

    assertEquals(ProgresoEnsamblado(2, fragmentos.size), assembler.progreso("5551", id))
}

@Test fun `sin grupo no hay progreso`() {
    assertNull(FragmentAssembler({ 0L }).progreso("5551", "A1B2C3D4E5F6"))
}

@Test fun `el progreso de un expirado se conserva`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora)
    val fragmentos = encode(ByteArray(300))
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("5551", fragmentos[0])
    reloj.avanzar(5 * 60 * 1000L + 1)

    assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
    assertEquals(ProgresoEnsamblado(1, fragmentos.size), assembler.progreso("5551", id))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.messaging.FragmentAssemblerTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: progreso` / `ProgresoEnsamblado`.

- [ ] **Step 3: Implement `progreso`**

Añade el `data class` al mismo archivo. `progreso` llama antes a `barrerInactivos(reloj())`, busca el grupo
por `ClaveEnsamblado`, parsea sus textos y devuelve `ProgresoEnsamblado(indices.size, total)` usando el
`count` del primer fragmento válido. Devuelve `null` si no existe el grupo o no hay ningún fragmento válido.
No modifica ni elimina el grupo; `estado` sigue siendo la fuente de verdad para `EXPIRADO`.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2.
Expected: PASS, incluidos los casos existentes del assembler.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los dos archivos y esperar autorización antes de
`git commit -m "feat(assembler): exponer progreso de partes recibidas"`.

---

### Task 2b: Encaje GSM-7 de cada Fragmento

**Files:**
- Modify: `app/src/test/java/com/securesms/app/messaging/SmsFragmentCodecTest.kt`

**Interfaces:**
- Consumes: `encode`, `Fragmento`, `encabezado`.
- Produces: una prueba de caracterización; no cambia producción.

- [ ] **Step 1: Write the test**

```kotlin
private val GSM7_BASICO: Set<Char> = (
    "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
        "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    ).toSet()

@Test fun `cada fragmento cabe en un sms gsm7`() {
    val fragmentos = encode(ByteArray(1024) { it.toByte() })

    fragmentos.forEach { texto ->
        assertTrue(texto.length <= 160)
        assertTrue(texto.all { it in GSM7_BASICO })
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.messaging.SmsFragmentCodecTest' --no-daemon --offline`
Expected: PASS. Es una prueba de caracterización del contrato: 23 caracteres de cabecera + 130 de payload = 153.
Si falla, el codec viola el contrato y se detiene la implementación para revisarlo.

- [ ] **Step 3: Commit (solo con autorización)**

Incluir el archivo en el commit de Task 2 o en uno propio:
`git commit -m "test(codec): cada fragmento cabe en un sms gsm7"`.

---

### Task 3: Normalización del número de destino

**Files:**
- Create: `app/src/main/java/com/securesms/app/messaging/NumeroSms.kt`
- Create: `app/src/test/java/com/securesms/app/messaging/NumeroSmsTest.kt`

**Interfaces:**
- Produces: `fun normalizarNumeroSms(numero: String): String?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `conserva un mas inicial y descarta formato`() {
    assertEquals("+525512345678", normalizarNumeroSms("+52 (55) 1234-5678"))
    assertEquals("5512345678", normalizarNumeroSms(" 55 1234 5678 "))
    assertEquals("551234", normalizarNumeroSms("55.12.34"))
}

@Test fun `rechaza lo que no es un numero utilizable`() {
    assertNull(normalizarNumeroSms(""))
    assertNull(normalizarNumeroSms("   "))
    assertNull(normalizarNumeroSms("abc"))
    assertNull(normalizarNumeroSms("+"))
    assertNull(normalizarNumeroSms("12+34"))
    assertNull(normalizarNumeroSms("12/34"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.messaging.NumeroSmsTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: normalizarNumeroSms`.

- [ ] **Step 3: Implement `normalizarNumeroSms`**

Descarta espacios, guiones, paréntesis y puntos. Si el primer carácter es `+`, lo conserva y exige que el
resto sean solo dígitos no vacíos; si no, exige que todo sean dígitos. No acepta `+` en otra posición. KDoc
en español.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2. Expected: PASS.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los dos archivos y esperar autorización antes de
`git commit -m "feat(messaging): normalizar numeros antes de enviar"`.

---

### Task 4: Puertos de transporte y recepción SMS

**Files:**
- Create: `app/src/main/java/com/securesms/app/messaging/TransporteSms.kt`
- Create: `app/src/main/java/com/securesms/app/messaging/TransporteSmsAndroid.kt`
- Create: `app/src/main/java/com/securesms/app/messaging/RegistroSms.kt`
- Create: `app/src/main/java/com/securesms/app/messaging/RegistroSmsAndroid.kt`
- Create: `app/src/test/java/com/securesms/app/messaging/TransporteSmsFalso.kt`
- Create: `app/src/test/java/com/securesms/app/messaging/RegistroSmsFalso.kt`

**Interfaces:**
- Produces:
  - `enum class EstadoSim { LISTA, AUSENTE, NO_DISPONIBLE }`
  - `interface TransporteSms { fun estadoSim(): EstadoSim; fun enviar(numero: String, textos: List<String>): Result<Unit> }`
  - `interface RegistroSms { fun registrar(alRecibir: (remitente: String, cuerpo: String) -> Unit); fun cancelar() }`
  - `class TransporteSmsAndroid(context: Context) : TransporteSms`
  - `class RegistroSmsAndroid(context: Context) : RegistroSms`
  - `class TransporteSmsFalso(var sim: EstadoSim = EstadoSim.LISTA, var falla: Boolean = false) : TransporteSms`
    con `val envios: MutableList<Envio>` y `data class Envio(val numero: String, val textos: List<String>)`.
  - `class RegistroSmsFalso : RegistroSms` con `fun simular(remitente: String, cuerpo: String)`.

- [ ] **Step 1: Write the failing test for the fakes**

En `TransporteSmsFalsoTest.kt` (nuevo, test unitario):

```kotlin
@Test fun `registra el envio y respeta el estado de la sim`() {
    val transporte = TransporteSmsFalso()
    assertEquals(EstadoSim.LISTA, transporte.estadoSim())
    assertTrue(transporte.enviar("5512345678", listOf("SS1:A1B2C3D4E5F6:01:01:AAAA")).isSuccess)
    assertEquals("5512345678", transporte.envios.single().numero)
    assertEquals(1, transporte.envios.single().textos.size)
}

@Test fun `puede simular ausencia de sim y fallo`() {
    val transporte = TransporteSmsFalso(sim = EstadoSim.AUSENTE, falla = true)
    assertEquals(EstadoSim.AUSENTE, transporte.estadoSim())
    assertTrue(transporte.enviar("5512345678", listOf("x")).isFailure)
    assertTrue(transporte.envios.isEmpty())
}

@Test fun `el registro falso entrega al callback`() {
    val registro = RegistroSmsFalso()
    var recibido: Pair<String, String>? = null
    registro.registrar { remitente, cuerpo -> recibido = remitente to cuerpo }
    registro.simular("5551", "SS1:A1B2C3D4E5F6:01:01:AAAA")
    assertEquals("5551" to "SS1:A1B2C3D4E5F6:01:01:AAAA", recibido)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.messaging.TransporteSmsFalsoTest' --no-daemon --offline`
Expected: FAIL en compilación por las clases ausentes.

- [ ] **Step 3: Implement the ports, the fakes and the Android adapters**

`TransporteSmsAndroid.estadoSim()` usa `context.getSystemService(TelephonyManager::class.java)?.simState` y
mapea `SIM_STATE_READY` a `LISTA`, `SIM_STATE_ABSENT` a `AUSENTE` y el resto a `NO_DISPONIBLE`.
`enviar` obtiene `context.getSystemService(SmsManager::class.java)`, llama `sendTextMessage(numero, null, texto, null, null)`
por cada texto y envuelve todo en `runCatching`; así un `SecurityException` por permiso revocado termina como `Result.failure`.
No pide `READ_PHONE_STATE` ni elige suscripción: usa la suscripción SMS por defecto.

`RegistroSmsAndroid` registra un `BroadcastReceiver` para `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` con el
contexto de aplicación. En API 33+ usa `Context.RECEIVER_NOT_EXPORTED`; en 31-32 usa la sobrecarga sin flag.
`registrar` es idempotente: si ya hay receptor, no registra otro. En `onReceive` recorre
`Telephony.Sms.Intents.getMessagesFromIntent(intent)`, descarta remitente o cuerpo nulos e invoca el callback.
`cancelar` desregistra y deja el campo en `null`. Sin logs de cuerpos ni números.

- [ ] **Step 4: Run test to verify it passes**

Run: el comando del Step 2.
Expected: PASS. Además, `./gradlew :app:assembleDebug --no-daemon --offline` debe compilar los adaptadores Android.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los seis archivos y esperar autorización antes de
`git commit -m "feat(messaging): puertos y adaptadores de SMS"`.

### Task 5: `MensajesViewModel` — estado, contactos y envío

**Files:**
- Create: `app/src/main/java/com/securesms/app/ui/MensajesViewModel.kt`
- Create: `app/src/test/java/com/securesms/app/ui/MensajesViewModelTest.kt`
- Test (reutiliza): `AlmacenContactosEnMemoria.kt`, `FakeCryptoGateway.kt`,
  `TransporteSmsFalso.kt`, `RegistroSmsFalso.kt`, `RelojFalso.kt`.

**Interfaces:**
- Consumes: `RepositorioContactos`, `CryptoGateway`, `TransporteSms`, `RegistroSms`,
  `FragmentAssembler`, `encode`, `parsear`, `normalizarNumeroSms`.
- Produces:
  - `enum class DireccionMensaje { ENVIADO, RECIBIDO }`
  - `enum class EstadoMensaje { PREPARANDO, CIFRADO, ENVIANDO, ACEPTADO_PARA_ENVIO, INCOMPLETO, RECIBIDO_Y_RECONSTRUIDO, DESCIFRADO, EXPIRADO, ERROR }`
  - `data class ContactoEnvioUi(val numero: String, val nombre: String, val perfil: Perfil)`
  - `data class MensajeUi(val clave: String, val messageId: String?, val direccion: DireccionMensaje, val numero: String, val contacto: String?, val texto: String?, val perfil: Perfil?, val estado: EstadoMensaje, val bytesEnvelope: Int? = null, val caracteresBase64: Int? = null, val cantidadSms: Int? = null, val recibidos: Int? = null, val total: Int? = null, val detalle: String = "")`
  - `data class MensajesUiState(val mensajes: List<MensajeUi> = emptyList(), val contactos: List<ContactoEnvioUi> = emptyList(), val seleccionado: String? = null, val permisoEnviar: Boolean = false, val permisoRecibir: Boolean = false, val estadoSim: EstadoSim = EstadoSim.LISTA, val aviso: String = "", val esDemo: Boolean = true)` con `val contactoSeleccionado: ContactoEnvioUi?`
  - `class MensajesViewModel(repositorio: RepositorioContactos, gateway: CryptoGateway, transporte: TransporteSms, registro: RegistroSms, ensamblador: FragmentAssembler = FragmentAssembler(System::currentTimeMillis)) : ViewModel()`
  - `fun seleccionar(numero: String)`, `fun actualizarPermisos(enviar: Boolean, recibir: Boolean)`,
    `fun refrescar()`, `suspend fun enviar(texto: String): Boolean`, `fun recibir(remitente: String, cuerpo: String)`.

- [ ] **Step 1: Write the failing tests (solo envío)**

En `MensajesViewModelTest.kt`, un helper `crear()` arma repositorio en memoria con un contacto
`5512345678` / «Ana» y su clave `ECC_P256` importada y verificada, `FakeCryptoGateway`,
`TransporteSmsFalso`, `RegistroSmsFalso` y `FragmentAssembler(RelojFalso::ahora)`. Casos:

- `estado inicial solo ofrece contactos verificados`: con un contacto verificado y otro solo importado,
  `contactos` tiene uno, `esDemo` es `true` y `estadoSim` es `LISTA`.
- `seleccionar un contacto no verificado no lo selecciona`: `seleccionar(otro)` deja `seleccionado == null` y aviso.
- `enviar sin permiso devuelve false y no toca el transporte`.
- `enviar sin sim devuelve false y avisa`: `sim = AUSENTE`.
- `enviar sin contacto seleccionado devuelve false`.
- `enviar sin texto devuelve false`.
- `enviar un mensaje corto crea una entrada aceptada`: texto «Hola»; estado `ACEPTADO_PARA_ENVIO`,
  `texto == "Hola"`, `perfil == ECC_P256`, `bytesEnvelope == 9`, `caracteresBase64 == 12`,
  `cantidadSms == 1`, `envios.single().textos.size == 1`.
- `un mensaje largo usa varios sms`: `"x".repeat(200)` produce `cantidadSms == 3` y tres textos.
- `el numero se normaliza antes de enviar`: contacto `+52 (55) 1234-5678`; el transporte recibe `+525512345678`.
- `un numero invalido no envia`: contacto `abc`; `enviar` devuelve `false`, aviso y transporte vacío.
- `un fallo del transporte deja error y conserva el texto`: `falla = true`; estado `ERROR`, texto intacto,
  `detalle` no contiene la excepción.
- `refrescar descubre un contacto verificado despues de crearlo`: verificar en el repositorio compartido
  y llamar `refrescar()`; el contacto aparece y se puede seleccionar.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.ui.MensajesViewModelTest' --no-daemon --offline`
Expected: FAIL en compilación por `Unresolved reference: MensajesViewModel`.

- [ ] **Step 3: Implement the state and the send path**

`init` llama `registro.registrar(::recibir)` y `refrescar()`. `onCleared` llama `registro.cancelar()`.
`refrescar()` recalcula `contactos` desde `repositorio.todos()` filtrando `puedeEnviar`, toma el `perfil`
de `claveDe`, conserva el contacto seleccionado si sigue existiendo, actualiza `estadoSim` con
`transporte.estadoSim()` y no borra mensajes.

`enviar` valida en este orden: contacto seleccionado, texto no vacío, `permisoEnviar`, `estadoSim == LISTA`,
clave verificada del repositorio, número normalizable. Cifra con el perfil de la clave, llama `encode`,
extrae el `messageId` con `parsear(fragmentos.first())`, publica una `MensajeUi` en `CIFRADO`, la pasa a
`ENVIANDO`, ejecuta `withContext(Dispatchers.IO) { transporte.enviar(destino, fragmentos) }` y termina en
`ACEPTADO_PARA_ENVIO` o `ERROR` con detalle genérico. Devuelve `false` solo si falló una validación previa;
si se creó la entrada, devuelve `true` aunque el transporte falle, porque el texto queda visible en la entrada.
No usa `viewModelScope` (las pruebas JVM no tienen `Dispatchers.Main`): el `suspend` lo invoca la pantalla.
`recibir` queda como función vacía en esta tarea; Task 6 la implementa.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando del Step 2. Expected: PASS.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los archivos nuevos y esperar autorización antes de
`git commit -m "feat(mensajes): estado y envio de mensajes"`.

---

### Task 6: `MensajesViewModel` — recepción, progreso y expiración

**Files:**
- Modify: `app/src/main/java/com/securesms/app/ui/MensajesViewModel.kt`
- Modify: `app/src/test/java/com/securesms/app/ui/MensajesViewModelTest.kt`

**Interfaces:**
- Consumes: `FragmentAssembler.agregar/estado/envelope/descartar/progreso`, `parsear`, `ClaveEnsamblado`.
- Produces: la implementación de `recibir` y la extensión de `refrescar` para grupos pendientes.

- [ ] **Step 1: Write the failing tests (recepción)**

- `fragmentos desordenados se reconstruyen y descifran`: texto de 200 caracteres → 3 fragmentos;
  simular en orden inverso. Tras el primero: una entrada `INCOMPLETO` con `recibidos = 1`, `total = 3`,
  `texto == null`. Tras el último: `DESCIFRADO`, texto original, `perfil == ECC_P256`, `cantidadSms == 3`.
- `el progreso avanza con cada fragmento`: 1/3, 2/3 y luego `DESCIFRADO`.
- `un sms ordinario se ignora`: `recibir("5551", "Hola, que tal")` deja `mensajes` vacío y sin aviso.
- `un fragmento invalido se ignora`: `"SS1:no"` no crea entrada.
- `un duplicado antes de completar no aumenta el progreso`: mismo fragmento dos veces → `recibidos == 1`.
- `un duplicado despues de completar no duplica`: completar y repetir el primer fragmento → una sola entrada.
- `un incompleto expira y no descifra`: avanzar el reloj 5 min + 1, `refrescar()` → `EXPIRADO`, `texto == null`.
- `un envelope alterado termina en error generico`: fragmentos de `"NO-DEMO".toByteArray()`; al completar,
  `ERROR`, `texto == null`, `detalle == "no se pudo descifrar el mensaje"` y sin rastro de `NO-DEMO`.
- `recibir muestra el nombre del contacto si el numero coincide`: contacto `5551234` / «Ana»; `contacto == "Ana"`.
- `solo se descifra cuando esta completo`: antes del último fragmento, `perfil == null` y `texto == null`.

- [ ] **Step 2: Run tests to verify they fail**

Run: el comando de Task 5.
Expected: FAIL en las nuevas pruebas porque `recibir` no hace nada y `refrescar` no expira.

- [ ] **Step 3: Implement the receive path**

`recibir` ignora cuerpos que no empiezan exactamente por `SS1:` y fragmentos no parseables. Construye la
clave interna `"$remitente|$messageId"`; si ya está en `consumidos`, retorna. Llama `agregar` y `progreso`; si
`progreso` devuelve `null` (fragmento descartado, índice fuera de rango o grupo liberado), retorna sin crear
entrada. Con `INCOMPLETO`, crea o actualiza una entrada `INCOMPLETO` con `recibidos`, `total`, `cantidadSms = total`,
`texto = null` y la registra en `pendientes`. Con `COMPLETO`, obtiene `envelope`, marca la clave como consumida,
publica `RECIBIDO_Y_RECONSTRUIDO` con bytes y longitud Base64, llama `gateway.descifrar`, pasa a `DESCIFRADO`
con texto y perfil o a `ERROR` genérico, y llama `descartar`. `refrescar` recorre `pendientes`: `EXPIRADO`
actualiza la entrada y la saca de pendientes; cualquier otro estado actualiza el progreso.

- [ ] **Step 4: Run tests to verify they pass**

Run: el comando de Task 5. Expected: PASS, incluidos los casos de envío.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los dos archivos y esperar autorización antes de
`git commit -m "feat(mensajes): recepcion, progreso y expiracion"`.

### Task 7: Pantalla Mensajes, botón «Iniciar conversacion» y cableado

**Files:**
- Create: `app/src/main/java/com/securesms/app/ui/PantallaMensajes.kt`
- Create: `app/src/test/java/com/securesms/app/ui/PantallaMensajesTest.kt`
- Modify: `app/src/main/java/com/securesms/app/ui/PantallaContactos.kt`
- Modify: `app/src/main/java/com/securesms/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MensajesViewModel`, `MensajesUiState`, `MensajeUi`, `EstadoMensaje`, `EstadoSim`,
  `TransporteSmsAndroid`, `RegistroSmsAndroid`, `RepositorioContactos`, `AlmacenDeClavesAndroid`,
  `ArchivoContactos`, `FakeCryptoGateway`, `FragmentAssembler`.
- Produces:
  - `fun etiquetaDe(estado: EstadoMensaje): String`
  - `fun detalleDe(mensaje: MensajeUi): String`
  - `@Composable fun PantallaMensajes(viewModel: MensajesViewModel)`
  - `PantallaContactos(viewModel: ContactosViewModel, onIniciarConversacion: (String) -> Unit)`.

- [ ] **Step 1: Write the failing tests (funciones puras)**

En `PantallaMensajesTest.kt`:

```kotlin
@Test fun `cada estado tiene su etiqueta del glosario`() {
    assertEquals("Preparando", etiquetaDe(EstadoMensaje.PREPARANDO))
    assertEquals("Cifrado", etiquetaDe(EstadoMensaje.CIFRADO))
    assertEquals("Enviando", etiquetaDe(EstadoMensaje.ENVIANDO))
    assertEquals("Aceptado para envio", etiquetaDe(EstadoMensaje.ACEPTADO_PARA_ENVIO))
    assertEquals("Incompleto", etiquetaDe(EstadoMensaje.INCOMPLETO))
    assertEquals("Recibido y reconstruido", etiquetaDe(EstadoMensaje.RECIBIDO_Y_RECONSTRUIDO))
    assertEquals("Descifrado", etiquetaDe(EstadoMensaje.DESCIFRADO))
    assertEquals("Expirado", etiquetaDe(EstadoMensaje.EXPIRADO))
    assertEquals("Error", etiquetaDe(EstadoMensaje.ERROR))
}

@Test fun `el detalle muestra perfil bytes base64 y sms`() {
    val mensaje = MensajeUi(
        clave = "k", messageId = "A1B2C3D4E5F6", direccion = DireccionMensaje.ENVIADO,
        numero = "5512345678", contacto = "Ana", texto = "Hola", perfil = Perfil.ECC_P256,
        estado = EstadoMensaje.ACEPTADO_PARA_ENVIO, bytesEnvelope = 9,
        caracteresBase64 = 12, cantidadSms = 1,
    )
    assertEquals("ECC_P256 | 9 B | 12 Base64 | 1 SMS", detalleDe(mensaje))
}

@Test fun `el detalle de un incompleto muestra el progreso`() {
    val mensaje = MensajeUi(
        clave = "k", messageId = "A1B2C3D4E5F6", direccion = DireccionMensaje.RECIBIDO,
        numero = "5551", contacto = null, texto = null, perfil = null,
        estado = EstadoMensaje.INCOMPLETO, recibidos = 2, total = 3, cantidadSms = 3,
    )
    assertEquals("2 de 3 fragmentos", detalleDe(mensaje))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.securesms.app.ui.PantallaMensajesTest' --no-daemon --offline`
Expected: FAIL en compilación por `etiquetaDe` / `detalleDe` ausentes.

- [ ] **Step 3: Implement the screen and the wiring**

`PantallaMensajes` compone: título «Mensajes»; banner permanente «modo demo: el cifrado no es real» si
`estado.esDemo`; tarjeta de permisos si faltan `SEND_SMS` o `RECEIVE_SMS` con botón para pedirlos y
«Abrir ajustes»; aviso si `estadoSim != LISTA`; selector de contactos verificados con `FilterChip`;
`OutlinedTextField` para el texto; `Button` «Enviar» centrado con `Modifier.align(Alignment.CenterHorizontally)`;
lista (más reciente primero) con dirección, nombre o número, texto solo si existe, etiqueta de estado y
`detalleDe`. Antes de componer, `LaunchedEffect(Unit)` sincroniza permisos con
`Context.checkSelfPermission` y lanza `RequestMultiplePermissions`; un segundo `LaunchedEffect` llama
`viewModel.refrescar()` y luego cada 10 s con `delay(10_000)`. El envío se lanza en `rememberCoroutineScope`;
si `enviar` devuelve `true`, se limpia el texto. Sin logs de cuerpos.

`MainActivity` crea una sola vez por proceso `RepositorioContactos(ArchivoContactos(...))` y
`AlmacenDeClavesAndroid(applicationContext)` y obtiene `ContactosViewModel` y `MensajesViewModel` con
`ViewModelProvider(this, factory)` para que sobrevivan a la rotación y compartan el mismo repositorio.
El factory de mensajes construye `TransporteSmsAndroid(applicationContext)`,
`RegistroSmsAndroid(applicationContext)` y `FragmentAssembler(System::currentTimeMillis)`.
La pestaña `MENSAJES` se añade al `enum` privado y a la fila de botones; `PantallaContactos` recibe
`onIniciarConversacion = { numero -> mensajes.seleccionar(numero); pantalla = Pantalla.MENSAJES }`.
En `DetalleDeClave`, el botón se renombra a «Iniciar conversacion», se centra con
`Modifier.align(Alignment.CenterHorizontally)` y llama a ese callback; sigue deshabilitado sin clave verificada.

En `AndroidManifest.xml` añade `android.permission.SEND_SMS` y `android.permission.RECEIVE_SMS` antes de
`<application>`. No añadir `READ_SMS` ni `READ_PHONE_STATE`.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline`
Expected: BUILD SUCCESSFUL y las pruebas puras en verde. La UI Compose se verifica por compilación y en Task 8;
es la excepción TDD ya aceptada en los tickets 04 y 05.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los cinco archivos y esperar autorización antes de
`git commit -m "feat(mensajes): pantalla y cableado del transporte SMS"`.

---

### Task 8: Verificación, emulador y evidencia

**Files:**
- Modify: `.partner/MISSIONS.md` (estado del ticket 07)
- Modify: `.partner/STATE.md` (handoff corto)
- Create si hay capturas: `docs/evidence/2026-11-24-sms-real.md`

- [ ] **Step 1: Run the full suite**

Run: `scripts/verificar.sh --rerun`
Expected: `BUILD SUCCESSFUL` y `pruebas=N fallos=0 errores=0 omitidas=0`. No fijar `N` de antemano; el
script lo imprime. Si falla, leer el bloque `FAILURE:` completo antes de tocar código.

- [ ] **Step 2: Emulator receive demo**

Arranca el AVD `Medium_Phone_API_37.0`, instala el APK debug (`./gradlew :app:installDebug --no-daemon --offline`
o `adb install -r app/build/outputs/apk/debug/app-debug.apk`), abre «Mensajes» y concede `SEND_SMS` y
`RECEIVE_SMS`. Con la pestaña abierta, inyecta un fragmento SS1 determinista del fake:

```bash
adb emu sms send 5551234 "SS1:A1B2C3D4E5F6:01:01:REVNTwIbCg8U"
```

Expected: aparece una entrada recibida que pasa a `Descifrado`, con texto «Hola», perfil `ECC_P256`,
9 B, 12 Base64 y 1 SMS. El payload es el Base64 de `DEMO` + `0x02` + XOR de «Hola» con `SecureSMS-demo`;
si cambia el formato del fake, se regenera el string y se actualiza esta línea. Esto es **emulador**, no
prueba física; se registra como tal.

- [ ] **Step 3: Physical test (HUMANO, Bruno)**

En cada teléfono con SIM: instalar, abrir «Contactos», generar la clave propia, copiar la clave pública y la
huella; en el otro teléfono agregar el contacto, importar la clave con el perfil `RSA_2048` (el único que la
app genera hoy; el material no se valida contra el perfil hasta el ticket 08 y el fake no lo usa), verificar
comparando la huella por otro canal, abrir «Mensajes» y enviar un texto corto. Expected: el emisor muestra
`Aceptado para envio`; el receptor muestra `Incompleto` si faltan fragmentos y luego `Recibido y reconstruido`
/ `Descifrado` con el texto. Con el fake demo el SMS no está cifrado realmente: registrar el resultado como
transporte demo, no como cifrado real. Si no se ejecuta, dejar `NOT TESTED` con la razón.

- [ ] **Step 4: Update the records**

En `.partner/MISSIONS.md`, ticket 07: estado `IN PROGRESS` o `VERIFIED` según la evidencia real, rama, plan,
decisiones D-029 a D-036, archivos, comandos y resultados. En `.partner/STATE.md`, un bloque corto «Ticket 07»
con fase, decisiones, evidencia y próxima acción. No inventar PASS. No hacer commit de estos cambios sin
autorización.

- [ ] **Step 5: Commit (solo con autorización)**

`git add` los archivos de evidencia y esperar autorización antes de
`git commit -m "docs(sms): evidencia y cierre del ticket 07"`.

---

## Self-review

- **Cobertura del spec:** permisos y SIM → Tasks 4 y 7; envío → Task 5; recepción, agrupación y no descifrar
  incompletos → Task 6; estados y errores → Tasks 5–7; diagnóstico (perfil, bytes, Base64, SMS) → Tasks 5–7;
  encaje GSM-7 → Task 2b; pruebas sin SMS → Tasks 1–6; emulador → Task 8; límites y evidencia → Tasks 7–8.
- **Tipos consistentes:** `MensajeDescifrado`, `MensajeUi`, `EstadoMensaje`, `EstadoSim`, `ProgresoEnsamblado`
  y `normalizarNumeroSms` se definen una vez y se consumen con los mismos nombres.
- **Dependencias:** no se añaden librerías; `Context`, `SmsManager` y `TelephonyManager` quedan detrás de
  puertos para que el ViewModel siga siendo JVM puro.
- **Riesgo asumido:** el fake reversible es una ofuscación, no cifrado; el banner y el KDoc lo dicen y el
  ticket 08 lo sustituye. Sin `sentIntent`, «aceptado» no implica entrega ni descifrado.
