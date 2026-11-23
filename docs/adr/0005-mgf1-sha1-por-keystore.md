# MGF1-SHA1 para RSA-OAEP en Android

Status: accepted

El spike del ticket 03 demostró que el POCO (API 36, `hardware_keystore=100`) rechaza MGF1-SHA256 con la clave privada en Android Keystore: `-79 UNSUPPORTED_MGF_DIGEST` cuando la clave declara el tag y `-1000 UNKNOWN_ERROR` en `finish` cuando no lo declara. El Samsung (API 37, `hardware_keystore=300`, StrongBox disponible) admite MGF1-SHA256 tanto en TEE como en StrongBox; ambos dispositivos admiten MGF1-SHA1. Bruno y Persona 2 acordaron el 2026-11-23 fijar MGF1-SHA1 para RSA-OAEP en todos los frentes, manteniendo SHA-256 como digest del mensaje y AES-256-GCM para el contenido. Persona 2 modifica `:crypto`; el frente Android adapta la generación de claves y el uso del `Cipher`. ECC, perfiles, interfaces y envelope no cambian (D-022).

## Considered Options

- Mantener MGF1-SHA256 y desplegar solo en Samsung: criptografía intacta, pero deja fuera al POCO y contradice la cobertura con dos teléfonos propios acordada en D-009.
- Negociar el digest de MGF1 por dispositivo: conserva SHA-256 donde el hardware lo admite, pero añade estado al protocolo y toca el envelope o el intercambio, prohibido por D-019 sin acuerdo.
- Clave privada fuera de Android Keystore en el POCO: permite MGF1-SHA256, pero degrada la protección de la privada y el contrato prohíbe exportarla.
- MGF1-SHA1 en Android y en `:crypto`: funciona en ambos dispositivos, conserva la privada en Keystore y no altera el formato del envelope. Elegida.

## Consequences

- En OAEP, MGF1 usa el digest para expandir una semilla en la máscara; la propiedad relevante es la unidireccionalidad y seudorrandomidad, no la resistencia a colisiones. SHA-1 conserva unos 160 bits de resistencia a preimagen, suficientes para RSA-2048 y RSA-3072, y el digest del mensaje sigue siendo SHA-256. No hay ataque práctico conocido para esta combinación, que además fue el valor por defecto de PKCS#1 v2.1. Aun así, la fuerza declarada del esquema queda limitada por el eslabón más débil y algunos criterios de cumplimiento pueden rechazar SHA-1 incluso en MGF1; la decisión queda registrada para que ese coste sea visible.
- Un envelope generado con los parámetros v0.2 no será descifrable con los nuevos y el formato del envelope no incluye los parámetros OAEP que permitirían detectarlo. No se mezclan mensajes de ambas configuraciones.
- `KeyGenParameterSpec.Builder.setMgf1Digests(DIGEST_SHA1)` solo existe desde API 35. En API 31–34 el tag no se declara y se depende del valor por defecto de Keystore, que es MGF1-SHA1; la implementación debe hacer explícita esa condición con una comprobación de `Build.VERSION.SDK_INT`.
- StrongBox sigue siendo opcional: se intenta si el dispositivo lo anuncia y se registra el nivel real. Android Keystore con TEE sigue siendo el mecanismo principal y no se degrada a claves sin protección.
- `docs/contract.md` queda pendiente de actualización por decisión de Bruno (D-023): el PDF v0.2 sigue siendo el artefacto canónico y no se emitirá uno nuevo para este cambio.
