# reservas-service

Corazón del sistema de reservas del Complejo Deportivo Los Alcanfores. Paquete `pe.alcanfores.reservas`, puerto **8093**,
BD **`alcanfores_reservas`** (Flyway `V1`…`V4`). Contrato: `docs/SPEC.md` §5.3 + `docs/SPEC_V2.md` (C, D, E) y
`docs/SPEC_V2_CAMBIOS.md`.

## Responsabilidad
Clientes, turnos (generados solos según el horario de CADA cancha y el calendario de feriados / días no laborables;
agenda y bloqueos, también por rango), disponibilidad y calendario, registro de reservas de 1 a 8 turnos (web y manual),
«Mis reservas» del cliente con cuenta (por DNI),
consulta y cancelación por el cliente, cancelación y asistencia desde el panel, expiración de reservas sin pago (saga),
consulta RENIEC/SUNAT (Factiliza) con caché, constancia PDF, dashboard, clientes con métricas y reportes (JSON y Excel).

## Tablas
| Tabla | Notas |
|---|---|
| `cliente` | `dni CHAR(8) UNIQUE`; nombres en Título, celular de 9 dígitos, correo en minúsculas; `verificado_reniec` si el DNI se consultó a RENIEC. |
| `turno` | `UNIQUE(id_cancha, fecha, hora_inicio)`; copia nombre/deporte de la cancha; `franja` DIA/NOCHE (empieza a la hora de noche de la cancha o después → NOCHE); `precio` = precio FINAL (incluye el recargo de feriado); **v4**: `feriado`, `recargo`, `tipo_bloqueo` (MANUAL, FERIADO, NO_LABORABLE, CANCHA_INACTIVA, FUERA_DE_HORARIO); `estado` DISPONIBLE/RESERVADO/BLOQUEADO + `motivo_bloqueo`. El cierre a medianoche se guarda 23:59:59 (sale `"23:59"`). |
| `reserva` | Código `ALC-XXXXXX`, estados (`estado_pago` incluye **REEMBOLSADO** desde V2), montos (total = suma de sus turnos), `vence_en`, `registrado_por`, `nota`, `es_semilla`, `version` (bloqueo optimista). `id_turno` = PRIMER turno (lo usan filtros, reportes y dashboard). `turno_activo` (columna generada STORED + UNIQUE) sigue protegiendo el primer turno. |
| `reserva_turno` | **v4** · turnos de cada reserva: `PK(id_reserva, id_turno)`, `precio` cobrado por ese turno, `vigente` (el código lo pone en 0 al cancelar o expirar) y **`turno_activo` = `CASE WHEN vigente = 1 THEN id_turno END` STORED UNIQUE**: un turno solo puede estar en UNA reserva vigente. |
| `cancelacion` | Una por reserva: quién (CLIENTE/ADMINISTRADOR/SISTEMA), motivo, devolución y monto. |
| `reserva_historial` | **Ampliación**: hitos de la línea de tiempo (REGISTRADA, COMPROBANTE_RECIBIDO, OBSERVADO, PAGO_VALIDADO, CONFIRMADA, ASISTENCIA, CANCELADA). |
| `consulta_documento` | Caché de RENIEC/SUNAT (`PK(tipo, numero)`, respuesta JSON): 30 días si se encontró; 1 día el «no encontrado» (`fuente = NO_ENCONTRADO`, respuesta `{}`). |
| `evento_procesado` | Idempotencia de los consumidores. |

## No doble reserva (RNF-11) — dos candados
1. **Bloqueo pesimista**: `SELECT … FOR UPDATE` de TODOS los turnos pedidos, siempre **ordenados por id**
   (`TurnoRepositorio.bloquearPorIds`): dos reservas que comparten un turno los piden en el mismo orden, no se
   interbloquean; la segunda espera, ve el turno RESERVADO y recibe **409 `TURNO_OCUPADO`** (toda la reserva se rechaza).
2. **Base de datos**: aunque alguien se salte el bloqueo, el índice UNIQUE de `reserva_turno.turno_activo` rechaza que un
   turno esté en dos reservas vigentes (también se traduce a 409). Una reserva CANCELADA no ocupa sus turnos.
Pruebas: `DobleReservaIntegracionTest` (2 y 5 hilos sobre un turno; dos reservas de 2 turnos que comparten uno, pedidos en
distinto orden: gana una; inserción directa en `reserva_turno` rechazada por la columna única).

