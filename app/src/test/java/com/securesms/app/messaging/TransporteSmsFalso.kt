package com.securesms.app.messaging

/** Doble de prueba que conserva las solicitudes de envío exitosas. */
class TransporteSmsFalso(
    var sim: EstadoSim = EstadoSim.LISTA,
    var falla: Boolean = false,
) : TransporteSms {
    data class Envio(val numero: String, val textos: List<String>)

    val envios = mutableListOf<Envio>()

    override fun estadoSim(): EstadoSim = sim

    override fun enviar(numero: String, textos: List<String>): Result<Unit> = runCatching {
        check(sim == EstadoSim.LISTA && !falla) { "Envio no disponible" }
        envios += Envio(numero, textos.toList())
    }
}
