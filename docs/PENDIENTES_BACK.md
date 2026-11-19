# Pendientes del back-end (encontrados al integrar el front con el back real)

> Estado al 18/11/2026: todos los puntos están resueltos o explicados (línea «Resuelto» en cada uno).

Cada punto trae cómo reproducirlo contra el API Gateway local (`http://localhost:8086`). El front no toca el back:
donde hizo falta, el portal se adaptó y se indica abajo.

## Portal

Probado el 18/11/2026 con los 7 microservicios en local. Clientes de prueba con DNI ficticio 9902xxxx.

### 1. Al reenviar un comprobante se pierde la factura pedida al reservar (media)
- Pasos: reservar y subir el comprobante con factura (`POST /api/pagos/comprobante`, `comprobante={"tipo":"FACTURA","ruc":"20131312955",…}`);
  rechazarlo (`POST /api/admin/pagos/{id}/rechazar`); reenviar el comprobante sin `comprobante` o con `{"tipo":"BOLETA"}`; validarlo.
- Resultado: se emite **boleta** (caso real ALC-D7TQ5R → B001-00000116). Pagos toma la facturación de cada envío y, si no viene, emite boleta.
  `GET /api/reservas/{codigo}` y `GET /api/pagos/reserva/{codigo}` no dicen qué comprobante se pidió, así que el cliente no puede repetirlo.
- Esperado: si el envío no trae `comprobante`, usar los `datos_facturacion` anteriores de la misma reserva, o exponer la facturación pedida
  en la consulta del cliente. Lo mismo para `POST /api/pagos/pasarela/intentos` desde «Mi reserva».
- En el front: el modal de reenvío ahora pide Boleta o Factura (con consulta SUNAT). Viene prellenado si la reserva se hizo en ese navegador.
  Así salió la factura F001-00000050 (ALC-G25GZ7).
- **Resuelto (18/11/2026):** si el envío no trae `comprobante` (reenvío, intento en línea o saldo en caja), pagos repite la última facturación pedida de la reserva (`FacturacionSolicitada` + `findFirstByCodigoReservaOrderByIdDesc`); un `{"tipo":"BOLETA"}` explícito sí cambia a boleta. `GET /api/pagos/reserva/{codigo}` expone `facturacion:{tipo, ruc, razonSocial, direccionFiscal}` por pago y «Mi reserva» prellena el reenvío con eso. Prueba: `ArreglosPagosTest` (reenvío, boleta explícita, pasarela y caja).

### 2. `puedeResenar` es `true` aunque la reserva ya tenga reseña (baja)
- `GET /api/reservas/ALC-BBGTSM?dni=99010015` → `"puedeResenar": true`, pero
  `POST /api/catalogo/canchas/3/resenas` con ese código → 409 `RESENA_DUPLICADA`. Reservas no sabe qué reseñas existen en catálogo.
- Efecto: «Mi reserva» muestra «Calificar la cancha» y el formulario termina en «Esta reserva ya tiene una reseña».
- **Resuelto:** catálogo expone `GET /interno/resenas/{codigoReserva}` → `{existe}`; reservas lo consulta (solo si la reserva ya permite reseñar; si catálogo no responde se deja en `true`). Pruebas: `ArreglosIntegracionTest.puedeResenarEsFalsoSiCatalogoYaTieneLaResena`, `ResenaTest.internoDiceSiLaReservaYaTieneResena`.

### 3. La reseña duplicada se responde antes de revisar el DNI (baja)
- `POST /api/catalogo/canchas/3/resenas` `{"codigoReserva":"ALC-BBGTSM","dni":"99019999",…}` (DNI que no es de la reserva) → 409
  «Esta reserva ya tiene una reseña…». Con eso se puede saber si un código existe y tiene reseña sin conocer el DNI.
  Mejor comprobar código + DNI primero (mismo 404/422 que en los otros casos).
