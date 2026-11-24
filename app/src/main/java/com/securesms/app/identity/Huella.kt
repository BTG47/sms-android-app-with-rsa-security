package com.securesms.app.identity

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

private val SEPARADORES = Regex("[\\s:\\-]")

/**
 * Huella de una clave pública: SHA-256 del material codificado, en hexadecimal minúscula.
 *
 * Una huella no demuestra autenticidad por sí sola; sirve para compararla por un canal
 * distinto de aquel por el que se importó la clave.
 */
object Huella {

    /**
     * Calcula la huella del material público codificado en Base64.
     *
     * Devuelve un fallo si el texto no es Base64 válido o si el material está vacío.
     */
    fun calcular(materialBase64: String): Result<String> = runCatching {
        val limpio = materialBase64.filterNot { it.isWhitespace() }
        val bytes = Base64.getDecoder().decode(limpio)
        require(bytes.isNotEmpty()) { "el material publico esta vacio" }

        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    /** Quita espacios y separadores y pasa a minúsculas para comparar huellas escritas a mano. */
    fun normalizar(huella: String): String =
        huella.replace(SEPARADORES, "").lowercase(Locale.ROOT)
}
