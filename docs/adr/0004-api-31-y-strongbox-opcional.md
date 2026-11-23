# Compatibilidad mínima API 31 y StrongBox opcional

Status: accepted

A partir del ticket 03 orientamos el frente Android a Android 12 (API 31) o superior y actualizamos `minSdk` de 29 a 31: ECDH gestionado por Android Keystore necesita la SPI y el propósito `PURPOSE_AGREE_KEY` introducidos en API 31, y los dos teléfonos disponibles usan API 36 y 37. La consigna universitaria no exige API 29, por lo que la ausencia de pruebas en esa versión se registra como limitación de compatibilidad deliberada, no como prueba fallida. StrongBox pasa a ser una mejora opcional: se intenta cuando el dispositivo la anuncia y la operación la admite; si no está disponible se usa Android Keystore convencional con TEE, registrando el proveedor real sin degradar a claves sin protección. Estos ajustes complementan el contrato v0.2 en compatibilidad y gestión de claves; no modifican algoritmos, perfiles ni el formato del envelope (D-019).

## Considered Options

- Mantener `minSdk = 29`: exigía una estrategia adicional de gestión de claves para ECDH sin una necesidad académica demostrada.
- Elevar `minSdk = 31`: cubre los dos dispositivos reales y expone la API de Keystore que implementa ECDH. Elegida.

## Consequences

- API 29 y API 30 quedan fuera de la cobertura declarada y se documentan como `NOT TESTED` por decisión, no por fallo.
- La implementación debe detectar StrongBox en tiempo de ejecución y registrar qué nivel de seguridad se usó realmente.
