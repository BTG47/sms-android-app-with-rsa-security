/*
 * Ejercicio corregido del bloque 1: resumen de diagnóstico del transporte SMS.
 * Responsabilidad: formatear los datos visibles en la UI sin exponer secretos.
 */

package com.securesms.app.ui

/** Destinatario con su clave pública importada y el estado de verificación de la huella. */
data class Destinatario(
    val numero: String,
    val perfil: String,
    val huella: String?
)

/**
 * Construye el resumen de diagnóstico de un envío.
 * Los datos ausentes se vuelven texto explícito; no se usa el operador `!!`.
 */
fun resumen(destinatario: Destinatario, bytesEnvelope: Int, cantidadSms: Int?): String {
    val sms: String = cantidadSms?.toString() ?: "pendiente"
    val huella = destinatario.huella ?: "sin verificar"
    return "${destinatario.numero} | ${destinatario.perfil} | $bytesEnvelope bytes | " +
        "SMS: $sms | huella: $huella"
}

fun main() {
    val casoUno = Destinatario("+59170000000", "ECC_P256", "A1B2")
    check(
        resumen(casoUno, 640, 5) ==
            "+59170000000 | ECC_P256 | 640 bytes | SMS: 5 | huella: A1B2"
    )

    val casoDos = Destinatario("+59170000000", "RSA_2048", null)
    check(
        resumen(casoDos, 384, 3) ==
            "+59170000000 | RSA_2048 | 384 bytes | SMS: 3 | huella: sin verificar"
    )

    val casoTres = Destinatario("+59170000000", "RSA_3072", "C3D4")
    check(
        resumen(casoTres, 900, null) ==
            "+59170000000 | RSA_3072 | 900 bytes | SMS: pendiente | huella: C3D4"
    )

    println("Los tres casos de aceptación pasaron.")
}