## Endpoints
Públicos: `GET /api/disponibilidad?fecha&idCancha`, `GET /api/disponibilidad/calendario?desde&dias&idCancha`,
`POST /api/reservas`, `GET /api/reservas/{codigo}?dni`, `POST /api/reservas/{codigo}/cancelar`,
`GET /api/reservas/{codigo}/constancia.pdf?dni`, `GET /api/documentos/dni/{dni}`, `GET /api/documentos/ruc/{ruc}`.
`GET /api/disponibilidad` agrega `feriado` y `recargo` en cada turno (el `precio` ya incluye el recargo) y genera al vuelo una
fecha visible que aún no tenga turnos.

Reservas de varios turnos: `POST /api/reservas` y `POST /api/admin/reservas` aceptan `idsTurno:[..]` (1 a 8, misma cancha y
fecha; pueden no ser seguidos) o `idTurno` (uno, como en la v1). Errores 400 en `idsTurno`: «Elige entre 1 y 8 turnos.»,
«No repitas el mismo turno.», «Los turnos deben ser de la misma cancha y del mismo día.», «Ese turno ya no está disponible.»
(si se usó `idTurno`, el campo es `idTurno`). Todas las respuestas de reserva (registro, consulta, panel, listado del panel,
«Mis reservas», interno) traen `turnos:[{idTurno, horaInicio, horaFin, precio}]`, `horarioTexto` («7:00–9:00 p. m.» o
«9:00–10:00 a. m. y 7:00–8:00 p. m.»), `cantidadTurnos`; `horaInicio` = primer turno y `horaFin` = último. La consulta del
cliente agrega `resenada`.

Cliente con cuenta (token **CLIENTE**, claim `dni`): `GET /api/cliente/reservas?estado&page&size` (size 1–50, por defecto 10;
fecha y hora más recientes primero; `estado` PENDIENTE | CONFIRMADA | CANCELADA, otro → 400) → página de
`{codigoReserva, estado, estadoPago, asistencia, origen, cancha:{id, nombre, deporte}, fecha, horaInicio, horaFin, horarioTexto,
cantidadTurnos, turnos, montoTotal, montoAdelanto, montoPagado, saldo, venceEn, fechaRegistro, puedeCancelar,
puedeReenviarComprobante, puedeResenar, resenada}` con TODAS las reservas de su DNI (también las hechas antes de crear la
cuenta). `GET /api/cliente/reservas/{codigo}` → el detalle de «Mi reserva» + `resenada` (404 si no es de su DNI). Sin token
401; token de administrador 403. Con token CLIENTE, `POST /api/reservas` reemplaza `cliente.dni` por el DNI del token.

Panel (token; ADMIN o RECEPCION salvo lo marcado):
`GET /api/admin/dashboard?fecha` · `GET /api/admin/reservas` (filtros `estado, estadoPago, fecha, desde, hasta, idCancha,
origen, q`; `sort` por `fecha, fechaRegistro, cancha, cliente, montoTotal, estado, estadoPago, codigoReserva`; incluye
`conteos`) · `GET /api/admin/reservas/{codigo}` · `POST /api/admin/reservas` (manual) · `POST /api/admin/reservas/{codigo}/cancelar`
· `POST /api/admin/reservas/{codigo}/asistencia` · `GET /api/admin/reservas/{codigo}/constancia.pdf` · `GET /api/admin/turnos?fecha&idCancha`
· `POST /api/admin/turnos/generar` (ADMIN, solo soporte: los turnos se generan solos) · `POST /api/admin/turnos/{id}/bloquear`
· `POST /api/admin/turnos/{id}/desbloquear` (422 `BLOQUEO_DE_CALENDARIO` si lo cerró un feriado o día no laborable)
· `POST /api/admin/turnos/bloquear-rango` `{idsCancha, fecha, fechaHasta?, horaDesde, horaHasta, motivo}` (acepta `idCancha`
suelto; hasta 31 días; `"24:00"` = fin del día) → `{bloqueados, omitidosConReserva:[códigos]}` · `POST /api/admin/turnos/desbloquear-rango`
`{idsCancha, fecha, fechaHasta?, horaDesde, horaHasta}` → `{desbloqueados}` (solo bloqueos MANUALES) · `GET /api/admin/clientes?q`
· `GET /api/admin/clientes/{dni}`. La agenda (`GET /api/admin/turnos`) agrega `feriado`, `recargo` y `tipoBloqueo` por turno y
muestra una reserva de varios turnos en cada una de sus celdas (también en el dashboard). Un token CLIENTE en `/api/admin/**` → 403.
· `GET /api/admin/reportes/reservas?desde&hasta` (ADMIN) · `GET /api/admin/reportes/reservas.xlsx` (ADMIN).

