/*
Código de prueba interno. Objetivo: Aplicar una clase y un print que permita
Visualizar su información
*/

package com.securesms.app.ui

class Destinatario(val numero: String, val perfil: String, val huella: String?)

// Obtiene un objeto destinario, bytesenvelope y cantidad sms para imprimir los datos
fun resume(destinatario: Destinatario, bytesEnvelope: Int, cantidadSms: Int?):String {
    return "$destinatario.numero | $destinatario.perfil | $bytesEnvelope bytes | ${cantidadSms ?: "pendiente"} | ${destinatario.huella ?: "sin verificar"}"
}

fun main() {
    val nuevo_destinatario = Destinatario("+59170000000", "ECC_P256", "A1B2")
    var resultado = resume(nuevo_destinatario, 640, 5)
    println("Resultado para el destinatario $nuevo_destinatario.numero")
    println(resultado)
}

