package com.securesms.crypto.bench

/**
 * Estimates how many SMS segments a payload needs (3GPP TS 23.040 / 23.038 limits). These are
 * estimates for the analysis; the real count is the one Bruno's app gets from SmsManager.divideMessage.
 *
 *  - GSM-7 text: 160 chars in one SMS, 153 per part when concatenated (6-byte UDH).
 *  - UCS-2 text: 70 chars in one SMS, 67 per part.
 *  - 8-bit binary: 140 bytes in one SMS, 134 per part.
 *
 * Base64 output (A-Z a-z 0-9 + / =) only uses characters of the GSM-7 basic table, so an envelope
 * sent as Base64 text is counted with the GSM-7 limits.
 */
object SmsEstimator {
    fun base64Length(bytes: Int): Int = 4 * ((bytes + 2) / 3)

    fun segmentsGsm7(chars: Int): Int = segments(chars, 160, 153)
    fun segmentsUcs2(chars: Int): Int = segments(chars, 70, 67)
    fun segmentsBinary(bytes: Int): Int = segments(bytes, 140, 134)

    /** Segments needed by a plain (unencrypted) text, choosing GSM-7 or UCS-2 like a phone would. */
    fun segmentsForPlainText(text: String): Int =
        if (text.all { it in GSM7_BASIC }) segmentsGsm7(text.length) else segmentsUcs2(utf16Units(text))

    /** Segments needed to send [envelopeBytes] as Base64 text over GSM-7. */
    fun segmentsForEnvelopeBase64(envelopeBytes: Int): Int = segmentsGsm7(base64Length(envelopeBytes))

    private fun utf16Units(text: String) = text.length

    private fun segments(units: Int, single: Int, perPart: Int): Int = when {
        units <= 0 -> 1
        units <= single -> 1
        else -> (units + perPart - 1) / perPart
    }

    // GSM 03.38 basic character set (without the escape/extension table).
    private val GSM7_BASIC: Set<Char> = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        ).toSet()
}