Internos (`X-Interno-Token`): `GET /interno/reservas/{codigo}` (campos de DatosReserva **planos** + estado, estadoPago,
asistencia, origen, metodoPagoPrevisto, saldo, inicio, venceEn, **horarioTexto, cantidadTurnos, turnos**),
`GET /interno/reservas/vigentes?fecha[&idsCancha=1,2]` → `{codigos:[…]}` (reservas PENDIENTE/CONFIRMADA con algún turno ese
día en esas canchas; catálogo lo usa para el 409 `DIA_CON_RESERVAS`), `GET /interno/reservas/{codigo}/elegible-resena?dni&idCancha`
→ `{elegible, motivo, autor}` (siempre 200), `GET /interno/reservas/{codigo}/constancia.pdf`,
`GET /interno/reservas/proximas?desde&hasta` (ISO fecha-hora; por defecto las próximas 24 h), `GET /interno/reservas/para-semilla`
(incluye `montoDevolucion` de las canceladas con devolución).

Swagger: `/swagger-ui.html`. Salud: `/actuator/health`.

## Reglas principales
- **Registro web** (una transacción): bloquea los turnos (en orden de id), valida fecha (hoy … hoy + `diasVisibles`), cada
  turno empieza ≥ ahora + 30 min, no está bloqueado ni ocupado; upsert del cliente por DNI (ver «Cliente por DNI»);
  `montoTotal` = suma de los precios (con recargo de feriado), `montoAdelanto = total × adelantoPct / 100` (HALF_UP);
  `venceEn = min(ahora + minutosParaPagar, inicio del primer turno)`; publica `reserva.registrada` tras el commit.
- **Cancelar / expirar** liberan TODOS los turnos de la reserva (`vigente = 0` y cada turno vuelve a DISPONIBLE con el precio y
  el calendario vigentes; si ya no calza con el horario de la cancha queda oculto).
- **Turnos automáticos** (sin botón): el arranque y la tarea de las 00:05 regeneran hoy + `dias_generados` (clave de catálogo,
  120 por defecto, nunca menos que `diasVisibles`) con el horario de CADA cancha (duración del turno, días abiertos, apertura
  y cierre, inicio de la noche). Regenerar = crear los que faltan, borrar los libres que ya no calzan, cambiar `hora_fin`
  y repreciar los que siguen; se **conservan** los turnos con reserva vigente, los bloqueos MANUALES y los que ya empezaron,
  y no se crea un turno nuevo que se cruce con uno conservado. Un turno libre que no calza pero que referencia una reserva
  cancelada no se puede borrar: queda `FUERA_DE_HORARIO` (oculto en disponibilidad, agenda, calendario y dashboard).
  Mientras catálogo no haya respondido canchas y calendario (copia semilla), solo se crean turnos: nada se cambia ni se borra.
- **Calendario**: FERIADO → cancha `feriadoModo = CERRADA`: turnos BLOQUEADOS `tipo_bloqueo = FERIADO` con motivo «Feriado:
  {nombre}»; ABIERTA: `precio + recargoFeriado`, `feriado = true`. NO_LABORABLE → BLOQUEADOS `NO_LABORABLE` con el nombre
  como motivo en las canchas indicadas (o todas). Al quitar el día se liberan (sin tocar reservas ni bloqueos manuales).
- **Reserva manual**: PRESENCIAL/TELEFONICA, sin `venceEn`, admite turnos que aún no terminan. Con `pagoInicial` valida
  Yape/Plin con número de operación, factura con RUC 10/20 + razón social 3–150 + dirección 5–200, TOTAL = monto exacto y
  monto ≤ total; luego (fuera de la transacción) llama a pagos `POST /interno/pagos/caja`. Si pagos falla, la reserva queda
  PENDIENTE sin plazo y la respuesta trae `pagoRegistrado:false` y un `aviso`.
