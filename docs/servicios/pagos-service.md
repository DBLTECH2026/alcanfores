# pagos-service

Microservicio de **pagos y comprobantes** del sistema de reservas Alcanfores. Puerto **8094**, base de datos
`alcanfores_pagos`, paquete `pe.alcanfores.pagos`. Contrato: `docs/SPEC.md` §5.4.

## Responsabilidad
- Cobros de cada reserva por tres canales: **comprobante Yape/Plin** (el cliente sube la captura y recepción la valida),
  **pago en línea** (Pasarela Alcanfores Pay beta o Mercado Pago sandbox) y **caja** del complejo.
- **Pasarela Alcanfores Pay (beta)**: un proveedor de pagos simulado dentro del servicio (tarjetas de prueba, Luhn, 3-D Secure,
  Yape beta) que avisa al comercio por **webhook firmado con HMAC-SHA256** e idempotente.
- **Mercado Pago (sandbox)**: preferencias de Checkout Pro por REST, webhook con `x-signature` y consulta por referencia.
- **Comprobantes electrónicos SIMULADOS**: boleta (B001), factura (F001) y notas de crédito (BC01/FC01) con numeración sin
  huecos, IGV 18 % incluido, importe en letras, hash SHA-256, QR con formato SUNAT, **PDF A4** (representación impresa) y
  **XML UBL 2.1**. Sin firma digital ni envío a SUNAT: sin validez tributaria (lo dicen el PDF y el XML).
- Compensación de la saga: al cancelarse una reserva anula lo pendiente y, si corresponde, **reembolsa (simulado)** y emite
  **nota de crédito** por cada comprobante.
- Reserva **reprogramada** (otra fecha, hora o cancha): cierra los pagos en línea abiertos (monto viejo) y pone el horario nuevo
  en los pagos en revisión; al cobrar o aprobar un pago en línea vuelve a mirar la reserva y no registra un monto mayor que el
  saldo vigente (ver «Reserva reprogramada»).
- Reporte de ingresos para el panel.

## Arquitectura interna
`controlador` → `servicio` (interfaces + `…Impl`) → `repositorio` (Spring Data JPA) → `modelo`; además `dto` (records con
Bean Validation y los mensajes del SPEC §3), `mapeador`, `config`, `evento`, `cliente`, `tarea`,
`servicio.documento` (PDF/XML/QR/hash) y `pasarela.simulador` (el «proveedor externo»).

| Paquete | Clases principales |
|---|---|
| `servicio` | `PagoServicio` (Yape/Plin, validar, rechazar, caja), `ComprobanteServicio` (emitir, nota de crédito, anular, descargas), `IntentoPagoServicio` (pasarela beta y Mercado Pago), `WebhookServicio`, `CancelacionReservaServicio`, `ReprogramacionReservaServicio`, `ReporteIngresosServicio`, `AprobacionEnLinea` (registra el cobro aprobado por un proveedor, idempotente; relee la reserva y no aplica un monto mayor que el saldo vigente), `NumeracionComprobantes`, `VerificadorReservas`, `AlmacenComprobantes`, `ConfiguracionComercio`, `SemillaPagos` |
| `servicio.documento` | `ArmadorComprobante` (montos + sello), `GeneradorXmlUbl`, `GeneradorPdfComprobante` (Thymeleaf + openhtmltopdf), `GeneradorQr` (ZXing), `HuellaComprobante`, `ConceptoReserva` |
| `pasarela.simulador` | `SimuladorPasarela`, `ValidadorTarjeta` (Luhn, marca, vencimiento, CVV, titular, correo), `MarcaTarjeta`, `TarjetasPrueba`, `NotificadorComercio` (webhook + reintentos), `FirmaHmac`, `TransaccionPasarela` |
| `cliente` | `ReservasClienteHttp`, `CatalogoClienteHttp` (`RestClient` `@LoadBalanced` + `InterceptorInterno`, 3 s, circuit breaker), `MercadoPagoClienteHttp` (REST directo, 10 s) |
| `evento` | `BusEventos` + `PublicadorTrasCommit` (eventos y auditoría DESPUÉS del commit), `ConsumidorReservaCancelada`, `ConsumidorReservaReprogramada` |
| `tarea` | `ExpirarIntentosTarea` (cada minuto), `SemillaTarea` (cada 30 s, máx. 20 intentos) |

