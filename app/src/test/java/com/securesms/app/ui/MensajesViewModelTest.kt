// Comprueba la orquestación SS1 con contactos, transporte y reloj en memoria.
package com.securesms.app.ui

import com.securesms.app.identity.AlmacenContactosEnMemoria
import com.securesms.app.identity.Contacto
import com.securesms.app.identity.RepositorioContactos
import com.securesms.app.integration.FakeCryptoGateway
import com.securesms.app.integration.Perfil
import com.securesms.app.messaging.EstadoSim
import com.securesms.app.messaging.FragmentAssembler
import com.securesms.app.messaging.RegistroSmsFalso
import com.securesms.app.messaging.RelojFalso
import com.securesms.app.messaging.TransporteSmsFalso
import com.securesms.app.messaging.encode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MensajesViewModelTest {
    private val numero = "+52 (55) 1234-5678"
    private val perfil = Perfil.ECC_P256
    private val repositorio = RepositorioContactos(AlmacenContactosEnMemoria())
    private val gateway = FakeCryptoGateway()
    private val transporte = TransporteSmsFalso()
    private val registro = RegistroSmsFalso()
    private val reloj = RelojFalso()
    private val modelo = MensajesViewModel(
        repositorio, gateway, transporte, registro, FragmentAssembler(reloj::ahora),
    )

    private fun verificarContacto(numero: String = this.numero) {
        repositorio.agregar(Contacto(numero, "Ana"))
        val clave = repositorio.importarClave(numero, perfil, "YWJj").getOrThrow()
        assertTrue(repositorio.verificarClave(numero, clave.huella))
        modelo.seleccionar(numero)
    }

    @Test
    fun `solo ofrece contactos verificados y los descubre al refrescar`() {
        repositorio.agregar(Contacto(numero, "Ana"))
        val clave = repositorio.importarClave(numero, perfil, "YWJj").getOrThrow()
        modelo.seleccionar(numero)
        assertNull(modelo.uiState.value.seleccionado)
        assertTrue(modelo.uiState.value.contactos.isEmpty())

        assertTrue(repositorio.verificarClave(numero, clave.huella))
        modelo.refrescar()
        assertEquals(perfil, modelo.uiState.value.contactos.single().perfil)
        assertTrue(modelo.uiState.value.esDemo)
    }

    @Test
    fun `bloquea envio sin contacto texto permiso o sim`() = runBlocking {
        assertFalse(modelo.enviar("Hola"))
        verificarContacto()
        assertFalse(modelo.enviar(""))
        assertFalse(modelo.enviar("Hola"))
        modelo.actualizarPermisos(enviar = true, recibir = true)
        transporte.sim = EstadoSim.AUSENTE
        assertFalse(modelo.enviar("Hola"))
        assertEquals(EstadoSim.AUSENTE, modelo.uiState.value.estadoSim)
        assertTrue(transporte.envios.isEmpty())
    }

    @Test
    fun `un numero invalido no llega al transporte`() = runBlocking {
        verificarContacto("abc")
        modelo.actualizarPermisos(enviar = true, recibir = true)
        assertFalse(modelo.enviar("Hola"))
        assertTrue(transporte.envios.isEmpty())
    }

    @Test
    fun `normaliza el destino y registra un envio aceptado`() = runBlocking {
        verificarContacto()
        modelo.actualizarPermisos(enviar = true, recibir = true)
        assertTrue(modelo.enviar("Hola"))
        assertEquals("+525512345678", transporte.envios.single().numero)
        assertEquals(1, transporte.envios.single().textos.size)

        val mensaje = modelo.uiState.value.mensajes.single()
        assertEquals(EstadoMensaje.ACEPTADO_PARA_ENVIO, mensaje.estado)
        assertEquals("Hola", mensaje.texto)
        assertEquals(perfil, mensaje.perfil)
        assertEquals("ECC_P256 | 9 B | 12 Base64 | 1 SMS", detalleDe(mensaje))
    }

    @Test
    fun `divide mensajes largos y conserva el texto si falla el transporte`() = runBlocking {
        verificarContacto()
        modelo.actualizarPermisos(enviar = true, recibir = true)
        assertTrue(modelo.enviar("x".repeat(200)))
        assertEquals(3, transporte.envios.single().textos.size)

        transporte.falla = true
        assertTrue(modelo.enviar("Hola"))
        val error = modelo.uiState.value.mensajes.first()
        assertEquals(EstadoMensaje.ERROR, error.estado)
        assertEquals("Hola", error.texto)
        assertEquals("no se pudo enviar todo el mensaje", error.detalle)
    }

    @Test
    fun `reconstruye fuera de orden y evita duplicados antes y despues`() {
        verificarContacto()
        val original = "x".repeat(200)
        val partes = encode(gateway.cifrar(original, perfil).getOrThrow()).reversed()
        val remitente = "+525512345678"
        repeat(2) { registro.simular(remitente, partes[0]) }
        val incompleto = modelo.uiState.value.mensajes.single()
        assertEquals(EstadoMensaje.INCOMPLETO, incompleto.estado)
        assertEquals("Ana", incompleto.contacto)
        assertEquals("1 de 3 fragmentos", detalleDe(incompleto))
        assertNull(incompleto.texto)
        assertNull(incompleto.perfil)

        registro.simular(remitente, partes[1])
        assertEquals(2, modelo.uiState.value.mensajes.single().recibidos)
        registro.simular(remitente, partes[2])
        registro.simular(remitente, partes[0])
        val descifrado = modelo.uiState.value.mensajes.single()
        assertEquals(EstadoMensaje.DESCIFRADO, descifrado.estado)
        assertEquals(original, descifrado.texto)
        assertEquals(perfil, descifrado.perfil)
    }

    @Test
    fun `ignora sms ajenos al protocolo y expira incompletos`() {
        val remitente = "5551234"
        listOf("Hola", "SS10:Hola", "SS1:no").forEach { registro.simular(remitente, it) }
        assertTrue(modelo.uiState.value.mensajes.isEmpty())
        assertEquals("", modelo.uiState.value.aviso)

        val primeraParte = encode(gateway.cifrar("x".repeat(200), perfil).getOrThrow()).first()
        registro.simular(remitente, primeraParte)
        reloj.avanzar(5 * 60 * 1000L + 1)
        modelo.refrescar()
        val expirado = modelo.uiState.value.mensajes.single()
        assertEquals(EstadoMensaje.EXPIRADO, expirado.estado)
        assertNull(expirado.texto)
    }

    @Test
    fun `un envelope ajeno produce un error generico y no se vuelve a procesar`() {
        val parte = encode("NO-DEMO".toByteArray()).single()
        repeat(2) { registro.simular("5551234", parte) }
        val error = modelo.uiState.value.mensajes.single()
        assertEquals(EstadoMensaje.ERROR, error.estado)
        assertNull(error.texto)
        assertEquals("no se pudo descifrar el mensaje", error.detalle)
    }
}
