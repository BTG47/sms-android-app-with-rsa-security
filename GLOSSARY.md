# SecureSMS

Glosario del frente Android de SecureSMS. Define el lenguaje del dominio; no contiene formatos de datos, APIs ni decisiones de implementación.

## Objetos del dominio

**Envelope**:
Unidad de datos cifrados que se produce al cifrar un mensaje y llega sin alteración hasta el descifrado.
_Avoid_: ciphertext, paquete cifrado, mensaje cifrado

**Fragmento**:
Una de las partes lógicas en que se divide un envelope para su transporte por SMS.
_Avoid_: SMS cifrado, parte, segmento

**SMS**:
Unidad de transporte de la red móvil; puede contener un fragmento de SecureSMS o un mensaje ordinario.
_Avoid_: fragmento, mensaje, paquete

**Mensaje**:
Texto que el remitente escribió y que el destinatario lee después del descifrado.
_Avoid_: payload, contenido, dato

## Personas y claves

**Contacto**:
Persona identificada por un número telefónico con la que se desea intercambiar mensajes.
_Avoid_: destinatario, usuario, clave

**Clave pública importada**:
Material público asociado a un contacto y a un perfil criptográfico; puede estar verificada o todavía no.
_Avoid_: contacto, certificado, identidad

**Verificación de clave**:
Confirmación de la huella de una clave pública por un canal distinto de aquel por el que se importó.
_Avoid_: importación, confianza automática

## Estados del transporte

**Aceptado para envío**:
El sistema de SMS recibió el fragmento; no implica que el destinatario lo haya recibido.
_Avoid_: enviado, entregado

**Recibido y reconstruido**:
Los fragmentos necesarios llegaron y forman un envelope íntegro; todavía no se descifró.
_Avoid_: descifrado, leído

**Descifrado**:
El envelope íntegro produjo un mensaje legible con la clave privada correcta.
_Avoid_: recibido, aceptado