## Tablas (Flyway `V1__esquema.sql`, válido en MySQL 8.4 y MariaDB 10.4)
| Tabla | Contenido |
|---|---|
| `pago` | Columnas del SPEC + **fotografía de la reserva** (`cliente_dni`, `cliente_nombres`, `cliente_apellidos`, `cliente_telefono`, `cliente_correo`, `concepto`, `porcentaje_adelanto`) para emitir comprobantes y eventos sin volver a llamar a reservas. |
| `datos_facturacion` | Adquirente pedido (boleta a DNI «1» o factura a RUC «6»). Amplía el SPEC con `referencia_intento` (los pagos en línea guardan la facturación antes de que exista el pago). Si un envío no trae `comprobante` (reenvío tras un rechazo, pago en línea desde «Mi reserva», saldo en caja) se copia la última facturación pedida de la reserva; un `{"tipo":"BOLETA"}` explícito sí cambia a boleta. |
| `intento_pago` | Sesión de pago en línea `ALP-` + 10 caracteres, con estado, `expira_en` (15 min), `id_externo`, `url_checkout`, `id_pago` y la misma fotografía. |
| `webhook_evento` | Avisos recibidos: `UNIQUE(proveedor, id_externo)` = idempotencia; `firma_valida`, `payload JSON`, `procesado`, `resultado`. |
| `pasarela_transaccion` | Libro del **proveedor** simulado: marca, últimos 4, estado, código de autorización y su cola de avisos (outbox con reintentos). Nunca guarda el número completo ni el CVV. |
| `serie` | B001, F001, BC01, FC01 con `ultimo_correlativo` (se bloquea con `SELECT … FOR UPDATE`). |
| `comprobante` | Columnas del SPEC + `codigo_motivo` (catálogo 09) y **datos del emisor al emitir** (`emisor_ruc`, `emisor_razon_social`, `emisor_direccion`). `UNIQUE(serie, correlativo)`. Emisor ficticio: RUC **20000000001** (el anterior, 20612345679, existía en SUNAT; `V2__ruc_emisor_ficticio` corrige `emisor_ruc` y `cadena_qr` de los ya emitidos). |
| `evento_procesado` | Eventos de RabbitMQ ya consumidos. |

