package com.securesms.app.messaging

/** Identifica un ensamblado por remitente e identificador de mensaje. */
data class ClaveEnsamblado(
    val remitente: String,
    val messageId: String
)

/** Estado observable de un ensamblado. */
enum class EstadoEnsamblado { INCOMPLETO, COMPLETO, EXPIRADO }

/**
 * Agrupa Fragmentos en memoria y entrega el Envelope solo cuando el conjunto es íntegro.
 * El reloj se inyecta para controlar la expiración en las pruebas.
 */
class FragmentAssembler(
    private val reloj: () -> Long,
    private val plazoMs: Long = 5 * 60 * 1000,
    private val maxFragmentos: Int = 99,
    private val maxGrupos: Int = 16
) {
    /** Estado interno de un grupo: textos recibidos y momento del último fragmento. */
    private data class Ensamblado(
        var ultimoFragmentoEn: Long,
        val textos: MutableList<String> = mutableListOf()
    )

    private val grupos = mutableMapOf<ClaveEnsamblado, Ensamblado>()

    /**
     * Incorpora un texto SS1 al grupo correspondiente y devuelve su estado resultante.
     * Un texto que no se puede parsear no completa ningún grupo.
     */
    fun agregar(remitente: String, texto: String): EstadoEnsamblado {
        // Tarea 1: parsear, obtener la clave, guardar el texto y devolver el estado.
        val fragmento = parsear(texto) ?: return EstadoEnsamblado.INCOMPLETO
        val clave = ClaveEnsamblado(remitente, fragmento.messageId)
        val grupo = grupos.getOrPut(clave) { Ensamblado(reloj()) }

        if (texto !in grupo.textos) {
            grupo.textos.add(texto)
            grupo.ultimoFragmentoEn = reloj()
        }

        return estado(remitente, fragmento.messageId) ?: EstadoEnsamblado.INCOMPLETO
    }

    /** Devuelve el estado del grupo, o null si no existe o expiró. */
    fun estado(remitente: String, messageId: String): EstadoEnsamblado? {
        // Tarea 1: deduce el estado a partir de los textos guardados.
        // Buscar la información del Ensamblado
        val clave = ClaveEnsamblado(remitente, messageId)
        val infoEnsamblada = grupos[clave] ?: return null

        if (decode(infoEnsamblada.textos) != null) return EstadoEnsamblado.COMPLETO else return EstadoEnsamblado.INCOMPLETO

    }

    /** Devuelve los bytes del Envelope solo si el grupo está completo. */
    fun envelope(remitente: String, messageId: String): ByteArray? {
        // Tarea 1: usa decode sobre los textos del grupo.
        val clave = ClaveEnsamblado(remitente, messageId)
        val infoEnsamblada = grupos[clave] ?: return null

        val decodedValue = decode(infoEnsamblada.textos)
        if (decodedValue != null) return decodedValue
        return null
    }

    /** Elimina un grupo; permite descartar un ensamblado ya consumido. */
    fun descartar(remitente: String, messageId: String) {
        // Tarea 1: quita la clave del mapa.
        val clave = ClaveEnsamblado(remitente, messageId)
        val infoEnsamblada = grupos[clave]
        if (infoEnsamblada != null){
            grupos.remove(clave)
        }
    }

    /**
     * Barrido de grupos incompletos inactivos.
     * Se invocará al inicio de cada método público en la Tarea 2.
     */
    private fun barrerInactivos(ahora: Long) {
        // Tarea 2: eliminar los grupos INCOMPLETO con ahora - ultimoFragmentoEn >= plazoMs.
        TODO("Implementar el barrido por inactividad")
    }
}
