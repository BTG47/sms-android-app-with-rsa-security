# Ensamblado de fragmentos con expiración — diseño corto

## Intención

Los SMS llegan desordenados, duplicados o incompletos. El frente debe agrupar los Fragmentos por remitente y `messageId`, exponer el estado *Incompleto* sin intentar descifrado, expirar por inactividad y liberar el grupo. Motivo: ADR-0002 (solo en memoria) y ADR-0003 / D-014 (la ventana de cinco minutos se reinicia con cada fragmento).

## Objetivos

- Agrupar por `remitente + messageId`.
- Ventana deslizante de cinco minutos medida desde el último fragmento.
- Tope de 99 fragmentos por mensaje y techo de memoria por ensamblado.
- Reloj inyectable: las pruebas controlan el tiempo sin esperar.
- Entregar un Envelope solo cuando el conjunto es íntegro.

## No objetivos

- Persistencia en disco (ADR-0002).
- Descifrado, claves, SMS reales ni UI: eso vive en los tickets 03 a 08.
- Temporizadores, hilos ni coroutines.

## Anti-objetivos

- Descartar un grupo por antigüedad desde el primer fragmento.
- Sobrescribir silenciosamente un fragmento previo con `associateBy`.
- Exponer secretos o bytes del Envelope en logs.

## Restricciones

- Kotlin y JUnit 4 en `:app`, paquete `com.securesms.app.messaging`.
- Se reutiliza `Fragmento`, `parsear`, `encode` y `decode` del ticket 01, ya fusionados en `main`.
- Comentarios en español, sin emojis, KDoc en API pública (D-007).
- Reparto D-013: Bruno decide la representación exacta de estados y la clave de agrupación; la IA aporta el reloj falso y las pruebas.

## Enfoques considerados

1. **Mapa mutable con barrido explícito** (elegido). `MutableMap<ClaveEnsamblado, Ensamblado>` más un reloj `() -> Long`. Cada operación pública marca primero los grupos incompletos inactivos; cada fragmento nuevo actualiza `ultimoFragmentoEn`. La liberación de memoria ocurre por desalojo al superar `maxGrupos` o por `descartar`. Simple, sin hilos y directo de probar.
2. **Temporizador por grupo**. Reprograma una tarea en cada llegada. Expira sin intervención, pero introduce hilos y reloj real, con tests lentos y frágiles.
3. **`StateFlow` con `debounce`**. La UI se actualiza sola, pero añade Coroutines y un modelo mental innecesario para este ticket.

Se eligió el 1 porque encaja con D-004 y prioriza entender el estado antes que el framework.

## Diseño acordado

```kotlin
data class ClaveEnsamblado(val remitente: String, val messageId: String)

enum class EstadoEnsamblado { INCOMPLETO, COMPLETO, EXPIRADO }

class FragmentAssembler(
    private val reloj: () -> Long,
    private val plazoMs: Long = 5 * 60 * 1000,
    private val maxFragmentos: Int = 99,
    private val maxGrupos: Int = 16
) {
    fun agregar(remitente: String, texto: String): EstadoEnsamblado
    fun estado(remitente: String, messageId: String): EstadoEnsamblado?
    fun envelope(remitente: String, messageId: String): ByteArray?
    fun descartar(remitente: String, messageId: String)
}
```

- Un grupo es `INCOMPLETO` mientras falte alguna parte; `COMPLETO` cuando el total coincide y `decode` devuelve bytes; contradicciones o Base64 inválido se mantienen `INCOMPLETO` y no entregan Envelope.
- El `messageId` se valida como doce caracteres hexadecimales, mayúsculas o minúsculas, en `encabezado`. La ampliación del contrato fija el ancho y la entrega del ticket 01 fija el alfabeto.
- `barrerInactivos` marca como `EXPIRADO` los grupos incompletos cuya inactividad alcanzó `plazoMs` y los conserva en el mapa para que `estado` pueda exponer ese valor. Los grupos `COMPLETO` nunca se marcan. Un fragmento nuevo sobre un grupo expirado descarta su contenido y crea un ensamblado nuevo.
- Expiración: `ahora - ultimoFragmentoEn >= plazoMs`. El barrido corre al inicio de cada llamada pública, incluida `agregar`; un fragmento que llega después de expirado crea un grupo nuevo con ese único fragmento.
- Límite de 99: se descartan índices fuera de `1..count`. Los duplicados idénticos se ignoran; un índice repetido con payload distinto se conserva para que `decode` detecte la contradicción y no se entregue Envelope. El total de textos distintos por grupo se limita a `maxFragmentos`; el texto que llega con el tope ya alcanzado se ignora y no reinicia el reloj.
- Techo de memoria: si se supera `maxGrupos`, se descarta el grupo con `ultimoFragmentoEn` más antiguo. Ese desalojo libera también los grupos expirados, que permanecen hasta entonces para que `estado` exponga `EXPIRADO`; `descartar` los elimina de inmediato.
- `envelope` devuelve nulo en cualquier estado que no sea `COMPLETO`.

## Estado de la implementación

- Resuelto: Bruno eligió `enum` y la clave `ClaveEnsamblado`, e implementó la ruta básica en `eee39a1`.
- Resuelto: la IA entregó el reloj falso, la ventana deslizante, los límites de 99 textos y `maxGrupos`, y las pruebas de contradicción, envíos mezclados y descarte.