## Endpoints
Públicos (el gateway los enruta; el de la pasarela tiene límite 30/min):

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/pagos/metodos` | Yape/Plin (de catálogo, con valores por defecto), `tarjetaBeta`, `mercadoPago` (si hay `MP_ACCESS_TOKEN`), tarjetas de prueba, Yape beta, código 3DS y `adelantoPct`. |
| POST | `/api/pagos/comprobante` | multipart `codigoReserva, dni, metodo, codigoOperacion, archivo, comprobante` (JSON). Valida todos los campos a la vez (400 con errores por campo), imagen por bytes mágicos, DNI de la reserva, reserva PENDIENTE con `SIN_PAGO/OBSERVADO`, sin otro pago en revisión ni número de operación repetido → 201 `{idPago, estado:"PENDIENTE", mensaje}` + `pago.registrado`. |
| GET | `/api/pagos/reserva/{codigo}?dni=` | Pagos con enlaces a la boleta/factura y nota de crédito. 404 si el DNI no corresponde. |
| POST | `/api/pagos/pasarela/intentos` | `{codigoReserva, dni, tipoPago: ADELANTO\|TOTAL, comprobante}` → 201 `{referencia, urlCheckout, expiraEn}`. Cierra intentos abiertos anteriores de la reserva. |
| GET | `/api/pasarela/intentos/{ref}` | Datos del checkout. |
| POST | `/api/pasarela/intentos/{ref}/tarjeta` · `/3ds` · `/yape` | Resultado `{estado, codigoAutorizacion?, motivo?, marca, ultimos4}`. Antes de cobrar relee la reserva: si el intento ya no es el que se crearía ahora (su monto no es el adelanto vigente / total − lo validado, o supera el saldo) → 409 `INTENTO_EXPIRADO` con «El monto cambió porque la reserva cambió de horario; vuelve a iniciar el pago.» (o «El saldo de tu reserva cambió; …» si el horario no cambió); mismo monto pero otro horario → «Tu reserva cambió de horario; vuelve a iniciar el pago.». El intento queda EXPIRADO y no se cobra. |
| GET | `/api/pagos/intentos/{ref}/estado` | Sondeo de la página de resultado (enlaces del comprobante completos, con `&dni=`); en Mercado Pago, si sigue pendiente, consulta `GET /v1/payments/search?external_reference=…` («Ya pagué, verificar»). |
| POST | `/api/pagos/mercadopago/preferencias` | 201 `{referencia, initPoint, sandbox, expiraEn}`; 409 `MP_NO_CONFIGURADO` sin token. |
| POST | `/api/webhooks/pasarela-beta` | `X-Firma` (HMAC-SHA256 hex del cuerpo) + `X-Evento-Id`. 401 `FIRMA_INVALIDA`. |
| POST | `/api/webhooks/mercadopago` | `x-signature`/`x-request-id` (si hay `MP_WEBHOOK_SECRETO`) y `data.id`; consulta `GET /v1/payments/{id}`. |
| GET | `/api/comprobantes/{serieNumero}.pdf` · `.xml` `?codigo=&dni=` | PDF en línea / XML como descarga. 404 si no es de esa reserva y DNI. |

Panel (token; ADMIN y RECEPCION salvo donde se indica):

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/admin/pagos?estado&metodo&canal&desde&hasta&q&page&size&sort` | Paginado + `conteos` por estado. |
| GET | `/api/admin/pagos/{id}` · `/{id}/archivo` | Detalle (facturación, comprobantes) e imagen (`no-store`, `nosniff`). |
| POST | `/api/admin/pagos/{id}/validar` | Bloquea la fila; VALIDADO + comprobante + `pago.validado` + `comprobante.emitido` + auditoría. 409 si ya no está pendiente. |
| POST | `/api/admin/pagos/{id}/rechazar` | `{observacion}` 10–255 → `pago.rechazado`. |
| POST | `/api/admin/pagos/caja` | `{codigoReserva, tipoPago, metodo, monto, codigoOperacion?, comprobante}`; monto > saldo → 400 «El monto no puede superar el saldo de S/ X.» |
| GET | `/api/admin/comprobantes?tipo&estado&desde&hasta&q` · `/{id}` · `/{id}/pdf` · `/{id}/xml` | |
| POST | `/api/admin/comprobantes/{id}/anular` | **ADMIN** · `{motivo}` → nota de crédito (motivo 01) que referencia al original. |
| GET | `/api/admin/reportes/ingresos?desde&hasta` | **ADMIN** · `{total, cantidadPagos, ticketPromedio, porMetodo, porCanal, porTipoPago, porDia, porTipoComprobante, tiempoMedioValidacionMin, pagosRechazados, reembolsado}` (por defecto últimos 30 días, máximo 1 año). |

Internos (`X-Interno-Token`): `POST /interno/pagos/caja` (reserva manual: `{…cobro, registradoPor (id), registradoPorUsuario, reserva?}`)
y `GET /interno/comprobantes/{id}/pdf` (lo usa notificaciones).

**`POST /interno/pagos/{id}/validar-ia`** (Canchita, asistente con IA; `X-Interno-Token`) · cuerpo
`{lectura (objeto JSON o texto con lo que la IA leyó de la foto), validadoPor? (≤ 60; por defecto «Canchita (IA)»)}`.
Es la MISMA validación del panel (`POST /api/admin/pagos/{id}/validar`): pago VALIDADO, boleta o factura, `pago.validado`
(→ reservas confirma → constancia por correo y WhatsApp) y `comprobante.emitido`; `validado_por` = el actor, sin
`id_administrador`, bitácora `VALIDAR_PAGO_IA` con el actor «Canchita (IA)» y la lectura guardada en `pago.lectura_ia`
(Flyway `V3__lectura_ia.sql`, hasta 4000 caracteres). Respuesta 200 `{idPago, estado:"VALIDADO", monto, comprobante}`.
**Idempotente**: si el pago ya está VALIDADO responde 200 con su comprobante sin emitir otro ni publicar eventos.
**422 `PAGO_NO_EN_REVISION`** si el pago no es un comprobante Yape/Plin PENDIENTE (rechazado, anulado, de caja o de pasarela);
404 si no existe; 400 sin `lectura`; 403 sin el token interno. Las reglas de auto-validación (monto, destinatario, fecha,
operación) las aplica asistente-service ANTES de llamar; pagos solo exige que el pago siga en revisión.

