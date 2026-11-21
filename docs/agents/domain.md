# Domain Docs

Cómo consumir la documentación de dominio al explorar el codebase.

## Antes de explorar, leer

- **`GLOSSARY.md`** en la raíz, o **`GLOSSARY-MAP.md`** si existe (apunta a un `GLOSSARY.md` por contexto).
- **`docs/adr/`**: leer los ADRs que toquen el área a trabajar.

Si alguno de estos archivos no existe, continuar sin avisar. El skill de modelado de dominio los crea cuando una decisión o término se resuelve de verdad.

## Estructura

Repo single-context:

```text
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-verificacion-no-es-importacion.md
│   └── 0002-fragmentos-incompletos-expiran.md
└── app/
```

## Usar el vocabulario del glosario

Al nombrar conceptos de dominio en issues, propuestas o nombres de prueba, usar el término definido en `GLOSSARY.md` y evitar los sinónimos marcados en `_Avoid_`. Si el concepto no existe todavía, es señal de preguntar o de proponer su incorporación.

## Señalar conflictos con ADRs

Si una salida contradice un ADR existente, decirlo explícitamente en lugar de ignorarlo: "_Contradice ADR-0002 (...), pero vale la pena reabrirlo porque ..._".
