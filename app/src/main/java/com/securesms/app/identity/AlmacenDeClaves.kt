package com.securesms.app.identity

import com.securesms.app.integration.Perfil

/**
 * Custodia de las claves privadas del dispositivo.
 *
 * La clave privada nunca se expone: este contrato solo permite saber si existe, generarla si
 * falta y obtener la clave pública. La implementación real vive en Android Keystore; en pruebas
 * se sustituye por un fake para no depender del dispositivo.
 */
interface AlmacenDeClaves {

    /** Indica si el perfil ya tiene un par de claves generado. */
    fun tieneClave(perfil: Perfil): Boolean

    /** Genera el par del perfil si no existe. Es bloqueante: no llamar desde el hilo principal. */
    fun generarSiFalta(perfil: Perfil): Result<Unit>

    /** Devuelve la clave pública del perfil en Base64 (X.509/SPKI). */
    fun clavePublicaBase64(perfil: Perfil): Result<String>
}
