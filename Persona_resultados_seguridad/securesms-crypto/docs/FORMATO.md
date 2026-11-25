# Formato binario SecureSMS v1

Todo mensaje cifrado es un arreglo de bytes con esta estructura. Las longitudes están fijadas por el perfil,
así que **no hay campos de longitud** que un atacante pueda manipular: el ciphertext es "el resto".

| Offset | Tamaño | Campo | Valor |
|---:|---:|---|---|
| 0 | 2 | magic | `0x53 0x53` ("SS") |
| 2 | 1 | versión | `0x01` |
| 3 | 1 | perfil | `0x01` RSA-2048 · `0x02` RSA-3072 · `0x11` P-256 · `0x12` P-384 |
| 4 | 4 | key id del destinatario | primeros 4 bytes de la huella SHA-256 de su clave pública |
| 8 | K | bloque de clave | RSA: salida RSA-OAEP (K = 256 / 384). ECC: punto efímero comprimido SEC1 (K = 33 / 49) |
| 8+K | n+16 | ciphertext + tag | AES-256-GCM del mensaje de n bytes, tag de 16 bytes al final |

Tamaño total = `8 + K + n + 16`. Overhead fijo: RSA-2048 280 B, RSA-3072 408 B, P-256 57 B, P-384 73 B.

## Derivación de claves

```
RSA :  seed (32 B aleatorios) ──RSA-OAEP(SHA-256, MGF1-SHA-1)──▶ bloque de clave
       HKDF-SHA-256(ikm = seed, salt = vacío, info = "SecureSMS-v1" ‖ header[0..8)) → 44 B
ECC :  (e, E) par efímero nuevo; Z = ECDH(e, Q_destinatario) (coordenada x)
       HKDF(ikm = Z, salt = E comprimido, info = "SecureSMS-v1" ‖ header ‖ Q comprimido) → 44 B
       (HKDF-SHA-256 para P-256, HKDF-SHA-384 para P-384)
44 B = clave AES-256 (32 B) ‖ nonce GCM (12 B)
AES-256-GCM(clave, nonce, AAD = header ‖ bloque de clave, mensaje)
```

Como cada mensaje usa una clave AES nueva, derivar el nonce de la misma salida HKDF es seguro y ahorra
12 bytes por SMS respecto de enviar un nonce aleatorio.

## Validación al descifrar (en este orden)

1. Entrada vacía o magic distinto → `MALFORMED_ENVELOPE`
2. Menos de 8 bytes → `TRUNCATED_ENVELOPE`
3. Versión ≠ 1 → `UNSUPPORTED_VERSION`
4. Perfil desconocido → `UNKNOWN_PROFILE`
5. Menos de `8 + K + 16` bytes → `TRUNCATED_ENVELOPE`; cuerpo > 64 KiB → `MALFORMED_ENVELOPE`
6. La clave del receptor no es del perfil del header → `KEY_PROFILE_MISMATCH`
7. Key id distinto al de la clave del receptor → `WRONG_RECIPIENT`
8. Punto efímero inválido o fuera de la curva (ECC) → `INVALID_EPHEMERAL_KEY`
9. Falla RSA-OAEP o falla el tag GCM → `AUTHENTICATION_FAILED` (mismo código para ambos: sin oráculo de padding)

Cualquier cambio en un solo bit del header, del bloque de clave o del ciphertext produce un error
(probado bit a bit en `NegativeTest`). Si un SMS se pierde, el envelope queda truncado y se rechaza.

## Huella (fingerprint) de una clave pública

`SHA-256("SSPK1" ‖ idPerfil ‖ claveCanónica)`, con claveCanónica = módulo RSA (k bytes) ‖ exponente, o el punto
EC sin comprimir `04 ‖ X ‖ Y`. Es independiente del proveedor, así que da lo mismo en PC y en Android.
Se muestra como 64 dígitos hex en grupos de 4 (`hex`) o los primeros 16 (`short`).

## Formatos de clave pública

* **X.509 SubjectPublicKeyInfo (DER/PEM)**: estándar, interoperable con OpenSSL.
* **Compacto**: `SSPK1:<PERFIL>:<base64url(DER) sin padding>`, una línea para QR o copiar/pegar.
  La importación verifica que la clave realmente sea del perfil indicado.
