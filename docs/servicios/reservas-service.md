# reservas-service

Corazón del sistema de reservas del Complejo Deportivo Los Alcanfores. Paquete `pe.alcanfores.reservas`, puerto **8093**,
BD **`alcanfores_reservas`** (Flyway `V1__esquema.sql`). Contrato: `docs/SPEC.md` §5.3.

## Responsabilidad
Clientes, turnos (generación, agenda y bloqueos), disponibilidad y calendario, registro de reservas (web y manual),
consulta y cancelación por el cliente, cancelación y asistencia desde el panel, expiración de reservas sin pago (saga),
consulta RENIEC/SUNAT (Factiliza) con caché, constancia PDF, dashboard, clientes con métricas y reportes (JSON y Excel).

## Tablas
| Tabla | Notas |
|---|---|
| `cliente` | `dni CHAR(8) UNIQUE`; nombres en Título, celular de 9 dígitos, correo en minúsculas; `verificado_reniec` si el DNI se consultó a RENIEC. |
| `turno` | `UNIQUE(id_cancha, fecha, hora_inicio)`; copia nombre/deporte de la cancha; `precio` según `franja` (DIA/NOCHE por `inicio_noche`); `estado` DISPONIBLE/RESERVADO/BLOQUEADO + `motivo_bloqueo`. |
| `reserva` | Código `ALC-XXXXXX`, estados, montos, `vence_en`, `registrado_por`, `nota`, `es_semilla`, `version` (bloqueo optimista). **`turno_activo` = columna generada STORED** (`id_turno` si PENDIENTE/CONFIRMADA, si no NULL) con **UNIQUE**: garantía de no doble reserva. |
| `cancelacion` | Una por reserva: quién (CLIENTE/ADMINISTRADOR/SISTEMA), motivo, devolución y monto. |
| `reserva_historial` | **Ampliación**: hitos de la línea de tiempo (REGISTRADA, COMPROBANTE_RECIBIDO, OBSERVADO, PAGO_VALIDADO, CONFIRMADA, ASISTENCIA, CANCELADA). |
| `consulta_documento` | Caché de 30 días de RENIEC/SUNAT (`PK(tipo, numero)`, respuesta JSON). |
| `evento_procesado` | Idempotencia de los consumidores. |

## No doble reserva (RNF-11) — dos candados
1. **Bloqueo pesimista**: `SELECT … FOR UPDATE` del turno (`TurnoRepositorio.bloquearPorId`) dentro de la transacción; la
   segunda solicitud espera, ve el turno RESERVADO y recibe **409 `TURNO_OCUPADO`**.
2. **Base de datos**: aunque alguien se salte el bloqueo, el índice UNIQUE de `turno_activo` rechaza una segunda reserva
   vigente del mismo turno (también se traduce a 409). Una reserva CANCELADA no ocupa el turno.
Prueba: `DobleReservaIntegracionTest` (2 y 5 hilos simultáneos contra MariaDB: gana uno; los demás reciben 409).

## Endpoints
Públicos: `GET /api/disponibilidad?fecha&idCancha`, `GET /api/disponibilidad/calendario?desde&dias&idCancha`,
`POST /api/reservas`, `GET /api/reservas/{codigo}?dni`, `POST /api/reservas/{codigo}/cancelar`,
`GET /api/reservas/{codigo}/constancia.pdf?dni`, `GET /api/documentos/dni/{dni}`, `GET /api/documentos/ruc/{ruc}`.

Panel (token; ADMIN o RECEPCION salvo lo marcado):
`GET /api/admin/dashboard?fecha` · `GET /api/admin/reservas` (filtros `estado, estadoPago, fecha, desde, hasta, idCancha,
origen, q`; `sort` por `fecha, fechaRegistro, cancha, cliente, montoTotal, estado, estadoPago, codigoReserva`; incluye
`conteos`) · `GET /api/admin/reservas/{codigo}` · `POST /api/admin/reservas` (manual) · `POST /api/admin/reservas/{codigo}/cancelar`
· `POST /api/admin/reservas/{codigo}/asistencia` · `GET /api/admin/reservas/{codigo}/constancia.pdf` · `GET /api/admin/turnos?fecha&idCancha`
· `POST /api/admin/turnos/generar` (ADMIN) · `POST /api/admin/turnos/{id}/bloquear` · `POST /api/admin/turnos/{id}/desbloquear`
· `POST /api/admin/turnos/bloquear-rango` · `GET /api/admin/clientes?q` · `GET /api/admin/clientes/{dni}`
· `GET /api/admin/reportes/reservas?desde&hasta` (ADMIN) · `GET /api/admin/reportes/reservas.xlsx` (ADMIN).

Internos (`X-Interno-Token`): `GET /interno/reservas/{codigo}` (campos de DatosReserva **planos** + estado, estadoPago,
asistencia, origen, metodoPagoPrevisto, saldo, inicio, venceEn), `GET /interno/reservas/{codigo}/elegible-resena?dni&idCancha`
→ `{elegible, motivo, autor}` (siempre 200), `GET /interno/reservas/{codigo}/constancia.pdf`,
`GET /interno/reservas/proximas?desde&hasta` (ISO fecha-hora; por defecto las próximas 24 h), `GET /interno/reservas/para-semilla`.

