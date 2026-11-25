package com.securesms.app.messaging

/** Separadores de formato que el usuario puede escribir y que no viajan en el número. */
private val SEPARADORES_DE_FORMATO = setOf(' ', '-', '(', ')', '.')

/**
 * Deja el número listo para `SmsManager`: conserva un `+` inicial y solo dígitos.
 *
 * Descarta espacios, guiones, paréntesis y puntos. Devuelve `null` si no queda un número
 * utilizable, para que el envío se detenga antes de tocar el transporte.
 */
fun normalizarNumeroSms(numero: String): String? {
    val limpio = numero.filterNot { it in SEPARADORES_DE_FORMATO }
    val conMas = limpio.startsWith('+')
    val digitos = if (conMas) limpio.drop(1) else limpio
    if (digitos.isEmpty() || !digitos.all { it in '0'..'9' }) return null
    return if (conMas) "+$digitos" else digitos
}
