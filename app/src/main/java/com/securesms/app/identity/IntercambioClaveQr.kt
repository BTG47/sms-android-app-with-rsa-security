// Transfiere material público en formato SSPK1 y conserva los bytes de la huella de Contactos.
package com.securesms.app.identity

import com.securesms.app.integration.Perfil
import com.securesms.crypto.PublicKeys
import java.util.Base64

/** Datos públicos que la pantalla puede previsualizar antes de importarlos. */
internal data class ClaveQr(
    val perfil: Perfil,
    val materialBase64: String,
    val huella: String,
)

/** Construye el formato SSPK1 con los mismos bytes que muestra la tarjeta de clave propia. */
internal fun codificarClaveQr(perfil: Perfil, materialBase64: String): String {
    val der = Base64.getDecoder().decode(materialBase64)
    val material = Base64.getUrlEncoder().withoutPadding().encodeToString(der)
    return "SSPK1:${perfil.name}:$material"
}

/** Valida que el QR contenga una clave pública del perfil indicado y calcula su huella local. */
internal fun leerClaveQr(codigo: String): ClaveQr? {
    if (codigo.length > 1024) return null
    return runCatching {
        val texto = codigo.trim()
        val importada = PublicKeys.importCompact(texto)
        val der = Base64.getUrlDecoder().decode(texto.substringAfterLast(':'))
        val base64 = Base64.getEncoder().encodeToString(der)
        ClaveQr(Perfil.valueOf(importada.profile.name), base64, Huella.calcular(base64).getOrThrow())
    }.getOrNull()
}