## Comunicación con otros servicios
- **reservas-service** `GET /interno/reservas/{codigo}` (campos de DatosReserva planos + `estado, estadoPago, montoPagado, saldo, inicio`)
  y `GET /interno/reservas/para-semilla`. Caído → **503 `SERVICIO_NO_DISPONIBLE`** limpio. La misma consulta se repite antes
  de cobrar en la pasarela beta y al aprobar un pago en línea (siempre FUERA de la transacción).
- **catalogo-service** `GET /interno/configuracion` (`{clave: valor}` o `[{clave, valor}]`) con caché de 5 min; si no responde,
  valores por defecto y reintento en 30 s.
- **Mercado Pago** `POST /checkout/preferences` (items, payer, `external_reference`, `back_urls` a `FRONT_URL/pago/resultado?ref=`,
  `expires` a 15 min, `X-Idempotency-Key`; `auto_return` y `notification_url` solo si las URL no son locales), `GET /v1/payments/{id}`,
  `GET /v1/payments/search`.

## Eventos (RabbitMQ, siempre después del commit)
- Publica: `pago.registrado` (comprobante subido), `pago.validado` (validación, caja, pasarela, Mercado Pago), `pago.rechazado`,
  `comprobante.emitido` (boleta, factura y notas de crédito), `auditoria.accion` (validar, rechazar, cobrar en caja, anular, reembolso).
- Consume: `reserva.cancelada` (cola `pagos.reserva-cancelada`, 3 reintentos y DLQ, idempotente por `evento_procesado`):
  pagos PENDIENTE → ANULADO, intentos abiertos → EXPIRADO; con `devolucion`, pagos VALIDADOS → REEMBOLSADO y nota de crédito
  (motivo 06) por cada comprobante.
- Consume: `reserva.reprogramada` (cola `pagos.reserva-reprogramada`, 3 reintentos y DLQ, idempotente por `evento_procesado`):
  intentos abiertos → EXPIRADO y pagos PENDIENTE con el horario nuevo (ver «Reserva reprogramada»).

## Pasarela Alcanfores Pay (beta)
1. El checkout envía la tarjeta a `/api/pasarela/intentos/{ref}/tarjeta`. El simulador valida (Luhn, 13–19 dígitos, marca,
   vencimiento futuro, CVV 3/4, titular, correo) y decide con las tarjetas del SPEC (4111…1111 y 5555…4444 y Amex aprueban;
   4000 0027 6000 3184 pide 3-D Secure con 123456; …0002 fondos insuficientes; …9995 bloqueada; …0069 vencida; cualquier otra:
   «solo tarjetas de prueba»). Yape beta: 123456 aprueba, 000000 rechaza.
2. Guarda su transacción (marca + últimos 4) y **avisa al comercio** con `POST /api/webhooks/pasarela-beta`, cuerpo JSON firmado
   (`X-Firma` = HMAC-SHA256 con `PASARELA_BETA_SECRETO`) y `X-Evento-Id`. Si el aviso falla, reintenta con espera creciente
   (máx. 10). El simulador no es transaccional, así el webhook no choca con bloqueos abiertos.
3. El webhook verifica la firma (inválida → 401 y queda registrada), inserta `webhook_evento` (duplicado → 200 `duplicado:true`)
   y aprueba el intento en la MISMA transacción: pago VALIDADO `validado_por = "Pasarela"`, código de autorización de 6 dígitos,
   comprobante y eventos. Si algo falla, nada queda registrado y el proveedor reintenta.
4. Intentos sin pagar → EXPIRADO a los 15 min (tarea cada minuto; además el API ya los muestra expirados al vencer).
   Los logs solo muestran `**** **** **** 1111`; `SolicitudTarjeta.toString()` enmascara número y CVV.

