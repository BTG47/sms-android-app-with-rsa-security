# La verificación de clave no es la importación

Status: accepted

La clave pública de un contacto puede importarse por Base64 o QR sin que eso demuestre su autenticidad. Separamos `Contacto` de `Clave pública importada` y modelamos su confianza con tres estados —sin importar, importada sin verificar y verificada— en lugar de un booleano, porque este último no distingue "no tengo clave" de "la tengo pero no la confirmé por otro canal". La consecuencia es que el envío debe impedirse cuando la clave no está verificada, y la UI debe mostrar ambos conceptos por separado.
