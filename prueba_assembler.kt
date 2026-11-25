/*
 * Ejercicio previo del ticket 02: estados, claves de agrupación y reloj controlado.
 * Responsabilidad: practicar MutableMap, enum y una función que	devuelve la hora antes
 * de escribir el ensamblador real. Completa los dos TODO y ejecuta con kotlinc.
 */

/** Estados simplificados del ensamblado. */
enum class EstadoEjercicio { INCOMPLETO, COMPLETO, EXPIRADO }

/** Clave compuesta: el mismo mensaje de dos remitentes distintos no es el mismo grupo. */
data class ClaveEjercicio(val remitente: String, val messageId: String)

/** Reloj controlado: el tiempo solo avanza cuando el test lo decide. */
class RelojFalsoEjercicio(private var valor: Long = 0L) {
    fun ahora(): Long = valor
    fun avanzar(ms: Long) {
        valor += ms
    }
}

fun main() {
    val grupos = mutableMapOf<ClaveEjercicio, EstadoEjercicio>()
    val reloj = RelojFalsoEjercicio()
    val clave = ClaveEjercicio("5551", "000000000000")

    grupos[clave] = EstadoEjercicio.INCOMPLETO
    check(grupos[clave] == EstadoEjercicio.INCOMPLETO)

    // TODO 1: cuando llega la última parte, el grupo pasa a COMPLETO. Una línea.
    grupos[clave] = EstadoEjercicio.COMPLETO

    reloj.avanzar(10 * 60 * 1000L)
    check(grupos[clave] == EstadoEjercicio.COMPLETO)

    val tardio = ClaveEjercicio("5552", "111111111111")
    grupos[tardio] = EstadoEjercicio.INCOMPLETO
    reloj.avanzar(5 * 60 * 1000L)

    // TODO 2: expira solo el grupo que sigue incompleto. Una línea.
    grupos[tardio] = EstadoEjercicio.EXPIRADO

    check(grupos[tardio] == EstadoEjercicio.EXPIRADO)
    check(grupos[clave] == EstadoEjercicio.COMPLETO)
    println("Los cuatro checks del ejercicio pasaron.")
}