## Reserva reprogramada (19/11/2026)
Pagos SÍ guarda una fotografía de la reserva (`InstantaneaReserva` en `pago` e `intento_pago`): datos del cliente, `concepto`
(cancha, deporte, día, horario y «(N turnos)») y `porcentaje_adelanto`. No guarda el total ni el saldo: el importe del comprobante
es el `monto` del pago. Por eso, al cambiar el horario:
- **Consumidor** (`ConsumidorReservaReprogramada` → `ReprogramacionReservaServicio`, una transacción, idempotente por `idEvento`):
  1. Intentos ABIERTOS de ese código (CREADO, REQUIERE_3DS o RECHAZADO; pasarela beta y Mercado Pago) creados ANTES del
     cambio (`creado_en <= ocurridoEn`; los dos relojes guardan segundos) → **EXPIRADO** con `motivo_rechazo` =
     «El monto cambió porque la reserva cambió de horario; vuelve a iniciar el pago.» si su monto ya no es el de un intento
     nuevo (ADELANTO → adelanto del evento; TOTAL → total del evento − lo VALIDADO en pagos), o «Tu reserva cambió de
     horario; vuelve a iniciar el pago.» si el horario nuevo cuesta lo mismo. Quedan ABIERTOS los creados después del cambio
     (el evento llegó tarde: reintentos, cola acumulada, DLQ o eventos en desorden) y los que ya tienen el monto y el
     concepto del horario nuevo. El sondeo `/api/pagos/intentos/{ref}/estado` y el checkout devuelven el motivo en
     `motivoRechazo`, y un cobro beta sobre ese intento responde 409 `INTENTO_EXPIRADO` con ese mensaje (no «venció el
     tiempo»; `AprobacionEnLinea.cerradoPorMonto` reconoce los tres motivos).
  2. Pagos **PENDIENTE** (comprobante Yape/Plin en revisión): `concepto` y `porcentaje_adelanto` del horario nuevo (se arman como
     siempre con `ConceptoReserva`; el cliente no cambia). La boleta que se emita al validarlos sale con el horario nuevo.
  3. No se tocan los pagos VALIDADOS ni sus comprobantes (ya emitidos), ni los intentos APROBADOS o ya EXPIRADOS.
  Intentos y pagos de la reserva se leen con `SELECT … FOR UPDATE` (en orden de id): si un webhook está aprobando o recepción
  validando a la vez, el evento espera y ve el estado ya confirmado (sin bloqueo, Hibernate reescribía la fila vieja y un pago
  recién validado volvía a PENDIENTE).
- **Antes de cobrar en la pasarela beta** (`abiertoParaCobro`) y **al aprobar** (`AprobacionEnLinea.aprobar`: webhook beta,
  webhook de Mercado Pago y «Ya pagué, verificar») se relee `/interno/reservas/{codigo}` fuera de la transacción. Saldo vigente =
  `montoTotal` de reservas − lo VALIDADO según pagos (como la caja).
  - Antes de cobrar (todavía no se cobró nada, cerrar no pierde dinero) la regla es estricta: el intento debe ser el que se
    crearía ahora. Si su monto no es el de un intento nuevo (ADELANTO → `montoAdelanto` vigente; TOTAL → total − lo
    VALIDADO) o supera el saldo, queda EXPIRADO y el checkout recibe 409 `INTENTO_EXPIRADO` con «El monto cambió porque…»
    (otro horario) o «El saldo de tu reserva cambió…» (mismo horario). Así no se cobra un adelanto viejo menor al vigente
    (quedaba CONFIRMADA con menos del adelanto, lo que `REPROGRAMAR_ADELANTO_INSUFICIENTE` evita) ni un «Pago total» viejo
    que ya no cubre el total. Mismo monto pero otro horario → «Tu reserva cambió de horario; vuelve a iniciar el pago.».
  - Al aprobar (el proveedor YA cobró), solo si `intento.monto` supera el saldo: **no se registra el pago** (sin comprobante
    ni `pago.validado`), el intento queda EXPIRADO con el motivo + «Te devolveremos lo cobrado.», el webhook responde 200
    «No aplicado: …» (procesado, sin reintentos) y la bitácora registra `PAGO_EN_LINEA_NO_APLICADO` (actor Sistema, entidad
    reserva) con monto, saldo, referencia y operación para devolverlo.
  - **La decisión es definitiva por operación del proveedor**: si después llega otra vez la MISMA operación (mismo id
    externo: «Ya pagué», `/pago/resultado`, otro aviso) responde «No aplicado» sin volver a evaluar, aunque ahora el saldo
    alcance (otro cambio a un horario más caro, un cobro de caja anulado): no se registra ni vuelve a la bitácora, porque
    ya se pidió devolverla. Otra operación sobre el mismo intento sí se evalúa.
  - Si el horario no cambió (otro cobro entró mientras el intento estaba abierto) el motivo es «El saldo de tu reserva cambió;
    vuelve a iniciar el pago.».
  - Si el monto cabe, se aprueba como siempre (aunque el intento haya expirado) y el pago toma el concepto de la reserva VIGENTE.
  - Reservas no responde → 503 sin registrar nada y el proveedor reintenta el aviso (la pasarela beta, hasta 10 veces); reservas
    no conoce el código → se aprueba con la fotografía del intento (no hay con qué comparar).
