package com.securesms.app.identity

/**
 * Almacén en memoria para pruebas: simula el puerto sin tocar disco.
 *
 * Permite forzar fallos de carga y de guardado para comprobar que el repositorio no
 * pierde el estado en memoria ni finge haber persistido.
 */
class AlmacenContactosEnMemoria(
    private var estado: EstadoContactos = EstadoContactos(),
    private val fallaAlCargar: Boolean = false,
    private val fallaAlGuardar: Boolean = false,
) : AlmacenContactos {

    /** Número de guardados correctos, para comprobar que cada cambio se escribe. */
    var guardados: Int = 0
        private set

    override fun cargar(): EstadoContactos {
        if (fallaAlCargar) throw IllegalStateException("fallo simulado de carga")
        return estado
    }

    override fun guardar(estado: EstadoContactos): Result<Unit> {
        if (fallaAlGuardar) {
            return Result.failure(IllegalStateException("fallo simulado de guardado"))
        }

        this.estado = estado
        guardados++
        return Result.success(Unit)
    }
}
