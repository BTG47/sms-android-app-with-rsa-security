package com.securesms.app.identity

/**
 * Puerto de persistencia de contactos y claves públicas.
 *
 * [cargar] puede lanzar cuando el contenido no es legible; el repositorio lo interpreta como
 * arranque vacío y lo reporta. [guardar] devuelve un fallo en lugar de lanzar, para que el
 * repositorio conserve el estado en memoria y no lo presente como persistido.
 */
interface AlmacenContactos {

    /** Devuelve el contenido guardado; si no existe, un estado vacío. */
    fun cargar(): EstadoContactos

    /** Reemplaza el contenido guardado por [estado]. */
    fun guardar(estado: EstadoContactos): Result<Unit>
}
