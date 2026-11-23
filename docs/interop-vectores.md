# Vectores de interoperabilidad JVM ↔ Android

Estado: esquema pendiente de valores. Los valores los entrega Persona 2 con su implementación de `:crypto`; la IA no los inventa ni los deduce de la evidencia del spike.

La verificación de interoperabilidad se ejecuta solo con vectores entregados por Persona 2. Un cifrado correcto en Android no demuestra por sí solo la interoperabilidad: hace falta que el mismo mensaje circule en ambos sentidos con los parámetros OAEP acordados en D-022.

## Casos obligatorios

| Caso | Perfil | Sentido | Texto |
|---|---|---|---|
| Cifrar en JVM, descifrar en Android | RSA-2048 | `:crypto` → Android | ASCII |
| Cifrar en JVM, descifrar en Android | RSA-3072 | `:crypto` → Android | ASCII |
| Cifrar en Android, descifrar en JVM | RSA-2048 | Android → `:crypto` | ASCII |
| Cifrar en Android, descifrar en JVM | RSA-3072 | Android → `:crypto` | ASCII |
| Texto vacío | RSA-2048 | ambos sentidos | cadena vacía |
| Unicode | RSA-2048 y RSA-3072 | ambos sentidos | caracteres no ASCII en UTF-8 |
| Clave recuperada de Keystore | RSA-2048 y RSA-3072 | descifrado en Android | ASCII |

Cada caso se ejecuta en los dos dispositivos propios, Samsung `R5CX80AMTKN` (API 37) y POCO `4eeb389e` (API 36), con StrongBox opcional según lo anunciado por cada uno. Los resultados se registran como `PASS`, `FAIL` o `NOT TESTED`, nunca como suposición.

## Formato del vector

Cada vector entregado por Persona 2 incluye:

```text
perfil: RSA_2048 | RSA_3072
tamano_clave_bits: 2048 | 3072
digest_mensaje: SHA-256
funcion_mascara: MGF1
digest_mgf1: SHA-1
psource: PSpecified.DEFAULT
texto_claro_hex: <hexadecimal del plaintext en UTF-8>
cifrado_hex: <hexadecimal del ciphertext>
clave_publica_prueba: <clave pública del par de prueba, sin material privado>
```

La clave pública de prueba se usa solo para el cifrado en JVM. La privada permanece en Android Keystore y no se entrega, imprime ni escribe en el vector. El `cifrado_hex` se produce con los parámetros anteriores; no se reutilizan vectores generados con MGF1-SHA256 porque no son descifrables con la configuración acordada (ADR-0005).

## Criterios de aceptación

- El descifrado en Android del vector JVM devuelve exactamente `texto_claro_hex`.
- El descifrado en JVM del cifrado producido en Android devuelve exactamente el mismo `texto_claro_hex`.
- La operación sigue funcionando tras `keyStore.getKey(alias, null)`.
- El proveedor y el nivel de seguridad se registran solo cuando la operación termina correctamente.
- No aparece material privado en logs, archivos ni reportes.

## Dependencias

El ticket 08 registra esta dependencia en `.partner/MISSIONS.md`. Hasta que Persona 2 entregue `:crypto` y los vectores, el trabajo se limita a preparar el arnés y la documentación; no se ejecuta la verificación ni se inventan resultados.
