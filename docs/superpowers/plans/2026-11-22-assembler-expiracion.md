# Ensamblado con expiración — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Ejecución acordada:** Partner en línea con `tdd`, sin subagentes. Bruno implementa `FragmentAssembler` y decide la representación de estados; la IA revisa cada diff y entrega el reloj falso y las pruebas de límites.

**Goal:** agrupar Fragmentos por remitente y `messageId`, exponer *Incompleto*, *Completo* y *Expirado*, y entregar un Envelope solo cuando el conjunto es íntegro.

**Architecture:** mapa mutable en memoria con barrido explícito al inicio de cada llamada pública. El reloj se inyecta como `() -> Long`; cada fragmento nuevo actualiza `ultimoFragmentoEn`, de modo que los cinco minutos miden inactividad. El codec del ticket 01 sigue siendo la autoridad en validación y reconstrucción.

**Tech Stack:** Kotlin, JUnit 4, Gradle `:app`, paquete `com.securesms.app.messaging`.

**Spec:** `docs/superpowers/specs/2026-11-22-assembler-expiracion-design.md`

## Global Constraints

- Sin hilos, temporizadores, coroutines ni persistencia en disco (ADR-0002, D-004).
- Plazo por defecto: `5 * 60 * 1000` ms, contado desde el último fragmento (D-014, ADR-0003).
- Tope de `99` fragmentos por mensaje y de `16` grupos por ensamblador, ambos configurables.
- Reutilizar `encode`, `decode`, `Fragmento` y `parsear` de `SmsFragmentCodec.kt` sin modificarlos.
- Comentarios y nombres en español; KDoc en la API pública; sin emojis (D-007).
- Comandos de verificación: `JAVA_HOME=/home/brunotarangogaray/android-studio/jbr ./gradlew :app:testDebugUnitTest --no-daemon --rerun-tasks`.
- No tocar `prueba.kt`, `prueba_codec.jar`, `SmsFragmentCodec.main.kt` ni `gradle/gradle-daemon-jvm.properties`.
- Push, PR y merge requieren autorización separada (D-012).

## Review Focus

- **Límite exacto del plazo:** en `300000` ms de inactividad el grupo expira; en `299999` sigue *Incompleto*. Se prueba en la Tarea 2 con `duplicado no reinicia el plazo y el limite expira`.
- **Duplicado idéntico:** no cuenta como fragmento nuevo y no reinicia el reloj. Misma prueba de la Tarea 2.
- **Grupo completo:** no expira por inactividad porque ADR-0002 habla de fragmentos incompletos. Se prueba en `grupo completo no expira por inactividad`, Tarea 2.
- **Contradicción:** mismo índice con payload distinto no debe entregar Envelope. Se prueba en `contradiccion no entrega envelope`, Tarea 3.
- **Mezcla de envíos:** dos `messageId` distintos nunca se combinan. Se prueba en `distintos messageId no forman un grupo`, Tarea 3.

---

### Tarea 1 — Estados y ruta básica (HUMANO: Bruno)

**Files:**
- Crear: `app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt`
- Crear: `app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt`

**Interfaces:**
- Consume: `encode(envelope: ByteArray): List<String>` y `decode(parts: List<String>): ByteArray?` de `SmsFragmentCodec.kt`.
- Produce:
  - `data class ClaveEnsamblado(val remitente: String, val messageId: String)`
  - `enum class EstadoEnsamblado { INCOMPLETO, COMPLETO, EXPIRADO }` (Bruno puede proponer `sealed class` si lo justifica)
  - `class FragmentAssembler(private val reloj: () -> Long, private val plazoMs: Long = 5 * 60 * 1000, private val maxFragmentos: Int = 99, private val maxGrupos: Int = 16)`
  - `fun agregar(remitente: String, texto: String): EstadoEnsamblado`
  - `fun estado(remitente: String, messageId: String): EstadoEnsamblado?`
  - `fun envelope(remitente: String, messageId: String): ByteArray?`
  - `fun descartar(remitente: String, messageId: String)`

- [x] **Paso 1: recibir el concepto mínimo y hacer el ejercicio corto.** La IA entrega `prueba_assembler.kt` en la raíz con un `MutableMap<String, MutableList<Int>>`, un `enum` de tres estados y un reloj falso; Bruno lo ejecuta con `kotlinc` y lo explica con sus palabras.
- [x] **Paso 2: escribir el test que falla** en `FragmentAssemblerTest.kt`:

