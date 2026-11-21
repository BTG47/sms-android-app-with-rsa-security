/*
 * Punto de entrada mínimo de la aplicación.
 * Responsabilidad: inicializar la interfaz Compose; aún no contiene navegación ni permisos.
 */

package com.securesms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/** Actividad principal del prototipo. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("SecureSMS")
            }
        }
    }
}