- Pendiente del front (no se tocó): el checkout beta muestra «El tiempo para pagar terminó» para todo EXPIRADO; debería mostrar
  `motivoRechazo` si viene. Y `frontend/src/utilidades/textosBitacora.ts` aún no rotula `PAGO_EN_LINEA_NO_APLICADO` (sale con
  `legible()`).

## Comprobantes
- **Un comprobante por cada cobro validado**. Boleta al DNI del cliente si no pidió factura; factura exige RUC 10/20 válido,
  razón social 3–150 y dirección 5–200.
- Numeración: `NumeracionComprobantes` bloquea la fila de la serie en la misma transacción del comprobante (si se revierte, el
  número no se consume). Probado con 100 emisiones concurrentes y 12 cobros simultáneos en MariaDB real.
- Montos con `Dinero` de `comun`: `op_gravada = total / 1.18` (2 decimales), `igv = total − op_gravada`, `monto_letras`.
- Hash: SHA-256 (base64) del XML UBL generado; se muestran 28 caracteres como «valor resumen».
  QR: `RUC|tipo|serie|numero|igv|total|fecha|tipoDocAdq|numDocAdq|valorResumen`.
- PDF A4 con logo, recuadro RUC/tipo/serie-número, adquirente, ítem (cantidad, unidad ZZ, descripción, valor unitario, importe),
  op. gravada/exonerada/inafecta, IGV, total, «SON: …», QR, valor resumen, estado «ACEPTADO (SIMULADO)», marca de agua diagonal
  «DOCUMENTO DE PRUEBA · SIN VALIDEZ TRIBUTARIA» y pie «Representación impresa de la … (simulada) · Proyecto académico UTP».
  Fuentes Inter y Barlow Semi Condensed incrustadas (licencia OFL).
- **Descripción del ítem (v2, varios turnos):** `ConceptoReserva` usa el `horarioTexto` que arma reservas-service y, si la
  reserva tiene más de un turno, agrega «(N turnos)»: «Alquiler de Cancha 1 · Fútbol 5 · vie 20/11/2026 9:00–10:00 a. m. y
  7:00–8:00 p. m. (2 turnos) · Adelanto 50 %». Un turno: «… vie 20/11/2026 7:00–8:00 p. m. · …». Si reservas aún no manda
  `horarioTexto`/`cantidadTurnos` (v1), se arma el rango con `Horarios.rango(horaInicio, horaFin)` de `comun` y se asume 1
  turno (`ReservaInterna.horario()`/`turnos()`). En el PDF cada tramo de horas queda sin cortes de línea. Los montos ya llegan
  sumados desde reservas; nada más cambia en pagos. Sin migraciones nuevas.
- XML UBL 2.1 `Invoice` / `CreditNote` (con `DiscrepancyResponse` y `BillingReference`), sin firma y con un comentario de documento simulado.
- Muestras: `docs/muestras/boleta-B001-00000012.pdf/.xml`, `factura-F001-00000003.pdf/.xml`, `nota-credito-FC01-00000001.pdf/.xml`
  (las genera `MuestrasComprobantesTest` en `target/muestras`).