- **Resuelto:** `ResenaServicioImpl.crear` verifica primero código + DNI con reservas (422 `RESENA_NO_ELEGIBLE` igual que un código inexistente) y solo después responde 409 `RESENA_DUPLICADA`. Pruebas: `ResenaTest.conUnDniAjenoNoSeRevelaQueLaReservaYaTieneResena`, `ResenaServicioImplTest.conDniAjenoNoSeRevisaSiYaHayResena`.

### 4. Una reserva cancelada con devolución mantiene `estadoPago`, `montoPagado` y `saldo` (baja)
- `GET /api/reservas/ALC-5ZYU9K?dni=99020001` (cancelada por el cliente con devolución de S/ 45.00) →
  `estadoPago: "ADELANTO_PAGADO"`, `montoPagado: 45.00`, `saldo: 45.00`. Pagos sí marcó el pago como REEMBOLSADO y emitió BC01-00000001.
- En el front: si la reserva está CANCELADA no se muestra «Saldo al llegar»; se muestra «Devuelto» con `cancelacion.montoDevolucion`.
- **Resuelto:** nuevo `estadoPago = REEMBOLSADO` (migración `V2__estado_pago_reembolsado`, que también corrige las ya canceladas): al cancelar con devolución `montoPagado` queda neto (0) y `saldo = 0`; toda reserva CANCELADA tiene `saldo = 0`. El evento sigue llevando lo cobrado. El front muestra «Devuelto». Pruebas: `ArreglosIntegracionTest` (portal, panel, migración).

### 5. Comprobante con enlaces incompletos en el sondeo del intento (baja)
- `GET /api/pagos/intentos/ALP-JY26PDM4AW/estado` → `comprobante.urlPdf = "/api/comprobantes/B001-00000113.pdf?codigo=ALC-GLWHHV"`
  (falta `&dni=`). Abrir esa URL → 400. En `GET /api/pagos/reserva/{codigo}?dni=` las URL sí vienen completas.
- **Resuelto:** `IntentoPagoServicioImpl.estado` arma `urlPdf`/`urlXml` con el DNI del cliente del intento (`&dni=`). Prueba: `ArreglosPagosTest.elPagoEnLineaDesdeMiReservaTambienRepiteLaFactura` (descarga la URL devuelta).

### 6. Doble punto en los textos de la línea de tiempo (baja)
- `GET /api/reservas/ALC-5ZYU9K?dni=99020001` → `linea[].detalle`:
  «Comprobante observado: La captura no muestra el número de operación completo.. Reenvíalo antes del…» y
  «Cancelada por el cliente: Se suspendió el partido del equipo por la lluvia.. Devolución de S/ 45.00.»
  Se concatena el texto del cliente o del administrador (que ya termina en punto) con otro punto. Además, el plazo del evento
  OBSERVADO sale en 24 h («19/11/2026 10:40») y el de REGISTRADA en 12 h («10:51 p. m.»). El portal usa 12 h.
- **Resuelto:** `ReglasReserva.frase/textoObservado/textoCancelacion` (sin doble punto; plazo en 12 h «19/11/2026 a las 10:40 a. m.»); `V3__textos_linea_tiempo` corrige los hitos ya guardados. Pruebas: `ArreglosIntegracionTest.laLineaDeTiempoNoDuplicaElPuntoYUsaHoraDe12Horas`, `v3QuitaElDoblePuntoDeLosHitosGuardados`.

### 7. El RUC «ficticio» del emisor existe en SUNAT (media, para el informe)
- `GET /api/documentos/ruc/20612345679` → `DRAGSTONE PERU S.A.C.`, estado `BAJA DE OFICIO`. Es el RUC que el SPEC y pagos-service usan
  como emisor «ficticio» y aparece en todas las boletas, facturas, notas de crédito (PDF y XML) y en el nombre del XML. Conviene cambiarlo
  por uno válido por módulo 11 que no esté asignado, o explicarlo en el informe.
- **Resuelto:** emisor ficticio `20000000001` (válido por módulo 11) en código, semillas, pruebas, SPEC, docs, muestras y front (`Legales.tsx`, mocks). BD ya creadas: `catalogo V3__ruc_emisor_ficticio` (clave `ruc_emisor`) y `pagos V2__ruc_emisor_ficticio` (`emisor_ruc` y `cadena_qr` de los comprobantes). Pruebas: `ConfiguracionTest`, `ArreglosPagosTest.v2PasaLosComprobantesDelRucViejoAlFicticio`.

