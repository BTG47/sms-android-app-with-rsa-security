package com.securesms.app.ui

import com.securesms.app.identity.AlmacenContactosEnMemoria
import com.securesms.app.identity.AlmacenDeClavesFalso
import com.securesms.app.identity.EstadoClave
import com.securesms.app.identity.Huella
import com.securesms.app.identity.RepositorioContactos
import com.securesms.app.integration.Perfil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NUMERO = "5512345678"
private const val MATERIAL = "YWJj"

/** Casos del estado que alimenta la pantalla de contactos. */
class ContactosViewModelTest {

    private val viewModel = ContactosViewModel(
        RepositorioContactos(AlmacenContactosEnMemoria()),
        AlmacenDeClavesFalso(),
    )

    private fun agregar() {
        viewModel.agregar(NUMERO, "Ana")
    }

    private fun importar() {
        agregar()
        viewModel.importar(NUMERO, Perfil.ECC_P256, MATERIAL)
    }

    @Test
    fun `agregar deja el contacto sin importar`() {
        agregar()

        val fila = viewModel.uiState.value.filas.single()
        assertEquals(EstadoClave.SIN_IMPORTAR, fila.estado)
        assertNull(fila.huella)
        assertFalse(viewModel.puedeEnviar(NUMERO))
    }

    @Test
    fun `numero vacio no crea contacto`() {
        viewModel.agregar("   ", "Ana")

        assertTrue(viewModel.uiState.value.filas.isEmpty())
        assertTrue(viewModel.uiState.value.aviso.isNotBlank())
    }

    @Test
    fun `importar muestra la huella y no verifica`() {
        importar()

        val fila = viewModel.uiState.value.filas.single()
        assertEquals(EstadoClave.IMPORTADA_SIN_VERIFICAR, fila.estado)
        assertEquals(64, fila.huella!!.length)
        assertFalse(viewModel.puedeEnviar(NUMERO))
    }

    @Test
    fun `verificar con huella distinta mantiene el estado`() {
        importar()

        viewModel.verificar(NUMERO, "00")

        assertEquals(
            EstadoClave.IMPORTADA_SIN_VERIFICAR,
            viewModel.uiState.value.filas.single().estado,
        )
        assertFalse(viewModel.puedeEnviar(NUMERO))
    }

    @Test
    fun `verificar con la huella correcta habilita el envio`() {
        importar()
        val huella = viewModel.uiState.value.filas.single().huella!!

        viewModel.verificar(NUMERO, huella)

        assertEquals(EstadoClave.VERIFICADA, viewModel.uiState.value.filas.single().estado)
        assertTrue(viewModel.puedeEnviar(NUMERO))
    }

    @Test
    fun `exportar publica el material importado`() {
        importar()

        viewModel.exportar(NUMERO)

        assertEquals(MATERIAL, viewModel.uiState.value.exportado)
    }

    @Test
    fun `importar material invalido no guarda clave`() {
        agregar()

        viewModel.importar(NUMERO, Perfil.ECC_P256, "%%%")

        assertEquals(
            EstadoClave.SIN_IMPORTAR,
            viewModel.uiState.value.filas.single().estado,
        )
        assertTrue(viewModel.uiState.value.aviso.isNotBlank())
    }

    @Test
    fun `si la carga falla lo avisa`() {
        val viewModel = ContactosViewModel(
            RepositorioContactos(AlmacenContactosEnMemoria(fallaAlCargar = true)),
            AlmacenDeClavesFalso(),
        )

        assertTrue(viewModel.uiState.value.filas.isEmpty())
        assertTrue(viewModel.uiState.value.aviso.isNotBlank())
    }

    @Test
    fun `sin clave propia no hay miClave`() {
        assertNull(viewModel.uiState.value.miClave)
    }

    @Test
    fun `generar publica la clave y su huella`() {
        runBlocking { viewModel.generarMiClave() }

        val miClave = viewModel.uiState.value.miClave!!
        assertEquals(MATERIAL, miClave.base64)
        assertEquals(Huella.calcular(MATERIAL).getOrThrow(), miClave.huella)
    }

    @Test
    fun `al iniciar publica la clave que ya existia`() {
        val viewModel = ContactosViewModel(
            RepositorioContactos(AlmacenContactosEnMemoria()),
            AlmacenDeClavesFalso(perfilInicial = Perfil.RSA_2048),
        )

        assertEquals(MATERIAL, viewModel.uiState.value.miClave!!.base64)
    }

    @Test
    fun `un fallo de generacion deja aviso y no publica clave`() {
        val viewModel = ContactosViewModel(
            RepositorioContactos(AlmacenContactosEnMemoria()),
            AlmacenDeClavesFalso(fallaAlGenerar = true),
        )

        runBlocking { viewModel.generarMiClave() }

        assertNull(viewModel.uiState.value.miClave)
        assertTrue(viewModel.uiState.value.aviso.isNotBlank())
    }

    @Test
    fun `si la generacion falla pero la clave quedo, la publica`() {
        val viewModel = ContactosViewModel(
            RepositorioContactos(AlmacenContactosEnMemoria()),
            AlmacenDeClavesFalso(fallaPeroQuedaGenerada = true),
        )
        assertNull(viewModel.uiState.value.miClave)

        runBlocking { viewModel.generarMiClave() }

        assertEquals(MATERIAL, viewModel.uiState.value.miClave!!.base64)
    }
}