- **Cancelación cliente**: solo PENDIENTE/CONFIRMADA y antes del inicio; `devolucion` si hubo pago y faltan ≥ `devolucionHoras`.
  **Panel**: cualquier reserva vigente sin asistencia; `devolucion` explícita o, si viene vacía, la misma regla.
  **Con devolución** la reserva queda `estadoPago = REEMBOLSADO`, `montoPagado` neto (lo cobrado − lo devuelto, 0 si se
  devolvió todo) y `saldo = 0`; el evento `reserva.cancelada` sigue llevando en `reserva.montoPagado` lo cobrado antes de
  devolver (pagos reembolsa y emite la nota de crédito con eso). Toda reserva CANCELADA tiene `saldo = 0`.
- **Cliente por DNI** (regla del upsert): celular y correo se actualizan siempre. Nombres y apellidos: un cliente nuevo
  toma lo escrito; uno existente los conserva, **salvo en la reserva manual del panel cuando no está verificado por RENIEC**:
  ahí se guardan los nombres que escribió recepción. Si el cliente está verificado (su DNI se consultó con éxito a RENIEC),
  mandan los datos de RENIEC. En la web no se cambian los nombres de un cliente existente (cualquiera podría escribir un DNI ajeno).
- **Línea de tiempo**: el motivo u observación se agrega sin doble punto (`ReglasReserva.frase`) y los plazos van en 12 h
  («Reenvíalo antes del 19/11/2026 a las 10:40 a. m.»), igual que el hito REGISTRADA y el portal.
- **Asistencia**: solo CONFIRMADA y desde 15 min antes del inicio; publica `reserva.asistencia`.
- **Consulta del cliente**: el mismo 404 si el código no existe o el DNI no coincide. Banderas `puedeCancelar`,
  `puedeReenviarComprobante`, `puedeResenar`. `puedeResenar` es falso si catálogo ya tiene la reseña de esa reserva
  (`GET /interno/resenas/{codigo}` en catalogo-service, solo cuando la reserva ya permite reseñar; si catálogo no
  responde se deja en verdadero y catálogo responde 409 al enviar).

## Eventos
Publica (después del commit con `@TransactionalEventListener(AFTER_COMMIT)`): `reserva.registrada`, `reserva.confirmada`,
`reserva.cancelada`, `reserva.asistencia`; además `auditoria.accion` en cada acción del panel.
Todos los eventos llevan en `DatosReserva` el `horarioTexto` y la `cantidadTurnos` (horaInicio = primer turno, horaFin =
último). Consume (idempotentes por `evento_procesado`), colas `reservas.pago-registrado`, `reservas.pago-validado`,
`reservas.pago-rechazado`, `reservas.cancha-actualizada`, **`reservas.calendario-actualizado`**:
- `pago.registrado` → EN_REVISION y sin plazo.
- `pago.validado` → suma el monto; ADELANTO→ADELANTO_PAGADO, TOTAL→PAGADO_TOTAL, SALDO→PAGADO_TOTAL si completa;
  ADELANTO/TOTAL confirman (CONFIRMADA, `confirmadaEn`) y publican `reserva.confirmada` con el saldo.
- `pago.rechazado` → OBSERVADO, `venceEn = min(ahora + 12 h, inicio − 1 h)` (con un mínimo de `minutosParaPagar`).
- `cancha.actualizada` (con horario, ver comun) → regenera los turnos futuros de la cancha (hoy + `dias_generados`) con
  sus datos y horario nuevos; INACTIVA bloquea los libres con «Cancha inactiva» (`CANCHA_INACTIVA`); ACTIVA los libera.
- `calendario.actualizado` → actualiza la copia del calendario y regenera ESA fecha en todas las canchas (aplica o quita
  el bloqueo / recargo).

## Catálogo en memoria
`ComplejoServicio` guarda configuración, canchas con su horario (`/interno/canchas`) y el calendario de hoy + `dias_generados`
(`/interno/calendario`). Se refresca al arrancar, cada 5 min (regenera las canchas y fechas que cambiaron) y con los eventos.
Si catálogo no responde, usa los valores del SPEC (adelanto 50 %, devolución 24 h, 08:00–23:00, 60 min, noche desde 18:00,
14 días visibles, 120 generados, 20 min para pagar, feriado ABIERTA con S/ 10, sin días especiales) y las 4 canchas semilla.
Los LocalTime aceptan `"24:00"` (se guarda 23:59:59); un cierre `00:00`, `23:59` o `23:59:59` cuenta como medianoche.
Clientes HTTP: `RestClient` balanceado por Eureka + `InterceptorInterno`, timeout 3 s, circuit breaker Resilience4j.