### 8. Todas las consultas de DNI responden 503 (por revisar)
- `GET /api/documentos/dni/99020001` (y cualquier DNI probado) → 503 `CONSULTA_NO_DISPONIBLE`, mientras que los RUC sí responden
  (`fuente: "CACHE"`). `FactilizaProveedor` lanza 503 si Factiliza responde con un estado distinto de 404/400/422 o no responde.
  No se probó con DNI de personas reales (por indicación); revisar en el log de reservas-service qué devuelve Factiliza para DNI
  (¿plan del token sin DNI?). El portal ya muestra el aviso y deja escribir los datos a mano.
- **Resuelto:** Factiliza tarda ~11 s en responder 404 a un DNI inexistente y el cliente cortaba a los 5 s. Timeout de 15 s en reservas, ruta `reservas-documentos` con 20 s en el gateway y 25 s en el front; el 404 sale como `DNI_NO_ENCONTRADO`/`RUC_NO_ENCONTRADO` con mensaje amable y se guarda 1 día en caché. Pruebas: `FactilizaProveedorTest` (servidor que tarda 6 s), `DocumentosTest` (MockRestServiceServer + caché negativa), `ValoresPorDefectoTest` (gateway).

### 9. `OPERACION_REPETIDA` llega sin campo (sugerencia)
- `POST /api/pagos/comprobante` con un número de operación ya validado (p. ej. YAPE 28027508) → 409 `OPERACION_REPETIDA`, `errores: []`.
  Si trajera `errores:[{campo:"codigoOperacion",…}]`, cualquier cliente lo marcaría en el campo. El portal ya lo hace por el `codigo`.
- **Resuelto:** el 409 trae `errores:[{campo:"codigoOperacion", mensaje}]`. Prueba: `ArreglosPagosTest.operacionRepetidaMarcaElCampoCodigoOperacion`.

### 10. Semilla con observación distinta en la reserva y en el pago (muy baja)
- `ALC-ZM4V2Q`: la línea de tiempo dice «la imagen no permite leer el número de operación» y `GET /api/pagos/reserva/ALC-ZM4V2Q?dni=99010005`
  dice «La imagen está borrosa y no se puede leer la fecha del pago.»
- **Resuelto:** ambas semillas usan la misma observación («La imagen no permite leer el número de operación…») y la semilla de pagos ahora crea el reembolso + nota de crédito de las canceladas con devolución. Rige para BD nuevas (la semilla no corre si ya hay datos). Prueba: `CancelacionYSemillaTest.semillaCreaPagosCoherentesSinPublicarEventos`.

## Panel

Probado el 18/11/2026 desde el panel (Vite en 5187) con `rhuaman` (ADMIN) y `mtorres` (RECEPCION). Todas las acciones del panel
funcionaron contra el back real; esto es lo que conviene corregir o tener en cuenta.

### P1. CORS: un Origin no permitido recibe 403 SIN cuerpo (media)
- `curl -X POST -H "Authorization: Bearer <token>" -H "Origin: http://localhost:5187" -H "Content-Type: application/json" -d '{}' http://localhost:8086/api/admin/recordatorios/ejecutar`
  → `403`, cuerpo vacío (0 bytes). Con `Origin: http://localhost:5186` (o sin Origin) responde normal.
- Es la política CORS del gateway (solo `FRONT_URL`), pero el error no usa el cuerpo estándar del SPEC §0 (`{status, codigo, mensaje…}`),
  así que el front solo puede decir «Algo falló». Sugerencia: devolver `403 {codigo:"ORIGEN_NO_PERMITIDO", mensaje:…}` y, para desarrollo,
  aceptar en `FRONT_URL` la lista `http://localhost:5186,http://localhost:5187`.
