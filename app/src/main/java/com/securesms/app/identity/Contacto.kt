package com.securesms.app.identity

/**
 * Persona con la que se desea intercambiar mensajes, identificada por su número telefónico.
 *
 * La clave pública no forma parte del contacto: se modela aparte como [ClavePublicaImportada].
 */
data class Contacto(
    val numero: String,
    val nombre: String,
) {
    init {
        require(numero.isNotBlank()) { "el numero no puede estar vacio" }
    }
}