## Tareas programadas (America/Lima)
Al arrancar: refresca catálogo y calendario y regenera hoy + `dias_generados` (~3 s para 4 canchas × 120 días). Cada minuto: expira reservas PENDIENTE con
SIN_PAGO/OBSERVADO vencidas (cancelación SISTEMA «Venció el plazo para pagar» + `reserva.cancelada`, cada una en su
transacción). 00:05: regenera la ventana (aparece el día nuevo). Cada 5 min: refresca catálogo. Se apagan con `TAREAS_HABILITADAS=false`.
**Catálogo leído tarde** (arrancó después que reservas o aún no estaba en Eureka): mientras no se lean canchas y calendario,
la regeneración solo crea turnos; cada 30 s se reintenta la lectura y cualquier evento `cancha.actualizada` o
`calendario.actualizado` la fuerza antes de aplicarse (si catálogo publica, ya responde). Encontrado en la prueba E2E
del 19/11/2026: sin esto, un cambio de horario o un feriado hecho en los primeros 5 min tras el arranque no se aplicaba.
Prueba: `SincronizacionPendienteTest`.

## Documentos (RENIEC/SUNAT)
Interfaz `ProveedorDocumentos`; implementación `FactilizaProveedor` (`FACTILIZA_TOKEN`, **timeout de 15 s**: Factiliza tarda
~11 s en responder 404 cuando el DNI no existe; con 5 s se cortaba y salía 503). El gateway da 20 s a `/api/documentos/**`
(ruta `reservas-documentos`) y el front espera 25 s. Valida DNI/RUC antes de consultar; caché 30 días (`fuente: "CACHE"`);
404/400/422 de Factiliza → 404 `DNI_NO_ENCONTRADO`/`RUC_NO_ENCONTRADO` con mensaje amable, y ese «no encontrado» se guarda
**1 día** para no gastar consultas (no marca al cliente como verificado); 503 `CONSULTA_NO_DISPONIBLE` sin token o con el
proveedor caído (el front deja escribir a mano). Nombres en Título.

## Constancia PDF
Thymeleaf (`plantillas/constancia.html`) + openhtmltopdf con fuentes Barlow Semi Condensed e Inter embebidas, logo
horizontal y QR (ZXing) que abre `FRONT_URL/mi-reserva?codigo=…`. A4: código grande, cancha, fecha larga, hora am/pm,
tarifa, cliente (DNI enmascarado), montos (total, adelanto, pagado, saldo), estado con color, aviso de plazo o saldo,
cancelación, condiciones y cómo llegar. Muestra: `docs/muestras/constancia-ejemplo.pdf`.

## Reportes
JSON con totales (incluye tasa de no-show y ocupación), por cancha, por día, por hora, por origen, cancelaciones (por actor y
motivos frecuentes) y top 5 clientes. Excel (Apache POI) con hojas **Resumen, Por cancha, Por día, Reservas**, encabezados
verde Alcanfores, filtros y panel congelado. Por defecto, los últimos 30 días; máximo 366.

## Semilla
`SemillaDatos` (CommandLineRunner) solo si no hay reservas y `SEMILLA_HABILITADA=true`: 25 clientes ficticios
(DNI 9901xxxx, correos @example.com), turnos de los últimos 45 días, ~120 reservas pasadas (confirmadas con asistencia y
no-shows, canceladas por cliente/complejo/sistema) y ~20 próximas (4 por validar, 2 observadas, pendientes, canceladas y
confirmadas), con más demanda de noche y en fin de semana. `Random` con semilla fija y fechas relativas a `Tiempo.hoy()`.
Marca `es_semilla = true` (lo expone `/interno/reservas/para-semilla` para pagos-service).