```kotlin
class FragmentAssemblerTest {
    private val envelope = ByteArray(300) { (it * 37).toByte() }

    @Test
    fun `incompleto hasta el ultimo fragmento`() {
        val fragmentos = encode(envelope)
        val id = fragmentos.first().split(':')[1]
        val assembler = FragmentAssembler({ 0L })

        fragmentos.dropLast(1).forEach {
            assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.agregar("5551", it))
        }
        assertNull(assembler.envelope("5551", id))
        assertEquals(EstadoEnsamblado.COMPLETO, assembler.agregar("5551", fragmentos.last()))
        assertArrayEquals(envelope, assembler.envelope("5551", id))
    }
}
```

- [x] **Paso 3: ejecutar y verificar que falla** con el comando global. Esperado: `compileDebugUnitTestKotlin FAILED`, `Unresolved reference: FragmentAssembler`.
- [x] **Paso 4: implementar** los tipos y los cuatro métodos en `FragmentAssembler.kt`. Regla: agrupar por `ClaveEnsamblado`, conservar los textos recibidos, llamar a `decode` sobre ellos y devolver `COMPLETO` solo si hay bytes. Aún sin expiración ni límites: esos llegan en las tareas 2 y 3. `descartar` elimina la clave del mapa.
- [x] **Paso 5: ejecutar y verificar que pasa.** Esperado: `BUILD SUCCESSFUL`, `FragmentAssemblerTest tests="1" failures="0"`.
- [x] **Paso 6: commit** con `git add -- app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt` y mensaje `feat(assembler): estados y agrupacion por remitente y messageId`.

### Tarea 2 — Expiración deslizante (IA, reasignada por Bruno el 2026-11-22)

**Files:**
- Crear: `app/src/test/java/com/securesms/app/messaging/RelojFalso.kt`
- Modificar: `app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt`
- Modificar: `app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt`

**Interfaces:**
- Consume: `FragmentAssembler` de la Tarea 1 y `reloj: () -> Long`.
- Produce: `class RelojFalso(inicial: Long = 0L) { fun ahora(): Long; fun avanzar(ms: Long) }`.

- [x] **Paso 1: crear el reloj falso** en `RelojFalso.kt` con `ahora()` y `avanzar(ms: Long)`. Sin KDoc: es código de prueba.
- [x] **Paso 2: escribir los tests que fallan** en `FragmentAssemblerTest.kt`:

```kotlin
@Test
fun `cada fragmento reinicia los cinco minutos`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora)
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("5551", fragmentos[0])
    reloj.avanzar(4 * 60 * 1000L)
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
    assembler.agregar("5551", fragmentos[1])
    reloj.avanzar(4 * 60 * 1000L)
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
    reloj.avanzar(60 * 1000L + 1)
    assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
}

@Test
fun `duplicado no reinicia el plazo y el limite expira`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora)
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("5551", fragmentos[0])
    reloj.avanzar(4 * 60 * 1000L)
    assembler.agregar("5551", fragmentos[0])
    reloj.avanzar(60 * 1000L - 1)
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
    reloj.avanzar(1)
    assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
}

@Test
fun `grupo completo no expira por inactividad`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora)
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    fragmentos.forEach { assembler.agregar("5551", it) }
    reloj.avanzar(10 * 60 * 1000L)
    assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
    assertArrayEquals(envelope, assembler.envelope("5551", id))
}

@Test
fun `fragmento tardio crea un grupo nuevo`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora)
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("5551", fragmentos[0])
    reloj.avanzar(5 * 60 * 1000L)
    assertEquals(EstadoEnsamblado.EXPIRADO, assembler.estado("5551", id))
    assertNull(assembler.envelope("5551", id))

    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.agregar("5551", fragmentos[1]))
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", id))
}
```

- [x] **Paso 3: ejecutar y verificar que fallan** los tres tests nuevos.
- [x] **Paso 4: implementar el barrido.** `barrerInactivos(ahora)` marca `expirado = true` en los grupos incompletos con `ahora - ultimoFragmentoEn >= plazoMs`; nunca marca grupos `COMPLETO`. `estado`, `envelope` y `descartar` lo invocan al inicio; `agregar` lo invoca al final a través de `estado`. Un `agregar` sobre un grupo expirado reemplaza su contenido por un ensamblado nuevo. `estado` devuelve `EXPIRADO` para los grupos marcados y `envelope` devuelve `null`.
- [x] **Paso 5: ejecutar y verificar que pasan** los tres tests y los anteriores.
- [x] **Paso 6: revisión conjunta del diff** por Bruno y la IA, con atención a `>=` frente a `>` y a que un grupo `COMPLETO` nunca se barra.
- [x] **Paso 7: commit** con `git add -- app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt app/src/test/java/com/securesms/app/messaging/RelojFalso.kt` y mensaje `feat(assembler): expiracion deslizante por inactividad`.

