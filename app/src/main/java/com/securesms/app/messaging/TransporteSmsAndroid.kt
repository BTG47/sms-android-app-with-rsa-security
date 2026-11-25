package com.securesms.app.messaging

import android.content.Context
import android.telephony.SmsManager
import android.telephony.TelephonyManager

/** Entrega cada fragmento a Android como un SMS independiente. */
class TransporteSmsAndroid(context: Context) : TransporteSms {
    private val contexto = context.applicationContext

    override fun estadoSim(): EstadoSim =
        when (contexto.getSystemService(TelephonyManager::class.java)?.simState) {
            TelephonyManager.SIM_STATE_READY -> EstadoSim.LISTA
            TelephonyManager.SIM_STATE_ABSENT -> EstadoSim.AUSENTE
            else -> EstadoSim.NO_DISPONIBLE
        }

    override fun enviar(numero: String, textos: List<String>): Result<Unit> = runCatching {
        val gestor = checkNotNull(contexto.getSystemService(SmsManager::class.java))
        textos.forEach { texto ->
            gestor.sendTextMessage(numero, null, texto, null, null)
        }
    }
}
