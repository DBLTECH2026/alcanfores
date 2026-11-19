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
- Reporte de ingresos para el panel.

## Arquitectura interna
`controlador` → `servicio` (interfaces + `…Impl`) → `repositorio` (Spring Data JPA) → `modelo`; además `dto` (records con
Bean Validation y los mensajes del SPEC §3), `mapeador`, `config`, `evento`, `cliente`, `tarea`,
`servicio.documento` (PDF/XML/QR/hash) y `pasarela.simulador` (el «proveedor externo»).

| Paquete | Clases principales |
|---|---|
| `servicio` | `PagoServicio` (Yape/Plin, validar, rechazar, caja), `ComprobanteServicio` (emitir, nota de crédito, anular, descargas), `IntentoPagoServicio` (pasarela beta y Mercado Pago), `WebhookServicio`, `CancelacionReservaServicio`, `ReporteIngresosServicio`, `AprobacionEnLinea` (registra el cobro aprobado por un proveedor, idempotente), `NumeracionComprobantes`, `VerificadorReservas`, `AlmacenComprobantes`, `ConfiguracionComercio`, `SemillaPagos` |
| `servicio.documento` | `ArmadorComprobante` (montos + sello), `GeneradorXmlUbl`, `GeneradorPdfComprobante` (Thymeleaf + openhtmltopdf), `GeneradorQr` (ZXing), `HuellaComprobante`, `ConceptoReserva` |
| `pasarela.simulador` | `SimuladorPasarela`, `ValidadorTarjeta` (Luhn, marca, vencimiento, CVV, titular, correo), `MarcaTarjeta`, `TarjetasPrueba`, `NotificadorComercio` (webhook + reintentos), `FirmaHmac`, `TransaccionPasarela` |
| `cliente` | `ReservasClienteHttp`, `CatalogoClienteHttp` (`RestClient` `@LoadBalanced` + `InterceptorInterno`, 3 s, circuit breaker), `MercadoPagoClienteHttp` (REST directo, 10 s) |
| `evento` | `BusEventos` + `PublicadorTrasCommit` (eventos y auditoría DESPUÉS del commit), `ConsumidorReservaCancelada` |
| `tarea` | `ExpirarIntentosTarea` (cada minuto), `SemillaTarea` (cada 30 s, máx. 20 intentos) |

## Tablas (Flyway `V1__esquema.sql`, válido en MySQL 8.4 y MariaDB 10.4)
| Tabla | Contenido |
|---|---|
| `pago` | Columnas del SPEC + **fotografía de la reserva** (`cliente_dni`, `cliente_nombres`, `cliente_apellidos`, `cliente_telefono`, `cliente_correo`, `concepto`, `porcentaje_adelanto`) para emitir comprobantes y eventos sin volver a llamar a reservas. |
| `datos_facturacion` | Adquirente pedido (boleta a DNI «1» o factura a RUC «6»). Amplía el SPEC con `referencia_intento` (los pagos en línea guardan la facturación antes de que exista el pago). |
| `intento_pago` | Sesión de pago en línea `ALP-` + 10 caracteres, con estado, `expira_en` (15 min), `id_externo`, `url_checkout`, `id_pago` y la misma fotografía. |
| `webhook_evento` | Avisos recibidos: `UNIQUE(proveedor, id_externo)` = idempotencia; `firma_valida`, `payload JSON`, `procesado`, `resultado`. |
| `pasarela_transaccion` | Libro del **proveedor** simulado: marca, últimos 4, estado, código de autorización y su cola de avisos (outbox con reintentos). Nunca guarda el número completo ni el CVV. |
| `serie` | B001, F001, BC01, FC01 con `ultimo_correlativo` (se bloquea con `SELECT … FOR UPDATE`). |
| `comprobante` | Columnas del SPEC + `codigo_motivo` (catálogo 09) y **datos del emisor al emitir** (`emisor_ruc`, `emisor_razon_social`, `emisor_direccion`). `UNIQUE(serie, correlativo)`. |
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
| POST | `/api/pasarela/intentos/{ref}/tarjeta` · `/3ds` · `/yape` | Resultado `{estado, codigoAutorizacion?, motivo?, marca, ultimos4}`. |
| GET | `/api/pagos/intentos/{ref}/estado` | Sondeo de la página de resultado; en Mercado Pago, si sigue pendiente, consulta `GET /v1/payments/search?external_reference=…` («Ya pagué, verificar»). |
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

## Comunicación con otros servicios
- **reservas-service** `GET /interno/reservas/{codigo}` (campos de DatosReserva planos + `estado, estadoPago, montoPagado, saldo, inicio`)
  y `GET /interno/reservas/para-semilla`. Caído → **503 `SERVICIO_NO_DISPONIBLE`** limpio.
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
- XML UBL 2.1 `Invoice` / `CreditNote` (con `DiscrepancyResponse` y `BillingReference`), sin firma y con un comentario de documento simulado.
- Muestras: `docs/muestras/boleta-B001-00000012.pdf/.xml`, `factura-F001-00000003.pdf/.xml`, `nota-credito-FC01-00000001.pdf/.xml`
  (las genera `MuestrasComprobantesTest` en `target/muestras`).

