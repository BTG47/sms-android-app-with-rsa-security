package com.securesms.app.identity

/**
 * Contenido completo que se persiste: contactos y sus claves públicas.
 *
 * Solo contiene material público, huellas y estados; nunca claves privadas ni texto plano.
 */
data class EstadoContactos(
    val contactos: List<Contacto> = emptyList(),
    val claves: List<ClavePublicaImportada> = emptyList(),
)