### Tarea 3 — Límites e integridad (IA; Bruno revisa)

**Files:**
- Modificar: `app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt`
- Modificar: `app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt`

**Interfaces:**
- Consume: `FragmentAssembler`, `RelojFalso` y `Fragmento` / `encabezado` del codec.
- Produce: ninguna firma pública nueva; solo el comportamiento de los límites.

- [x] **Paso 1: escribir los tests que fallan:**

```kotlin
@Test
fun `contradiccion no entrega envelope`() {
    val assembler = FragmentAssembler({ 0L })
    val fragmentos = encode(envelope).toMutableList()
    val campos = fragmentos[0].split(':').toMutableList()
    campos[4] = "QUJD"
    fragmentos.add(campos.joinToString(":"))
    val id = fragmentos.first().split(':')[1]

    fragmentos.forEach { assembler.agregar("5551", it) }
    assertNull(assembler.envelope("5551", id))
}

@Test
fun `distintos messageId no forman un grupo`() {
    val assembler = FragmentAssembler({ 0L })
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]
    val ajeno = if (id == "000000000000") "111111111111" else "000000000000"
    val campos = fragmentos[1].split(':').toMutableList()
    campos[1] = ajeno

    fragmentos.forEach { assembler.agregar("5551", it) }
    assembler.agregar("5551", campos.joinToString(":"))
    assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
    assertArrayEquals(envelope, assembler.envelope("5551", id))
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("5551", ajeno))
    assertNull(assembler.envelope("5551", ajeno))
}

@Test
fun `techo de grupos descarta el mas inactivo`() {
    val reloj = RelojFalso()
    val assembler = FragmentAssembler(reloj::ahora, maxGrupos = 2)
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("A", fragmentos[0])
    reloj.avanzar(1000)
    assembler.agregar("B", fragmentos[0])
    reloj.avanzar(1000)
    assembler.agregar("C", fragmentos[0])

    assertNull(assembler.estado("A", id))
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("B", id))
    assertEquals(EstadoEnsamblado.INCOMPLETO, assembler.estado("C", id))
}

@Test
fun `tope de noventa y nueve textos por grupo`() {
    val completo = ByteArray(9560) { (it * 31).toByte() }
    val fragmentos = encode(completo)
    assertEquals(99, fragmentos.size)
    val id = fragmentos.first().split(':')[1]
    val assembler = FragmentAssembler({ 0L })

    fragmentos.forEach { assembler.agregar("5551", it) }
    assertEquals(EstadoEnsamblado.COMPLETO, assembler.estado("5551", id))
    assertArrayEquals(completo, assembler.envelope("5551", id))

    val campos = fragmentos[0].split(':').toMutableList()
    campos[4] = "QUJD"
    assertEquals(EstadoEnsamblado.COMPLETO, assembler.agregar("5551", campos.joinToString(":")))
    assertArrayEquals(completo, assembler.envelope("5551", id))
}

@Test
fun `descartar elimina el grupo`() {
    val assembler = FragmentAssembler({ 0L })
    val fragmentos = encode(envelope)
    val id = fragmentos.first().split(':')[1]

    assembler.agregar("5551", fragmentos[0])
    assembler.descartar("5551", id)
    assertNull(assembler.estado("5551", id))
}
```

- [x] **Paso 2: ejecutar y verificar que fallan** los tres tests.
- [x] **Paso 3: implementar los límites.** Conservar todo texto recibido para pasarlos a `decode`, salvo los duplicados idénticos; así una contradicción se convierte en `INCOMPLETO`. Descartar índices fuera de `1..count`. Al superar `maxGrupos`, eliminar el grupo con `ultimoFragmentoEn` más antiguo. Respetar `maxFragmentos` como tope de textos distintos por grupo.
- [x] **Paso 4: ejecutar la suite completa y verificar** `BUILD SUCCESSFUL` y cero fallos, incluidas las 9 pruebas del ticket 01.
- [x] **Paso 5: revisión de Bruno** del diff de la IA, con atención a que no se modifies `SmsFragmentCodec.kt`.
- [x] **Paso 6: commit** con `git add -- app/src/main/java/com/securesms/app/messaging/FragmentAssembler.kt app/src/test/java/com/securesms/app/messaging/FragmentAssemblerTest.kt` y mensaje `test(assembler): contradicciones, envios mezclados y techo de grupos`.

## Cierre del ticket

- [x] Revisión final con `partner-review`: diferencias frente al diseño, KDoc, nombres en español y cobertura de los cinco riesgos de Review Focus.
- [x] Actualizar `.partner/MISSIONS.md` y `.partner/STATE.md` solo con evidencia real: número de pruebas, SHA de los commits y rama. Sin push ni PR.
