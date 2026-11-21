# Issue tracker: GitHub

Los issues y specs de este repo viven en GitHub Issues de `BTG47/sms-android-app-with-rsa-security`. Todas las operaciones se hacen con la CLI `gh`.

## Convenciones

- **Crear issue**: `gh issue create --title "..." --body "..."`. Usar heredoc para bodies multilínea.
- **Leer issue**: `gh issue view <numero> --json number,title,body,labels,comments`.
- **Listar issues**: `gh issue list --state open --json number,title,body,labels,comments`, con filtros `--label` y `--state`.
- **Sub-issues**: `gh issue create --parent <padre> ...` o `gh issue edit <padre> --add-sub-issue <hijo>` (gh 2.94+). Sin sub-issues, agregar `Part of #<padre>` arriba del cuerpo.
- **Bloqueo**: dependencias nativas de GitHub, `gh issue create --blocked-by <n>,<n> ...` o `gh issue edit <hijo> --add-blocked-by <bloqueante>`. Un ticket se desbloquea cuando se cierran todos sus bloqueantes.
- **Contar bloqueantes abiertos**: `gh api repos/BTG47/sms-android-app-with-rsa-security/issues/<n> --jq .issue_dependencies_summary.blocked_by`.
- **Comentar**: `gh issue comment <numero> --body "..."`.
- **Etiquetas**: `gh issue edit <numero> --add-label "..." / --remove-label "..."`.
- **Cerrar**: publicar la explicación con `gh issue comment`, luego `gh issue close <numero>`.

`gh` infiere el repo desde `git remote -v`. Antes del primer `gh issue create` de una sesión, verificar el destino con `gh repo view --json nameWithOwner`.

## Pull requests como superficie de solicitudes

**PRs as a request surface: no.**

## Cuándo un skill dice "publicar en el issue tracker"

Crear un GitHub issue.

## Cuándo dice "traer el ticket referido"

Leerlo como en **Leer issue**.

## Operaciones de wayfinding

Usado por `/wayfinder`. El mapa es un issue con tickets como child issues.

- **Mapa**: issue etiquetado `wayfinder:map`, con el cuerpo Notes / Decisions-so-far / Fog.
- **Ticket hijo**: sub-issue del mapa, con etiquetas `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`).
- **Bloqueo**: ver Convenciones.
- **Frontier**: listar hijos abiertos, descartar los que tengan bloqueantes abiertos o assignee; gana el primero en orden del mapa.
- **Claim**: `gh issue edit <n> --add-assignee @me`.
- **Resolve**: comentar la respuesta, cerrar el issue y agregar un puntero a Decisions-so-far.

## Ramas de trabajo

Cuando se trabaje un ticket, crear la rama con la nomenclatura `bruno/feature/<nombre_de_rama>`, en minúsculas y con nombre descriptivo. No crear ramas sin un ticket asignado. La creación de rama está autorizada por el usuario; commits y push requieren autorización separada.