## Semilla
Si `alcanfores.semilla.habilitada=true` y no hay pagos, `SemillaTarea` pide a reservas `/interno/reservas/para-semilla` cada 30 s
(máx. 20 veces) y crea: ADELANTO_PAGADO/PAGADO_TOTAL → pagos VALIDADOS con comprobante en fechas pasadas (correlativos en
orden cronológico; uno de cada cuatro con factura a RUC de prueba `2000000XXXD` válido por módulo 11; PAGADO_TOTAL alterna
pago total o adelanto + saldo en caja), EN_REVISION → pago PENDIENTE con imagen de ejemplo, OBSERVADO → RECHAZADO con la misma
observación que la línea de tiempo de reservas, REEMBOLSADO (cancelada con devolución) → cobro + REEMBOLSADO con nota de crédito.
Las 4 imágenes de ejemplo (`src/main/resources/semilla/comprobantes/`) son constancias ficticias genéricas con sello «EJEMPLO»
(sin logos reales). La semilla no publica eventos.

## Configuración (variables)
Comunes: `DB_HOST/DB_PORT/DB_USUARIO/DB_CLAVE`, `EUREKA_URL`, `RABBIT_*`, `JWT_SECRETO`, `INTERNO_TOKEN`, `FRONT_URL`,
`PUBLIC_API_URL`. Propias: `UPLOADS_DIR` (por defecto `./uploads`; imágenes en `comprobantes/`), `PASARELA_BETA_SECRETO`,
`PASARELA_BETA_WEBHOOK_URL` (vacía = el propio servicio en localhost), `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRETO`, `MP_URL_API`,
`SEMILLA_HABILITADA`. Multipart 5 MB por archivo / 6 MB por solicitud; páginas de hasta 100 elementos.

## Decisiones
- Fotografía de la reserva en `pago` e `intento_pago`: validar y emitir comprobantes no depende de que reservas esté arriba.
  Cobrar en la pasarela beta y aprobar un pago en línea SÍ relee la reserva (desde el 19/11/2026): si reservas no responde,
  503 y el proveedor reintenta.
- Lo ya cobrado para la caja se calcula en pagos-service (dueño de los pagos), no con `montoPagado` de reservas, que se
  actualiza por eventos y puede ir un paso atrás.
- Las llamadas HTTP a otros servicios se hacen fuera de la transacción de base de datos (`TransactionTemplate`).
- Un pago en línea aprobado se registra aunque el intento ya haya expirado (no se pierde un cobro hecho), SALVO que su monto
  supere el saldo vigente de la reserva: no se registra y la bitácora `PAGO_EN_LINEA_NO_APLICADO` pide devolverlo (decisión
  definitiva para esa operación, aunque luego el saldo alcance); los intentos rechazados
  pueden reintentarse con otra tarjeta mientras no expiren; un intento nuevo cierra los anteriores de la misma reserva.
- Un mismo número de operación Yape/Plin no puede sustentar dos pagos (409 `OPERACION_REPETIDA`, con
  `errores:[{campo:"codigoOperacion", mensaje}]` para que el front lo marque en el campo).
- `GET /api/pagos/reserva/{codigo}` incluye por pago `facturacion: {tipo, ruc, razonSocial, direccionFiscal}` (lo que se pidió).
- Idempotencia de Mercado Pago por `payment:{id}:{status}` (un pago puede avisar «pending» y luego «approved»).
- El time limiter de Resilience4j está desactivado: el timeout lo da el cliente HTTP (3 s) y la llamada corre en el mismo hilo
  (se conserva el `X-Request-Id`).
- `ManejadorErroresPagos` convierte `?sort=` inválido en 400 (el manejador común lo dejaría en 500).

## Pruebas (`mvn -B -f backend/pagos-service/pom.xml verify`)
138 pruebas, 0 fallos (19/11/2026, con `reserva.reprogramada` y los arreglos de su revisión; antes 133 y 123). JaCoCo: líneas 93,5 % en total, 92,5 % en la capa de servicio (instrucciones 93,6 % y
91,7 %; regla `check` ≥ 70 % en el pom).
Las de integración usan MariaDB `alcanfores_pagos_test` + Flyway, servidor en puerto aleatorio (la pasarela llama de verdad a su
webhook) y simulan reservas, catálogo, Mercado Pago y RabbitMQ.

