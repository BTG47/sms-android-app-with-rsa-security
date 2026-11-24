/*
 * Punto de entrada mínimo de la aplicación.
 * Responsabilidad: inicializar la interfaz Compose; aún no contiene navegación ni permisos.
 */

package com.securesms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securesms.app.integration.FakeCryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.ui.ContactosViewModel
import com.securesms.app.ui.PantallaContactos
import com.securesms.app.ui.ResumenViewModel

/** Pantallas del prototipo, sin biblioteca de navegación. */
private enum class Pantalla {
    CONTACTOS,
    RESUMEN,
}

/** Actividad principal del prototipo. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resumen = ResumenViewModel(FakeCryptoGateway())
        val contactos = ContactosViewModel()

        setContent {
            MaterialTheme {
                var pantalla by remember { mutableStateOf(Pantalla.CONTACTOS) }

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { pantalla = Pantalla.CONTACTOS }) {
                            Text("Contactos")
                        }
                        TextButton(onClick = { pantalla = Pantalla.RESUMEN }) {
                            Text("Resumen demo")
                        }
                    }

                    when (pantalla) {
                        Pantalla.CONTACTOS -> PantallaContactos(contactos)
                        Pantalla.RESUMEN -> ResumenPantalla(resumen)
                    }
                }
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
