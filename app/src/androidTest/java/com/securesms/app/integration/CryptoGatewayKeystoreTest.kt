// Valida el adaptador con claves no exportables en los cuatro perfiles.
package com.securesms.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securesms.app.identity.AlmacenDeClavesAndroid
import com.securesms.app.identity.ClavePublicaImportada
import com.securesms.app.identity.Contacto
import com.securesms.app.identity.EstadoClave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CryptoGatewayKeystoreTest {
    @Test
    fun cifraYDescifraConClavesNoExportablesEnCadaPerfil() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val prefijo = "securesms.test.${UUID.randomUUID()}"
        val almacen = AlmacenDeClavesAndroid(contexto, prefijo)
        val gateway = CryptoGatewayReal(almacen::parParaDescifrar)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        for (perfil in Perfil.entries) {
            val alias = "$prefijo.${perfil.name.lowercase()}.v1"
            try {
                almacen.generarSiFalta(perfil).getOrThrow()
                assertNull("$perfil", keyStore.getKey(alias, null).encoded)
                val contacto = Contacto("+525512345678", "Prueba")
                val clave = ClavePublicaImportada.crear(
                    contacto, perfil, almacen.clavePublicaBase64(perfil).getOrThrow(),
                ).getOrThrow()
                assertTrue(gateway.cifrar("Prueba", clave).isFailure)

                val verificada = clave.copy(estado = EstadoClave.VERIFICADA)
                for (texto in listOf("", "SecureSMS", "México 🔐")) {
                    val envelope = gateway.cifrar(texto, verificada).getOrThrow()
                    assertEquals(
                        perfil.name, MensajeDescifrado(texto, perfil),
                        gateway.descifrar(envelope).getOrThrow(),
                    )
                }
            } finally {
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            }
        }
    }
}
