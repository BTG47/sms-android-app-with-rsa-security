/*
 * Punto de entrada de SecureSMS y navegación entre sus pantallas.
 */

package com.securesms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securesms.app.identity.AlmacenDeClavesAndroid
import com.securesms.app.identity.ArchivoContactos
import com.securesms.app.identity.RepositorioContactos
import com.securesms.app.integration.CryptoGatewayReal
import com.securesms.app.integration.FakeCryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.ui.ContactosViewModel
import com.securesms.app.ui.PantallaContactos
import com.securesms.app.ui.ResumenViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.securesms.app.messaging.RegistroSmsAndroid
import com.securesms.app.messaging.TransporteSmsAndroid
import com.securesms.app.ui.MensajesViewModel
import com.securesms.app.ui.PantallaMensajes
import java.io.File

/** Pantallas del prototipo, sin biblioteca de navegación. */
private enum class Pantalla {
    CONTACTOS,
    MENSAJES,
    RESUMEN,
}

/** Actividad principal del prototipo. */
class MainActivity : ComponentActivity() {
    companion object {
        private lateinit var repositorio: RepositorioContactos
        private lateinit var almacenClaves: AlmacenDeClavesAndroid
        private val inicializado get() = ::repositorio.isInitialized
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contexto = applicationContext
        if (!inicializado) {
            val archivo = File(contexto.noBackupFilesDir, "contactos.json")
            repositorio = RepositorioContactos(ArchivoContactos(archivo))
            almacenClaves = AlmacenDeClavesAndroid(contexto)
        }
        val proveedor = ViewModelProvider(this, viewModelFactory {
            initializer { ContactosViewModel(repositorio, almacenClaves) }
            initializer { ResumenViewModel(FakeCryptoGateway()) }
            initializer {
                MensajesViewModel(
                    repositorio, CryptoGatewayReal(almacenClaves::parParaDescifrar),
                    TransporteSmsAndroid(contexto), RegistroSmsAndroid(),
                )
            }
        })
        val contactos = proveedor[ContactosViewModel::class.java]
        val resumen = proveedor[ResumenViewModel::class.java]
        val mensajes = proveedor[MensajesViewModel::class.java]

        setContent {
            val colores = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(contexto)
            } else {
                dynamicLightColorScheme(contexto)
            }
            MaterialTheme(colorScheme = colores) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var pantalla by rememberSaveable { mutableStateOf(Pantalla.CONTACTOS) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        Surface(tonalElevation = 2.dp) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    Pantalla.CONTACTOS to "Contacts",
                                    Pantalla.MENSAJES to "Messages",
                                    Pantalla.RESUMEN to "Diagnostics",
                                ).forEach { (destino, etiqueta) ->
                                    FilterChip(
                                        selected = pantalla == destino,
                                        onClick = { pantalla = destino },
                                        label = { Text(etiqueta) },
                                    )
                                }
                            }
                        }

                        when (pantalla) {
                            Pantalla.CONTACTOS -> PantallaContactos(contactos) { numero ->
                                mensajes.seleccionar(numero)
                                pantalla = Pantalla.MENSAJES
                            }
                            Pantalla.MENSAJES -> PantallaMensajes(mensajes) { pantalla = Pantalla.CONTACTOS }
                            Pantalla.RESUMEN -> ResumenPantalla(resumen)
                        }
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
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
        if (viewModel.esDemo) {
            Text("See the encrypted payload size using a demo. No SMS is sent.")
        }
        Text(estado.resumen)
        Button(onClick = { viewModel.cifrar("Hello", Perfil.RSA_2048) }) {
            Text("Run encryption demo")
        }
    }
}