## Semilla
Si `alcanfores.semilla.habilitada=true` y no hay pagos, `SemillaTarea` pide a reservas `/interno/reservas/para-semilla` cada 30 s
(máx. 20 veces) y crea: ADELANTO_PAGADO/PAGADO_TOTAL → pagos VALIDADOS con comprobante en fechas pasadas (correlativos en
orden cronológico; uno de cada cuatro con factura a RUC de prueba `2000000XXXD` válido por módulo 11; PAGADO_TOTAL alterna
pago total o adelanto + saldo en caja), EN_REVISION → pago PENDIENTE con imagen de ejemplo, OBSERVADO → RECHAZADO con observación.
Las 4 imágenes de ejemplo (`src/main/resources/semilla/comprobantes/`) son constancias ficticias genéricas con sello «EJEMPLO»
(sin logos reales). La semilla no publica eventos.

## Configuración (variables)
Comunes: `DB_HOST/DB_PORT/DB_USUARIO/DB_CLAVE`, `EUREKA_URL`, `RABBIT_*`, `JWT_SECRETO`, `INTERNO_TOKEN`, `FRONT_URL`,
`PUBLIC_API_URL`. Propias: `UPLOADS_DIR` (por defecto `./uploads`; imágenes en `comprobantes/`), `PASARELA_BETA_SECRETO`,
`PASARELA_BETA_WEBHOOK_URL` (vacía = el propio servicio en localhost), `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRETO`, `MP_URL_API`,
`SEMILLA_HABILITADA`. Multipart 5 MB por archivo / 6 MB por solicitud; páginas de hasta 100 elementos.

## Decisiones
- Fotografía de la reserva en `pago` e `intento_pago`: validar, emitir comprobantes y aprobar pagos en línea no depende de que
  reservas esté arriba.
- Lo ya cobrado para la caja se calcula en pagos-service (dueño de los pagos), no con `montoPagado` de reservas, que se
  actualiza por eventos y puede ir un paso atrás.
- Las llamadas HTTP a otros servicios se hacen fuera de la transacción de base de datos (`TransactionTemplate`).
- Un pago en línea aprobado se registra aunque el intento ya haya expirado (no se pierde un cobro hecho); los intentos rechazados
  pueden reintentarse con otra tarjeta mientras no expiren; un intento nuevo cierra los anteriores de la misma reserva.
- Un mismo número de operación Yape/Plin no puede sustentar dos pagos (409 `OPERACION_REPETIDA`).
- Idempotencia de Mercado Pago por `payment:{id}:{status}` (un pago puede avisar «pending» y luego «approved»).
- El time limiter de Resilience4j está desactivado: el timeout lo da el cliente HTTP (3 s) y la llamada corre en el mismo hilo
  (se conserva el `X-Request-Id`).
- `ManejadorErroresPagos` convierte `?sort=` inválido en 400 (el manejador común lo dejaría en 500).

## Pruebas (`mvn -B -f backend/pagos-service/pom.xml verify`)
105 pruebas, 0 fallos. JaCoCo: líneas 92,9 % en total, 92,1 % en la capa de servicio (regla `check` ≥ 70 % en el pom).
Las de integración usan MariaDB `alcanfores_pagos_test` + Flyway, servidor en puerto aleatorio (la pasarela llama de verdad a su
webhook) y simulan reservas, catálogo, Mercado Pago y RabbitMQ.

| Clase | Qué cubre |
|---|---|
| `FlujoComprobanteYapeTest` | 400 por campo (código, DNI, método, operación, archivo), imagen falsa con extensión .png y menor de 200 px, factura con RUC inválido y sin razón social, DNI ajeno 404, reservas caído 503, flujo subir → validar → boleta → eventos y auditoría, factura F001, rechazo, caja con monto > saldo (400) y saldo, caja interna, anulación (403 RECEPCION / NC BC01 / 409 repetida), descargas PDF/XML, reporte (403/401/400), token inválido 401. |
| `PasarelaBetaTest` | Tarjeta aprobada vía webhook, fondos/bloqueada/vencida, reintento tras rechazo, Luhn/CVV/vencimiento/marca, 3DS correcto e incorrecto, Yape beta, firma HMAC inválida 401, idempotencia por `X-Evento-Id`, intento expirado, PAN nunca guardado. |
| `MercadoPagoTest` | Sin token (`mercadoPago:false`, 409), cuerpo de la preferencia, webhook con `x-signature` válida/inválida e idempotente, avisos ignorados, «Ya pagué, verificar». |
| `NumeracionConcurrenteTest` | 100 correlativos simultáneos sin huecos ni duplicados, rollback no consume número, 12 cobros simultáneos → B001-1…12. |
| `CancelacionYSemillaTest` | `reserva.cancelada` (anulación, reembolso + nota de crédito, idempotencia) y semilla coherente sin eventos. |
| `ClientesHttpTest` | `MockRestServiceServer`: reservas (200/404/500→503), catálogo (mapa/lista/caído), Mercado Pago (preferencia con token e idempotencia, pago, búsqueda, 502→503). |
| Unitarias | `ValidadorTarjetaTest`, `FirmaHmacTest` (vector conocido + manifiesto MP), `ConceptoEIgvTest` (IGV y letras), `FacturaCompletaTest`, `AlmacenComprobantesTest`, `ReglasCobroTest`, `ConfiguracionComercioTest`, `TareasTest`, `MuestrasComprobantesTest`, `ArranqueTest`. |