| Clase | Qué cubre |
|---|---|
| `ValidacionIaTest` | `validar-ia`: valida como el panel con actor «Canchita (IA)» (evento `pago.validado`, comprobante B001, bitácora `VALIDAR_PAGO_IA`, `validado_por`, `lectura_ia` guardada), idempotencia (segunda llamada sin eventos ni otro comprobante), pago rechazado → 422 con lectura en texto, sin token 403, sin lectura 400, pago inexistente 404. |
| `FlujoComprobanteYapeTest` | 400 por campo (código, DNI, método, operación, archivo), imagen falsa con extensión .png y menor de 200 px, factura con RUC inválido y sin razón social, DNI ajeno 404, reservas caído 503, flujo subir → validar → boleta → eventos y auditoría, factura F001, rechazo, caja con monto > saldo (400) y saldo, caja interna, anulación (403 RECEPCION / NC BC01 / 409 repetida), descargas PDF/XML, reporte (403/401/400), token inválido 401; reserva de **2 turnos no seguidos** cobrada en caja: la
descripción del comprobante (BD, XML y PDF) lleva «9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)» y el total S/ 160. |
| `PasarelaBetaTest` | Tarjeta aprobada vía webhook, fondos/bloqueada/vencida, reintento tras rechazo, Luhn/CVV/vencimiento/marca, 3DS correcto e incorrecto, Yape beta, firma HMAC inválida 401, idempotencia por `X-Evento-Id`, intento expirado, PAN nunca guardado. |
| `MercadoPagoTest` | Sin token (`mercadoPago:false`, 409), cuerpo de la preferencia, webhook con `x-signature` válida/inválida e idempotente, avisos ignorados, «Ya pagué, verificar». |
| `NumeracionConcurrenteTest` | 100 correlativos simultáneos sin huecos ni duplicados, rollback no consume número, 12 cobros simultáneos → B001-1…12. |
| `CancelacionYSemillaTest` | `reserva.cancelada` (anulación, reembolso + nota de crédito, idempotencia) y semilla coherente sin eventos. |
| `ReprogramacionPagosTest` | `reserva.reprogramada`: intentos abiertos (beta en 3DS y Mercado Pago) → EXPIRADO con el motivo, los de otra reserva intactos, pago en revisión con concepto y % nuevos y la boleta al validarlo con el horario nuevo, idempotencia; el evento espera a una validación en curso y no devuelve el pago a PENDIENTE. Aprobación: webhook de Mercado Pago con monto mayor al saldo → sin pago ni `pago.validado`, intento EXPIRADO, una sola bitácora `PAGO_EN_LINEA_NO_APLICADO` aunque «Ya pagué» repita; beta no cobra el monto viejo (409, sin transacción del proveedor), tampoco un adelanto viejo menor al vigente ni un «Pago total» que ya no cubre el total (el intento nuevo sí se cobra); Mercado Pago ya cobró un monto que cabe → aprobado con el horario nuevo en la boleta; motivo de saldo sin cambio de horario; reservas caído → 503 y el reintento aprueba; reservas sin la reserva → fotografía del intento. Arreglos de la revisión: «No aplicado» definitivo por operación (otro cambio más caro o un cobro de caja anulado no lo registra después; otra operación sí se evalúa); evento atrasado o en desorden no expira el intento creado después del cambio, ni uno ya al día aunque los relojes se crucen; cambio al mismo precio → motivo neutro (consumidor y beta). |
| `ClientesHttpTest` | `MockRestServiceServer`: reservas (200/404/500→503; respuesta v1 sin `horarioTexto` → rango y 1 turno), catálogo (mapa/lista/caído), Mercado Pago (preferencia con token e idempotencia, pago, búsqueda, 502→503). |
| Unitarias | `ValidadorTarjetaTest`, `FirmaHmacTest` (vector conocido + manifiesto MP), `ConceptoEIgvTest` (IGV, letras, concepto con 1 y 2 turnos, concepto desde el evento v1/v2 e instantánea con otro horario), `ReservaInternaTest` (horario y turnos con respaldo v1), `FacturaCompletaTest`, `AlmacenComprobantesTest`, `ReglasCobroTest`, `ConfiguracionComercioTest`, `TareasTest`, `MuestrasComprobantesTest`, `ArranqueTest`. |
