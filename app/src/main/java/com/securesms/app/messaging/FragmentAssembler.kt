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
    /** Estado interno de un grupo: textos recibidos, último fragmento y marca de expiración. */
    private data class Ensamblado(
        var ultimoFragmentoEn: Long,
        var expirado: Boolean = false,
        val textos: MutableList<String> = mutableListOf()
    )

    private val grupos = mutableMapOf<ClaveEnsamblado, Ensamblado>()

    /**
     * Incorpora un texto SS1 al grupo correspondiente y devuelve su estado resultante.
     * Un texto que no se puede parsear no completa ningún grupo.
     */
    fun agregar(remitente: String, texto: String): EstadoEnsamblado {
        val fragmento = parsear(texto) ?: return EstadoEnsamblado.INCOMPLETO
        val clave = ClaveEnsamblado(remitente, fragmento.messageId)

        val grupo = grupos[clave]
        val activo = if (grupo == null || grupo.expirado) {
            Ensamblado(ultimoFragmentoEn = reloj()).also { grupos[clave] = it }
        } else {
            grupo
        }

        if (texto !in activo.textos) {
            activo.textos.add(texto)
            activo.ultimoFragmentoEn = reloj()
        }

        return estado(remitente, fragmento.messageId) ?: EstadoEnsamblado.INCOMPLETO
    }

    /** Devuelve el estado del grupo, EXPIRADO si se marcó por inactividad, o null si no existe. */
    fun estado(remitente: String, messageId: String): EstadoEnsamblado? {
        barrerInactivos(reloj())
        val grupo = grupos[ClaveEnsamblado(remitente, messageId)] ?: return null
        if (grupo.expirado) return EstadoEnsamblado.EXPIRADO
        return if (decode(grupo.textos) != null) {
            EstadoEnsamblado.COMPLETO
        } else {
            EstadoEnsamblado.INCOMPLETO
        }
    }

    /** Devuelve los bytes del Envelope solo si el grupo está completo y no expiró. */
    fun envelope(remitente: String, messageId: String): ByteArray? {
        barrerInactivos(reloj())
        val grupo = grupos[ClaveEnsamblado(remitente, messageId)] ?: return null
        if (grupo.expirado) return null
        return decode(grupo.textos)
    }

    /** Elimina un grupo; permite descartar un ensamblado ya consumido o expirado. */
    fun descartar(remitente: String, messageId: String) {
        barrerInactivos(reloj())
        grupos.remove(ClaveEnsamblado(remitente, messageId))
    }

    /**
     * Marca como expirados los grupos incompletos sin actividad durante [plazoMs].
     * Los grupos completos nunca se marcan y los expirados se conservan para que
     * `estado` pueda exponerlos hasta que llegue un fragmento nuevo o se descarten.
     */
    private fun barrerInactivos(ahora: Long) {
        grupos.values.forEach { grupo ->
            val inactivo = ahora - grupo.ultimoFragmentoEn >= plazoMs
            if (!grupo.expirado && inactivo && decode(grupo.textos) == null) {
                grupo.expirado = true
            }
        }
    }
}
