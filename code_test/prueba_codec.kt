/*
 * Ejercicio del ticket 01: modelo Fragmento y formato del encabezado SS1.
 * Material de aprendizaje; no se compila como parte del módulo :app.
 */

package com.securesms.app.ui

/**
 * Fragmento lógico de un Envelope, preparado para su transporte por SMS.
 * El prefijo SS1 y los anchos fijos se aplican al construir el encabezado.
 */
data class Fragmento(
    val messageId: String,
    val index: Int,
    val count: Int,
    val payload: String
)

/**
 * Construye la representación textual que viaja en un SMS.
 * El id usa doce caracteres y el índice y el total, dos, según el contrato v0.2.
 */
fun encabezado(fragmento: Fragmento): String {
    // Verificaciones de datos del fragmento.
    require(fragmento.messageId.length == 12) { "El id debe tener 12 caracteres" }
    require(fragmento.index in 1..99) { "El índice del fragmento debe estar entre 1 y 99" }
    require(fragmento.count in 1..99) { "El total de fragmentos debe estar entre 1 y 99" }
    val index = fragmento.index.toString().padStart(2, '0')
    val count = fragmento.count.toString().padStart(2, '0')
    return "SS1:${fragmento.messageId}:${index}:${count}:${fragmento.payload}"
}

/**
 * Convierte un texto SS1 en un [Fragmento], o devuelve null si no es canónico.
 * La validación se delega a [encabezado] para no duplicar las reglas del contrato.
 */
fun parsear(texto: String): Fragmento? = runCatching {
    val partes = texto.split(':', limit = 5)
    require(partes.size == 5 && partes[0] == "SS1")
    val fragmento = Fragmento(partes[1], partes[2].toInt(), partes[3].toInt(), partes[4])
    require(encabezado(fragmento) == texto)
    fragmento
}.getOrNull()

/** Casos de aceptación ejecutables del formato de encabezado y del parser. */
fun main() {
    println("Ejecutando casos de encabezado")
    check(encabezado(Fragmento("A1B2C3D4E5F6", 1, 3, "QUJD")) ==
        "SS1:A1B2C3D4E5F6:01:03:QUJD")
    check(encabezado(Fragmento("A1B2C3D4E5F6", 12, 99, "WA==")) ==
        "SS1:A1B2C3D4E5F6:12:99:WA==")
    check(runCatching { encabezado(Fragmento("A1B2C3D4E5F6", 0, 3, "QUJD")) }
        .exceptionOrNull() is IllegalArgumentException)
    check(runCatching { encabezado(Fragmento("A1B2C3D4E5F6", 100, 3, "QUJD")) }
        .exceptionOrNull() is IllegalArgumentException)
    println("Los cuatro casos de aceptación pasaron.")
    println("Ejecutando casos de parser")
    val texto = "SS1:A1B2C3D4E5F6:01:03:QUJD"
    check(parsear(texto) == Fragmento("A1B2C3D4E5F6", 1, 3, "QUJD"))
    check(parsear("SMS:A1B2C3D4E5F6:01:03:QUJD") == null)
    check(parsear("SS1:A1B2C3D4E5F6:1:03:QUJD") == null)
    check(parsear("SS1:ABC:01:03:QUJD") == null)
    println("Los cuatro casos del parser pasaron.")
}