- En el front: no se cambió nada; el Vite del panel en 5187 se arrancó con un proxy que envía `Origin: http://localhost:5186`.
- **Resuelto:** `FiltroCors` responde 403 `ORIGEN_NO_PERMITIDO` con el cuerpo `ErrorApi` y `FRONT_URL` por defecto es `http://localhost:5186,http://localhost:5187`. Pruebas: `GatewayIntegracionTest.preflightDeOtroOrigenRechazado`, `solicitudSimpleDeOtroOrigenRecibe403ConErrorApi`, `ValoresPorDefectoTest.enLocalCorsAceptaElPortalYElPanel`.

### P2. Reserva cancelada con devolución mantiene `estadoPago`, `montoPagado` y `saldo` (baja; también en el portal, punto 4)
- `GET /api/admin/reservas/ALC-QNBHGA` (manual, adelanto S/ 45 en efectivo, cancelada desde el panel con devolución) →
  `estado: CANCELADA`, `estadoPago: ADELANTO_PAGADO`, `montoPagado: 45.00`, `saldo: 45.00`, `cancelacion.devolucion: true`.
  Pagos sí dejó el pago REEMBOLSADO y emitió la nota de crédito BC01-00000002 (anula B001-00000119).
- En el panel se ve «Pagado S/ 45.00 / Saldo S/ 45.00» con el aviso rojo de cancelación y devolución; lo correcto sería saldo 0 y estado de pago «Devuelto».
- **Resuelto:** igual que el punto 4 (`REEMBOLSADO`, pagado neto, saldo 0); el panel muestra «Devuelto» y ya no ofrece cobrar saldo. Prueba: `ArreglosIntegracionTest.elPanelCancelaConDevolucionYLaBitacoraDiceCuantoSeDevolvio`.

### P3. La reserva manual ignora los nombres escritos si el DNI ya existe (baja)
- `POST /api/admin/reservas` con `cliente:{dni:"99010003", nombres:"Lucía", apellidos:"Mendoza Paredes", …}` → la reserva ALC-T4W7TZ queda a nombre de
  «Carlos Mendoza Huamán» (el cliente existente), sin aviso. El upsert solo actualiza celular y correo (así lo dicen los docs).
- En el panel la lupa del DNI ya trae los datos del cliente existente para que recepción vea a quién se registra; si se corrigen a mano, el back no los guarda.
  Sugerencia: actualizar también nombres/apellidos cuando vienen de la reserva manual (no verificados por RENIEC) o responder un aviso.
- **Resuelto:** en la reserva manual, si el cliente NO está verificado por RENIEC se guardan los nombres y apellidos escritos; si está verificado se conservan los de RENIEC (celular y correo siempre se actualizan; la web no cambia nombres de un cliente existente). Documentado en `docs/servicios/reservas-service.md`. Pruebas: `ArreglosIntegracionTest.laReservaManual*`, `laReservaWebNoCambia*`.

### P4. No se pudo probar «Reintentar» notificación (sin datos)
- `GET /api/admin/notificaciones?estado=FALLIDO` → 0 avisos: sin SMTP ni Evolution todo queda SIMULADO y nunca FALLIDO. El botón «Reintentar»
  (solo ADMIN, solo si `reintentable`) está hecho pero no hubo un aviso fallido para probarlo.
- **Sin cambio en el back:** el reintento ya está cubierto por `ControladoresAdminTest.reintentar` (notificaciones-service: solo FALLIDOS, 422 si no, auditado); en local todo queda SIMULADO porque no hay SMTP ni Evolution. Para verlo en vivo hace falta un SMTP o Evolution caído/configurado.

### P5. WhatsApp sin configurar (esperado)
- `POST /api/admin/whatsapp/conectar` → 409 `WHATSAPP_NO_CONFIGURADO`. El panel lo muestra en la pestaña WhatsApp de Configuración y en el modal
  del QR con lo que falta (`EVOLUTION_URL`, `EVOLUTION_APIKEY`, opcional `EVOLUTION_INSTANCIA`). La prueba de envío queda SIMULADO.
- **Sin cambio (esperado):** se configura con `EVOLUTION_URL`, `EVOLUTION_APIKEY` y `EVOLUTION_INSTANCIA`.
