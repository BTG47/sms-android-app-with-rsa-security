package com.securesms.app.ui

import com.securesms.app.identity.EstadoClave
import com.securesms.app.integration.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NUMERO = "5512345678"
private const val MATERIAL = "YWJj"

/** Casos del estado que alimenta la pantalla de contactos. */
class ContactosViewModelTest {

    private val viewModel = ContactosViewModel()

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
}
