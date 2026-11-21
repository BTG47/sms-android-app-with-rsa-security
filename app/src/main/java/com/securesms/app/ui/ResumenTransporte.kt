/*
 * Formateo del resumen de diagnóstico del transporte SMS.
 * Responsabilidad: presentar perfil, tamaño del envelope y cantidad de SMS sin exponer secretos.
 */

package com.securesms.app.ui

/**
 * Proyección de pantalla con los datos mínimos del diagnóstico.
 * No reemplaza a los conceptos del dominio `Contacto` ni `Clave pública importada`.
 */
data class DatosDeEnvio(
    val numero: String,
    val perfil: String,
    val huella: String?
)

/**
 * Construye el resumen de diagnóstico de un envío.
 * Los datos ausentes se vuelven texto explícito; no se usa el operador `!!`.
 */
fun resumen(datos: DatosDeEnvio, bytesEnvelope: Int, cantidadSms: Int?): String {
    val sms: String = cantidadSms?.toString() ?: "pendiente"
    val huella = datos.huella ?: "sin verificar"
    return "${datos.numero} | ${datos.perfil} | $bytesEnvelope bytes | " +
        "SMS: $sms | huella: $huella"
}
