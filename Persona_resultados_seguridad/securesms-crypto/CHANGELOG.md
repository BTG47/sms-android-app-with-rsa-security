# Cambios

## 1.0.1 (23 nov 2026)
- Se confirma la configuración RSA-OAEP acordada con Android (contrato v0.3): OAEP SHA-256 + MGF1 SHA-1, AES-256-GCM.
  El código ya la usaba; no cambia el formato del envelope, ni los perfiles, ni la API pública.
- Nueva prueba `OaepParametersTest` (4 casos): interoperabilidad en ambos sentidos con un Cipher independiente
  y rechazo de MGF1-SHA-256. Total: 80 pruebas.
- Documentación actualizada (README, PRUEBAS, INTEGRACION_BRUNO, sección del reporte).

## 1.0.0 (19 nov 2026)
- Primera versión: RSA-2048/3072 híbrido, ECC P-256/P-384 tipo ECIES, formato v1, 76 pruebas, vectores y benchmarks.
