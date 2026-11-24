/*
 * Punto de entrada mínimo de la aplicación.
 * Responsabilidad: inicializar la interfaz Compose; aún no contiene navegación ni permisos.
 */

package com.securesms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.securesms.app.integration.FakeCryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.ui.ResumenViewModel

/** Actividad principal del prototipo. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ResumenViewModel(FakeCryptoGateway())
        setContent {
            MaterialTheme {
                ResumenPantalla(viewModel)
            }
        }
    }
}

/** Pantalla de diagnóstico del demo; no envía SMS ni conserva texto plano. */
@Composable
private fun ResumenPantalla(viewModel: ResumenViewModel) {
    val estado by viewModel.uiState.collectAsState()

    Column {
        Text("SecureSMS")
        if (viewModel.esDemo) {
            Text("modo demo")
        }
        Text(estado.resumen)
        Button(onClick = { viewModel.cifrar("Hola", Perfil.RSA_2048) }) {
            Text("Cifrar demo")
        }
    }
}
