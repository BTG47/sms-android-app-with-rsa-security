package com.securesms.app.ui

import androidx.lifecycle.ViewModel
import com.securesms.app.integration.CryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.messaging.encode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Texto fijo para no exponer el motivo de un fallo en la interfaz. */
private const val TEXTO_ERROR = "error al cifrar"

/**
 * Estado que consume la pantalla: solo el resumen y los bytes del Envelope.
 *
 * No contiene texto plano, claves ni huellas sin verificar.
 */
data class UiState(
    val resumen: String = "",
    val envelope: ByteArray? = null,
)

/**
 * ViewModel mínimo del diagnóstico de transporte.
 *
 * Recibe la fachada de cifrado por constructor y deriva las métricas con el codec propio
 * de la aplicación. Un fallo de la gateway se convierte en un estado de error sin detalles.
 */
class ResumenViewModel(private val gateway: CryptoGateway) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())

    /** Estado actual de la pantalla. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Indica si la gateway inyectada es una demostración. */
    val esDemo: Boolean get() = gateway.esDemo

    /**
     * Cifra [texto] con [perfil] y publica el resumen resultante.
     *
     * Los bytes vacíos se tratan como fallo porque no pueden viajar por el codec.
     */
    fun cifrar(texto: String, perfil: Perfil) {
        _uiState.value = gateway.cifrar(texto, perfil).fold(
            onSuccess = { bytes ->
                if (bytes.isEmpty()) {
                    UiState(resumen = TEXTO_ERROR, envelope = null)
                } else {
                    UiState(
                        resumen = resumen(
                            DatosDeEnvio(
                                numero = "sin destinatario",
                                perfil = perfil.name,
                                huella = null,
                            ),
                            bytes.size,
                            encode(bytes).size,
                        ),
                        envelope = bytes,
                    )
                }
            },
            onFailure = { UiState(resumen = TEXTO_ERROR, envelope = null) },
        )
    }
}
