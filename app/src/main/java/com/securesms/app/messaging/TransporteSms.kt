package com.securesms.app.messaging

/** Disponibilidad de la SIM para el envío por la suscripción de SMS predeterminada. */
enum class EstadoSim { LISTA, AUSENTE, NO_DISPONIBLE }

/** Puerto para entregar fragmentos al sistema de telefonía. */
interface TransporteSms {
    fun estadoSim(): EstadoSim

    /** Devuelve el resultado de solicitar el envío de cada fragmento al sistema. */
    fun enviar(numero: String, textos: List<String>): Result<Unit>
}