## Pruebas
106 pruebas al 19/11/2026 (v2), 0 fallos. Nuevas: `TurnosPorHorarioTest` (horario de cada cancha: 90 min, día cerrado,
cierre 24:00; regeneración al cambiar el horario conservando reserva y bloqueo manual y ocultando el turno de una reserva
cancelada; feriado ABIERTA con recargo y CERRADA bloqueado; no laborable que bloquea y al borrarse libera, idempotente;
bloquear/desbloquear rango de 2 canchas × 2 días con omitidos; validaciones del rango; generación al vuelo; sincronización
con catálogo y `dias_generados`; cancha inactiva), `VariosTurnosTest` (2 turnos no seguidos: total, adelanto,
`horarioTexto`, evento; seguidos en la agenda, dashboard, listado, interno y constancia; validaciones de `idsTurno`; 409 si
uno está tomado; cancelar y expirar liberan todos; reserva manual de 2 turnos; `/interno/reservas/vigentes`; datos de la
migración V4), `MisReservasTest` (reserva hecha antes de la cuenta, solo su DNI, filtro, 400/404, `resenada`, DNI del token
al reservar, 401/403), `ComplejoServicioImplTest` (copia semilla vs. leída, calendario, reglas de horario) y 2 de
concurrencia en `DobleReservaIntegracionTest`. Cobertura JaCoCo (jacoco.csv): 94,3 % de instrucciones en total, capa de
servicio 95,8 % (ramas 79 %). Antes, 78 pruebas al 18/11/2026 (JUnit 5, Mockito, AssertJ, MockMvc, `@SpringBootTest` contra `alcanfores_reservas_test` en MariaDB; Flyway clean
+ migrate al iniciar; RabbitMQ, Eureka, catálogo y pagos simulados; reloj fijado con `Tiempo.fijar`):
doble reserva concurrente (409), validaciones por campo (400), fecha/turno fuera de regla, disponibilidad y calendario,
consulta con mismo 404, cancelación con y sin devolución, cancelación del panel, expiración, consumidores (confirmación por
`pago.validado`, idempotencia, rechazo, cambios de cancha, refresco del catálogo), 401/403/rutas internas, Factiliza con
`MockRestServiceServer`, caché de documentos, PDF (`%PDF`), reportes y Excel, panel (manual con/sin pagos, asistencia,
bloqueos, generación idempotente, dashboard, listado, clientes) y semilla. Cobertura JaCoCo: 94 % de instrucciones
(capa `servicio.impl` 94 %, ramas 74 %).

## Decisiones
- La línea de tiempo se guarda en `reserva_historial` (tabla extra) para no reconstruirla a partir de los eventos.
- `GET /interno/reservas/{codigo}` devuelve los campos de `DatosReserva` en plano (no anidados).
- La reserva manual llama a pagos **después** del commit; la confirmación llega por `pago.validado` (no se confirma en
  la respuesta síncrona), para que el flujo sea el mismo que en la web.
- `POST /interno/pagos/caja` envía `registradoPor` como **id numérico** del administrador y `registradoPorUsuario` con su usuario.
- `elegible-resena` responde siempre 200 con `{elegible, motivo, autor}`.
- Tras `pago.rechazado` el nuevo plazo nunca es menor que `minutosParaPagar` (sin pasar del inicio), para que el cliente alcance a reenviar.
- **v2 (19/11/2026)** `V4__turnos_por_horario_y_varios_turnos`: columnas `feriado/recargo/tipo_bloqueo` en `turno` (los
  bloqueos existentes pasan a CANCHA_INACTIVA o MANUAL), tabla `reserva_turno` y copia de cada reserva existente (precio =
  `monto_total`, vigente según su estado). Probada sobre una copia de la BD local (163 reservas → 163 filas, las 124
  vigentes ocupan su turno, montos y pagos intactos) y arrancando el jar nuevo contra otra copia (Flyway v4 + 120 días).
- `reserva.id_turno` se conserva como PRIMER turno: así no cambian filtros, reportes, dashboard ni la constancia; la garantía
  por turno la da `reserva_turno`. Los turnos que ya no calzan y no se pueden borrar se ocultan (`FUERA_DE_HORARIO`, valor
  extra del enum `tipo_bloqueo`; nunca aparece en las respuestas).
- Con token CLIENTE el DNI del cuerpo se reemplaza por el del token; los demás datos siguen siendo obligatorios (el portal los
  llena con la cuenta): no hace falta consultar auth-service.
- Migraciones de datos (18/11/2026): `V2__estado_pago_reembolsado` (enum + reservas ya canceladas con devolución) y
  `V3__textos_linea_tiempo` (quita el doble punto de los hitos guardados).
- El QR de la constancia lleva la URL de «Mi reserva» con el código (escaneable por el cliente y por recepción).
