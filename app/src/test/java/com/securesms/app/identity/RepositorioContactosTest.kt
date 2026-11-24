package com.securesms.app.identity

import com.securesms.app.integration.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MATERIAL = "YWJj"
private const val OTRO_MATERIAL = "ZGVm"

/** Casos del repositorio en memoria: importar no verifica y verificar habilita el envío. */
class RepositorioContactosTest {

    private val repositorio = RepositorioContactos()
    private val contacto = Contacto("5512345678", "Ana")

    private fun importar(material: String = MATERIAL) {
        repositorio.agregar(contacto)
        repositorio.importarClave(contacto.numero, Perfil.ECC_P256, material).getOrThrow()
    }

    private fun verificar() {
        val huella = repositorio.claveDe(contacto.numero)!!.huella
        assertTrue(repositorio.verificarClave(contacto.numero, huella))
    }

    @Test
    fun `un contacto sin clave no puede enviar`() {
        repositorio.agregar(contacto)

        assertNull(repositorio.claveDe(contacto.numero))
        assertFalse(repositorio.puedeEnviar(contacto.numero))
    }

    @Test
    fun `importar deja la clave sin verificar y sin habilitar envio`() {
        importar()

        val clave = repositorio.claveDe(contacto.numero)!!
        assertEquals(EstadoClave.IMPORTADA_SIN_VERIFICAR, clave.estado)
        assertFalse(clave.habilitaEnvio)
        assertFalse(repositorio.puedeEnviar(contacto.numero))
    }

    @Test
    fun `verificar con otra huella no cambia el estado`() {
        importar()

        assertFalse(repositorio.verificarClave(contacto.numero, "00"))

        assertEquals(
            EstadoClave.IMPORTADA_SIN_VERIFICAR,
            repositorio.claveDe(contacto.numero)!!.estado,
        )
        assertFalse(repositorio.puedeEnviar(contacto.numero))
    }

    @Test
    fun `verificar con la huella confirmada habilita el envio`() {
        importar()

        verificar()

        assertEquals(EstadoClave.VERIFICADA, repositorio.claveDe(contacto.numero)!!.estado)
        assertTrue(repositorio.puedeEnviar(contacto.numero))
    }

    @Test
    fun `verificar acepta la huella agrupada y en mayusculas`() {
        importar()
        val huella = repositorio.claveDe(contacto.numero)!!.huella
        val agrupada = huella.chunked(8).joinToString(" ").uppercase()

        assertTrue(repositorio.verificarClave(contacto.numero, agrupada))
    }

    @Test
    fun `volver a importar reinicia la verificacion`() {
        importar()
        verificar()

        repositorio.importarClave(contacto.numero, Perfil.ECC_P256, OTRO_MATERIAL).getOrThrow()

        assertEquals(
            EstadoClave.IMPORTADA_SIN_VERIFICAR,
            repositorio.claveDe(contacto.numero)!!.estado,
        )
        assertFalse(repositorio.puedeEnviar(contacto.numero))
    }

    @Test
    fun `exportar devuelve el material importado`() {
        importar()

        assertEquals(MATERIAL, repositorio.exportarClave(contacto.numero))
        assertNull(repositorio.exportarClave("0000000000"))
    }

    @Test
    fun `importar en un contacto desconocido falla sin guardar`() {
        val resultado = repositorio.importarClave("0000000000", Perfil.RSA_2048, MATERIAL)

        assertTrue(resultado.isFailure)
        assertNull(repositorio.claveDe("0000000000"))
    }

    @Test
    fun `estadoDe devuelve SIN_IMPORTAR cuando no hay clave`() {
        repositorio.agregar(contacto)

        assertEquals(EstadoClave.SIN_IMPORTAR, repositorio.estadoDe(contacto.numero))
    }

    @Test
    fun `estadoDe devuelve el estado guardado`() {
        importar()
        assertEquals(EstadoClave.IMPORTADA_SIN_VERIFICAR, repositorio.estadoDe(contacto.numero))

        verificar()
        assertEquals(EstadoClave.VERIFICADA, repositorio.estadoDe(contacto.numero))
    }
}