Swagger: `/swagger-ui.html`. Salud: `/actuator/health`.

## Reglas principales
- **Registro web** (una transacción): bloquea el turno, valida fecha (hoy … hoy + `diasVisibles`), inicio ≥ ahora + 30 min,
  turno no bloqueado ni ocupado; upsert del cliente por DNI (actualiza celular y correo); `montoAdelanto = total × adelantoPct / 100`
  (HALF_UP); `venceEn = min(ahora + minutosParaPagar, inicio)`; publica `reserva.registrada` tras el commit.
- **Reserva manual**: PRESENCIAL/TELEFONICA, sin `venceEn`, admite turnos que aún no terminan. Con `pagoInicial` valida
  Yape/Plin con número de operación, factura con RUC 10/20 + razón social 3–150 + dirección 5–200, TOTAL = monto exacto y
  monto ≤ total; luego (fuera de la transacción) llama a pagos `POST /interno/pagos/caja`. Si pagos falla, la reserva queda
  PENDIENTE sin plazo y la respuesta trae `pagoRegistrado:false` y un `aviso`.
- **Cancelación cliente**: solo PENDIENTE/CONFIRMADA y antes del inicio; `devolucion` si hubo pago y faltan ≥ `devolucionHoras`.
  **Panel**: cualquier reserva vigente sin asistencia; `devolucion` explícita o, si viene vacía, la misma regla.
- **Asistencia**: solo CONFIRMADA y desde 15 min antes del inicio; publica `reserva.asistencia`.
- **Consulta del cliente**: el mismo 404 si el código no existe o el DNI no coincide. Banderas `puedeCancelar`,
  `puedeReenviarComprobante`, `puedeResenar`.

## Eventos
Publica (después del commit con `@TransactionalEventListener(AFTER_COMMIT)`): `reserva.registrada`, `reserva.confirmada`,
`reserva.cancelada`, `reserva.asistencia`; además `auditoria.accion` en cada acción del panel.
Consume (idempotentes por `evento_procesado`), colas `reservas.pago-registrado`, `reservas.pago-validado`,
`reservas.pago-rechazado`, `reservas.cancha-actualizada`:
- `pago.registrado` → EN_REVISION y sin plazo.
- `pago.validado` → suma el monto; ADELANTO→ADELANTO_PAGADO, TOTAL→PAGADO_TOTAL, SALDO→PAGADO_TOTAL si completa;
  ADELANTO/TOTAL confirman (CONFIRMADA, `confirmadaEn`) y publican `reserva.confirmada` con el saldo.
- `pago.rechazado` → OBSERVADO, `venceEn = min(ahora + 12 h, inicio − 1 h)` (con un mínimo de `minutosParaPagar`).
- `cancha.actualizada` → repone nombre y precio de los turnos futuros DISPONIBLES; INACTIVA bloquea los libres con
  «Cancha inactiva»; ACTIVA los libera; genera los turnos que falten.

## Catálogo en memoria
`ComplejoServicio` guarda configuración y canchas de catalogo-service (`/interno/configuracion`, `/interno/canchas`).
Se refresca al arrancar, cada 5 min y con `cancha.actualizada`. Si catálogo no responde, usa los valores del SPEC
(adelanto 50 %, devolución 24 h, 08:00–23:00, 60 min, noche desde 18:00, 14 días, 20 min para pagar) y las 4 canchas semilla.
Clientes HTTP: `RestClient` balanceado por Eureka + `InterceptorInterno`, timeout 3 s, circuit breaker Resilience4j.

## Tareas programadas (America/Lima)
Al arrancar: refresca catálogo y genera turnos de hoy + `diasVisibles`. Cada minuto: expira reservas PENDIENTE con
SIN_PAGO/OBSERVADO vencidas (cancelación SISTEMA «Venció el plazo para pagar» + `reserva.cancelada`, cada una en su
transacción). 00:05: genera turnos del nuevo día. Cada 5 min: refresca catálogo. Se apagan con `TAREAS_HABILITADAS=false`.

## Documentos (RENIEC/SUNAT)
Interfaz `ProveedorDocumentos`; implementación `FactilizaProveedor` (`FACTILIZA_TOKEN`, timeout 5 s). Valida DNI/RUC antes
de consultar; caché 30 días (`fuente: "CACHE"`); 404 `DNI_NO_ENCONTRADO`/`RUC_NO_ENCONTRADO`; 503 `CONSULTA_NO_DISPONIBLE`
sin token o con el proveedor caído (el front deja escribir a mano). Nombres en Título.

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
61 pruebas (JUnit 5, Mockito, AssertJ, MockMvc, `@SpringBootTest` contra `alcanfores_reservas_test` en MariaDB; Flyway clean
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
- El QR de la constancia lleva la URL de «Mi reserva» con el código (escaneable por el cliente y por recepción).
