// Distingue los segmentos físicos de Android de los fragmentos lógicos SS1.
package com.securesms.app.messaging

/** Reconstruye los segmentos de un mismo SMS antes de pasar su SS1 al ensamblador. */
internal fun fragmentosSmsEntrantes(partes: List<String>): List<String> {
    if (partes.size > 1 && partes.first().startsWith("SS1:") &&
        partes.drop(1).none { it.startsWith("SS1:") }
    ) {
        return listOf(partes.joinToString("")).filter(::esFragmentoSms)
    }
    return partes.filter(::esFragmentoSms)
}

private fun esFragmentoSms(cuerpo: String): Boolean {
    // Longitud máxima de SS1 con sus separadores y 130 caracteres de payload.
    if (cuerpo.length > 153 || !cuerpo.startsWith("SS1:")) return false
    val parte = parsear(cuerpo) ?: return false
    return parte.index <= parte.count && parte.payload.isNotEmpty() &&
        parte.payload.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+/=" }
}
