package com.securesms.app.messaging

import java.security.SecureRandom
import java.util.Base64

/** Cantidad máxima de caracteres GSM-7 por payload, según el contrato v0.2. */
private const val TAMANO_PAYLOAD = 130

/** Tope de fragmentos por envío, según el contrato v0.2. */
private const val MAX_FRAGMENTOS = 99

/**
 * Fragmento lógico de un Envelope, preparado para su transporte por SMS.
 * El prefijo SS1 y los anchos fijos se aplican al construir el encabezado.
 */
data class Fragmento(
    val messageId: String,
    val index: Int,
    val count: Int,
    val payload: String
)

/**
 * Construye la representación textual que viaja en un SMS.
 * El id usa doce caracteres hexadecimales y el índice y el total, dos, según el contrato v0.2.
 */
fun encabezado(fragmento: Fragmento): String {
    // Verificaciones de datos del fragmento.
    require(fragmento.messageId.length == 12) { "El id debe tener 12 caracteres" }
    require(fragmento.messageId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "El id debe ser hexadecimal"
    }
    require(fragmento.index in 1..99) { "El índice del fragmento debe estar entre 1 y 99" }
    require(fragmento.count in 1..99) { "El total de fragmentos debe estar entre 1 y 99" }
    val index = fragmento.index.toString().padStart(2, '0')
    val count = fragmento.count.toString().padStart(2, '0')
    return "SS1:${fragmento.messageId}:${index}:${count}:${fragmento.payload}"
}

/**
 * Convierte un texto SS1 en un [Fragmento], o devuelve null si no es canónico.
 * La validación se delega a [encabezado] para no duplicar las reglas del contrato.
 */
fun parsear(texto: String): Fragmento? = runCatching {
    val partes = texto.split(':', limit = 5)
    require(partes.size == 5 && partes[0] == "SS1")
    val fragmento = Fragmento(partes[1], partes[2].toInt(), partes[3].toInt(), partes[4])
    require(encabezado(fragmento) == texto)
    fragmento
}.getOrNull()

/**
 * Divide un Envelope opaco en Fragmentos SS1 listos para su transporte por SMS.
 * El contenido del envelope no se interpreta: solo se codifica en Base64 y se parte.
 *
 * @param envelope bytes cifrados producidos fuera de este módulo.
 * @return un texto SS1 por fragmento, con índices desde 1 y un id común al envío.
 */
fun encode(envelope: ByteArray): List<String> {
    require(envelope.isNotEmpty()) { "El envelope no puede estar vacío" }

    val base64 = Base64.getEncoder().encodeToString(envelope)
    val partes = base64.chunked(TAMANO_PAYLOAD)
    require(partes.size <= MAX_FRAGMENTOS) { "El mensaje supera los 99 fragmentos" }

    val idBytes = ByteArray(6)
    SecureRandom().nextBytes(idBytes)
    val messageId = idBytes.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }.uppercase()

    return partes.mapIndexed { position, payload ->
        encabezado(Fragmento(messageId, position + 1, partes.size, payload))
    }
}

/**
 * Reconstruye el Envelope original a partir de Fragmentos SS1.
 *
 * Toler duplicados idénticos y acepta cualquier orden de llegada. Devuelve null
 * si falta una parte, si hay contradicciones o si algún fragmento no es válido.
 *
 * @param parts textos SS1 recibidos, potencialmente desordenados o repetidos.
 * @return los bytes originales del Envelope, o null cuando el conjunto no es íntegro.
 */
fun decode(parts: List<String>): ByteArray? {
    // mapNotNull descarta lo inválido; la comparación de tamaños lo detecta igual.
    val fragmentos = parts.mapNotNull(::parsear)
    if (fragmentos.size != parts.size || fragmentos.isEmpty()) return null

    val validos = fragmentos.distinct()
    val primero = validos.first()
    if (validos.any { it.messageId != primero.messageId || it.count != primero.count }) return null

    val porIndice = validos.groupBy { it.index }
    if (porIndice.any { (_, grupo) -> grupo.map { it.payload }.distinct().size != 1 }) return null
    if (porIndice.keys.sorted() != (1..primero.count).toList()) return null
    if (validos.any { it.payload.length > TAMANO_PAYLOAD }) return null

    val base64 = porIndice.toSortedMap().values.joinToString("") { it.first().payload }
    return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
}
