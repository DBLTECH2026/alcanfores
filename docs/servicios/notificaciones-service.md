# notificaciones-service

Microservicio que **avisa al cliente cada paso de su reserva** por los dos canales, **correo** (SMTP) y **WhatsApp** (Evolution API v2, Baileys). Adjunta la constancia y los comprobantes en PDF, envía el **recordatorio 2 horas antes** del partido y guarda una **bitácora de envíos** que el panel muestra con vista previa.

| Dato | Valor |
|---|---|
| Paquete | `pe.alcanfores.notificaciones` |
| Puerto | 8095 |
| Base de datos | `alcanfores_notificaciones` (Flyway `V1__esquema.sql`) |
| Rutas del gateway | `/api/admin/notificaciones/**`, `/api/admin/recordatorios/**`, `/api/admin/whatsapp/**` |
| Depende de | reservas-service (constancia y datos de la reserva), pagos-service (PDF del comprobante), catalogo-service (configuración del complejo), RabbitMQ, SMTP, Evolution API |

## Responsabilidad

1. Consumir los eventos de dominio y convertir cada uno en **dos avisos** (correo + WhatsApp) con el mismo contenido.
2. Descargar los PDFs (constancia y comprobante) y adjuntarlos. Si la descarga falla, el aviso sale igual sin el PDF y se anota el motivo.
3. Programar, cancelar y enviar el recordatorio previo al partido (cron cada minuto).
4. Reintentar los envíos fallidos (cada 5 minutos, hasta 3 intentos) y permitir el reintento manual.
5. Conectar el WhatsApp del complejo desde el panel (QR o código de emparejamiento) y enviar mensajes de prueba.

El servicio **solo envía**: no lee ni responde chats.

## Tablas

| Tabla | Columnas | Notas |
|---|---|---|
| `notificacion` | `id, codigo_reserva, evento, canal ENUM('WHATSAPP','CORREO'), destino, asunto, cuerpo MEDIUMTEXT, adjuntos JSON, estado ENUM('PENDIENTE','ENVIADO','FALLIDO','SIMULADO'), intentos, ultimo_error, creado_en, enviado_en` | Una fila por aviso y canal. `cuerpo` = HTML del correo (logo como `cid:`) o texto del WhatsApp. `adjuntos` = `[{tipo, referencia, nombreArchivo, estado, detalle}]`: no se guarda el PDF, sino de dónde sacarlo (así el reintento lo vuelve a descargar). Índices por reserva, por `(estado, intentos)` y por fecha. |
| `recordatorio` | `id, codigo_reserva UQ, enviar_en, inicio_reserva, estado ENUM('PROGRAMADO','ENVIADO','CANCELADO','OMITIDO'), datos JSON, enviado_en, detalle` | `datos` = `{reserva: DatosReserva, saldoPendiente}`. `detalle` explica por qué se canceló u omitió (columna añadida al SPEC). |
| `evento_procesado` | `id_evento CHAR(36) PK, tipo, procesado_en` | Idempotencia de los consumidores. |

## Eventos consumidos

Colas propias, durables y con DLX (`alcanfores.eventos.dlx`): `notificaciones.<evento>`. Si el consumidor falla, se reintenta 3 veces y luego el mensaje pasa a la cola de muertos.

| Routing key | Cola | Aviso | PDF | Efecto adicional |
|---|---|---|---|---|
| `reserva.registrada` | `notificaciones.reserva-registrada` | «Reserva registrada: completa tu pago» (adelanto, plazo para pagar, enlace a Mi reserva) | Constancia | — |
| `pago.registrado` | `notificaciones.pago-registrado` | «Recibimos tu comprobante, lo validamos en minutos» | — | Solo si `canal = COMPROBANTE`. Pasarela y caja se validan al instante, así que el cliente recibe directamente la confirmación. Los datos de contacto se piden a reservas-service. |
| `pago.rechazado` | `notificaciones.pago-rechazado` | Observación + nuevo plazo + enlace para reenviar | — | Contacto y plazo vigente desde reservas-service. |
| `reserva.confirmada` | `notificaciones.reserva-confirmada` | Confirmación + saldo a pagar en el complejo + cómo llegar (dirección, referencia, Google Maps) | Constancia | **Programa el recordatorio.** |
| `comprobante.emitido` | `notificaciones.comprobante-emitido` | Boleta, factura o nota de crédito (con aviso de comprobante simulado) | Comprobante | — |
| `reserva.cancelada` | `notificaciones.reserva-cancelada` | Motivo, quién canceló, devolución o regla de no devolución | — | **Cancela el recordatorio.** |
| `reserva.asistencia` | `notificaciones.reserva-asistencia` | Si `ASISTIO`: invitación a calificar con `FRONT_URL/canchas/{idCancha}?resena=ALC-…` | — | `NO_ASISTIO`: no se envía nada. |

**Publica:** solo `auditoria.accion` (a través del `Auditor` de `comun`) en las acciones administrativas: `REINTENTAR_NOTIFICACION`, `EJECUTAR_RECORDATORIOS`, `CONECTAR_WHATSAPP`, `DESCONECTAR_WHATSAPP` y `PRUEBA_WHATSAPP`.

### Flujo de cada evento (idempotente)

1. Si su `idEvento` ya está en `evento_procesado`, se ignora.
2. Se arma el aviso **fuera de la transacción**, porque puede consultar a reservas o al catálogo.
3. En **una transacción** se inserta `evento_procesado` (la PK frena las copias concurrentes), se guardan los avisos como PENDIENTE y se programa o cancela el recordatorio.
4. Tras el commit se envían. Cada PDF se descarga **una sola vez** para los dos canales.
5. Si falta un dato imprescindible (por ejemplo, reservas no responde y no se sabe a quién avisar), se lanza la excepción y RabbitMQ reintenta. El evento no queda marcado como procesado.

## Canales

| Canal | Configurado si | Sin configuración |
|---|---|---|
| Correo | `MAIL_HOST` (+ `MAIL_PORT`, `MAIL_USUARIO`, `MAIL_CLAVE`, `MAIL_REMITENTE`; STARTTLS con `MAIL_STARTTLS`, `MAIL_AUTH`) | SIMULADO: se guarda el HTML y se ve en el panel |
| WhatsApp | `EVOLUTION_URL` + `EVOLUTION_APIKEY` (cabecera `apikey`), instancia `EVOLUTION_INSTANCIA` (`alcanfores` por defecto) | SIMULADO |

- **Correo:** `JavaMailSender`, multipart *mixed/related*, HTML de Thymeleaf. El logo va **embebido como CID** (`cid:logo-alcanfores`, PNG de 11 KB en `resources/correo/`), así se ve aunque el cliente de correo bloquee imágenes remotas. Los PDFs van como adjuntos.
- **WhatsApp (Evolution API v2):**
  - `POST /message/sendText/{inst}` `{number:"51XXXXXXXXX", text}`.
  - `POST /message/sendMedia/{inst}` `{number, mediatype:"document", mimetype:"application/pdf", media:<base64>, fileName, caption}`.
  - Si hay un PDF, el mensaje va como **leyenda del documento** (un solo envío, así un reintento no duplica el texto). Si el texto supera los 1000 caracteres, primero se envía el texto y luego el documento.
  - Gestión de la instancia:
    - `GET /instance/connect/{i}[?number=]` devuelve el QR en base64 y el código de emparejamiento.
    - Si la instancia no existe (404), se crea con `POST /instance/create {instanceName, integration:"WHATSAPP-BAILEYS", qrcode:true}`.
    - `GET /instance/connectionState/{i}` da el estado; con `open` se lee el número vinculado en `GET /instance/fetchInstances`.
    - `DELETE /instance/logout/{i}` cierra la sesión.
  - Los errores se traducen al español: clave inválida, instancia inexistente, número sin WhatsApp.
- **Textos de WhatsApp:** usan `*negritas*` y saltos de línea, sin emojis, y van firmados «Complejo Deportivo Los Alcanfores». Se arman con plantillas Thymeleaf en modo TEXT (`resources/plantillas/whatsapp/*.txt`).

### Plantillas de correo

Las plantillas están en `resources/plantillas/correo/`: `base.html` (layout), `componentes.html` (fila, tarjeta de la reserva, botón principal y secundario, notas, cómo llegar) y una plantilla por aviso: `reserva-registrada`, `pago-registrado`, `pago-rechazado`, `reserva-confirmada`, `comprobante-emitido`, `reserva-cancelada`, `invitacion-resena` y `recordatorio`.

- **Diseño:** tablas con estilos en línea, ancho máximo de 600 px y *media query* para celular (botón a ancho completo). Usa los colores de la marca: verde #27811F, azul noche #091A2A, menta #E8F3EC, verde profundo #0B5D33 y amarillo #F2B705 solo como acento de las notas.
- **Contenido:** pre-encabezado oculto, tarjeta con el código grande y los datos de la reserva, y un botón principal.
- **Pie:** dirección y referencia, WhatsApp (`wa.me`), correo y aviso de privacidad (Ley N.° 29733).
- **Enlaces para el cliente** (a partir de la primera URL de `FRONT_URL`): `/mi-reserva?codigo=ALC-…`, `/reservar/listo/{codigo}`, `/canchas/{idCancha}?resena=ALC-…`, `/reservar` y `/privacidad`.
- **Datos del complejo** (dirección, referencia, mapa, WhatsApp, `recordatorio_horas`, `devolucion_horas`): se leen de `GET http://catalogo-service/interno/configuracion` y se guardan en caché 5 minutos. Si el catálogo no responde, se usan los datos reales del complejo de `application.yml` y se vuelve a intentar en 1 minuto.

## Recordatorio

- Al consumir `reserva.confirmada` se crea (o reprograma) con `enviar_en = inicio − recordatorio_horas` (2 por defecto).
- **Cron:** `@Scheduled(cron = "0 * * * * *", zone = "America/Lima")` revisa los PROGRAMADOS cuya hora llegó:
  - si faltan **menos de 30 min** para el inicio → OMITIDO, con el motivo;
  - si reservas-service dice que la reserva está CANCELADA → CANCELADO;
  - si no, se toma con un `UPDATE … WHERE estado='PROGRAMADO'` (así dos instancias no lo envían dos veces) y se envía por los dos canales.
- **Contenido:** el tiempo que falta («Tu partido es en 2 horas», o «1 hora y 30 minutos» si se confirmó tarde), «de hoy» o «de mañana», cancha, hora, **saldo actual** (consultado a reservas; si no responde, se usa el guardado), «Llega 10 minutos antes», dirección + referencia + Google Maps.
- `reserva.cancelada` → CANCELADO.
- **Varios turnos (v2):** `inicio` = fecha + `horaInicio` de `DatosReserva`, que reservas-service llena con el PRIMER turno;
  así el recordatorio sale `recordatorio_horas` antes del primer turno aunque los turnos no sean seguidos.

## Horario en los textos (v2)
Todos los avisos (correo y WhatsApp) y el recordatorio muestran el campo «Hora» con `ModeloMensajes.horario`: el
`horarioTexto` de reservas-service («7:00–8:00 p. m.», «9:00–10:00 a. m. y 7:00–8:00 p. m.») y «(N turnos)» si son varios.
Los eventos y los recordatorios guardados antes de la v2 (JSON sin `horarioTexto` ni `cantidadTurnos`) se leen igual: el
rango se arma con `Horarios.rango(horaInicio, horaFin)`. «a las …» (confirmación y recordatorio) es la hora del primer turno.
Sin migraciones nuevas. La demo (`DatosDemo`) incluye una reserva de 2 turnos no seguidos.

## Reintentos

`@Scheduled(cron = "30 */5 * * * *", zone = "America/Lima")` reintenta:

- los FALLIDOS con `intentos < 3`;
- los PENDIENTES de hace más de 5 minutos (el proceso se cayó durante el envío).

Las pruebas de WhatsApp del panel no se reintentan solas. Cada intento suma `intentos` y guarda `ultimo_error`. El ADMIN puede reintentar a mano cualquier aviso FALLIDO, aunque ya haya agotado los 3 intentos.

## Endpoints (todos con JWT; las acciones solo-ADMIN usan `@PreAuthorize`)

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/admin/notificaciones?canal&estado&evento&q&desde&hasta&page&size&sort` | ADMIN, RECEPCION | Página de `{id, codigoReserva, evento, eventoTexto, canal, destino, asunto, estado, intentos, ultimoError, adjuntos, creadoEn, enviadoEn}`. `q` busca en código, destino y asunto. `sort` admite `creadoEn`, `enviadoEn`, `estado`, `canal`, `evento` e `intentos`. `size` va de 1 a 100. |
| GET | `/api/admin/notificaciones/{id}` | ADMIN, RECEPCION | Lo anterior + `formato` (HTML/TEXTO), `cuerpo`, `vistaPrevia` (HTML con el logo como data URI, para un iframe), `adjuntos[]` y `reintentable`. |
| POST | `/api/admin/notificaciones/{id}/reintentar` | ADMIN | Detalle con el nuevo estado. Si no es FALLIDO → 422 `NO_REINTENTABLE`; si otro proceso lo está reenviando → 409 `NOTIFICACION_EN_PROCESO`. |
| GET | `/api/admin/recordatorios?estado&page&size&sort` | ADMIN | Página de `{id, codigoReserva, cliente, telefono, cancha, inicioReserva, enviarEn, estado, enviadoEn, detalle}`. |
| POST | `/api/admin/recordatorios/ejecutar` | ADMIN | `{revisados, enviados, omitidos, cancelados}` (corre el job en el momento, para la demo). |
| GET | `/api/admin/whatsapp/estado` | ADMIN | `{configurado, instancia, estado: open, connecting, close o NO_CONFIGURADO, numero}` |
| POST | `/api/admin/whatsapp/conectar` | ADMIN | Cuerpo opcional `{telefono}` (`@Celular`). Responde `{qrBase64 (data URL), codigoEmparejamiento, estado, mensaje}`. |
| POST | `/api/admin/whatsapp/desconectar` | ADMIN | `{estado:"close", mensaje}` |
| POST | `/api/admin/whatsapp/prueba` | ADMIN | `{telefono, mensaje}` (celular peruano; mensaje de 1 a 1000 caracteres). Devuelve el detalle del aviso `whatsapp.prueba` (SIMULADO si no hay Evolution). |

**Errores:**

- 400 `VALIDACION` con errores por campo.
- 401 `NO_AUTENTICADO`; 403 `SIN_PERMISO`; 404 `NO_ENCONTRADO`.
- 409 `WHATSAPP_NO_CONFIGURADO`.
- 503 `WHATSAPP_NO_DISPONIBLE`, con el motivo que dio Evolution.

OpenAPI en `/swagger-ui.html`.

## Clientes internos

- **Clientes:**
  - `ReservasCliente`: `GET http://reservas-service/interno/reservas/{codigo}/constancia.pdf` y `GET …/interno/reservas/{codigo}`. Acepta la respuesta plana (DatosReserva + estado, saldo, venceEn) o anidada (`{reserva: {...}}`).
  - `PagosCliente`: `GET http://pagos-service/interno/comprobantes/{id}/pdf`.
  - `CatalogoCliente`: `GET http://catalogo-service/interno/configuracion`, como objeto `{clave: valor}` o lista `[{clave, valor}]`.
- **Conexión:** `RestClient` con `@LoadBalanced` + `InterceptorInterno` (envía `X-Interno-Token` y `X-Request-Id`). Timeouts: 3 s de conexión; 3 s de lectura en las consultas y 10 s en los PDFs.
- **Circuit breaker** (Resilience4j, en `LlamadaProtegida`): un 404 → `NO_ENCONTRADO`; cualquier otra falla → 503 `SERVICIO_NO_DISPONIBLE` «El servicio de X no responde. Intenta en unos segundos.».
- **PDFs:** se comprueba que la respuesta empiece con `%PDF`.

## Configuración (variables de entorno)

| Tema | Variables |
|---|---|
| Comunes | `DB_HOST/DB_PORT/DB_USUARIO/DB_CLAVE`, `EUREKA_URL`, `RABBIT_*`, `JWT_SECRETO`, `INTERNO_TOKEN`, `FRONT_URL`, `PUBLIC_API_URL` |
| Correo | `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USUARIO`, `MAIL_CLAVE`, `MAIL_REMITENTE`, `MAIL_NOMBRE_REMITENTE`, `MAIL_STARTTLS` (true), `MAIL_AUTH` (true) |
| WhatsApp | `EVOLUTION_URL`, `EVOLUTION_APIKEY`, `EVOLUTION_INSTANCIA` (alcanfores), `EVOLUTION_TIMEOUT` (20 s) |
| Del servicio | `NOTIF_TAREAS` (true; apaga los cron), `NOTIF_DATOS_DEMO` (false; carga avisos de ejemplo si la bandeja está vacía), `RABBIT_ESCUCHAR` (true; `false` para correrlo sin RabbitMQ) |

## Decisiones

1. **Dos motores Thymeleaf propios** (HTML para correo y TEXT para WhatsApp), con el mismo modelo de datos. Se excluye `ThymeleafAutoConfiguration` porque el servicio no sirve vistas, y `UserDetailsServiceAutoConfiguration` porque la autenticación es solo JWT.
2. **Logo embebido como CID**, porque `PUBLIC_API_URL` no sirve imágenes del front y muchos clientes de correo bloquean las imágenes remotas. Para el panel se entrega `vistaPrevia` con el logo como data URI.
3. **`adjuntos` guarda la referencia y no el PDF.** El reintento vuelve a descargar el archivo y la tabla no crece con binarios.
4. **WhatsApp con el PDF como documento y el texto como leyenda**: un solo envío atómico (sin textos duplicados al reintentar).
5. **`pago.registrado` solo avisa si el canal es COMPROBANTE**, porque pasarela y caja confirman al instante.
6. **Recordatorio con datos frescos**: al enviarlo se consulta el saldo y el estado actuales a reservas; si no responde, se usan los datos guardados. El texto calcula el tiempo que realmente falta.
7. **MariaDB local:** con `mysql-connector-j`, Hibernate no reconoce bien la versión de MariaDB y la validación del esquema falla en las columnas `JSON` (MariaDB las guarda como LONGTEXT). `config/ResolutorDialecto` (registrado en `hibernate.dialect_resolvers`) usa un `MariaDBDialect` con la versión real que acepta LONGTEXT donde la entidad espera JSON. **Solo actúa si el servidor es MariaDB**; en MySQL 8.4 la resolución es la estándar. Flyway 11.7 funcionó sin problemas con MariaDB 10.4.
8. **Tareas que se pueden apagar** (`alcanfores.notificaciones.tareas-habilitadas`): las pruebas llaman a los servicios directamente.
9. **Varias instancias:** los recordatorios y los reintentos se «toman» con un `UPDATE` condicional, así dos instancias no envían lo mismo.

## Pruebas

`mvn -B -f backend/notificaciones-service/pom.xml verify` → **101 pruebas, 0 fallos** (19/11/2026, v2). JaCoCo: **95,5 % de instrucciones** en total y **94,5 %** en la capa de
servicio (93,6 % de líneas).

| Clase | Qué cubre |
|---|---|
| `ConsumidorEventosTest` | Cada consumidor genera sus 2 avisos con destino, asunto, cuerpo y adjuntos correctos; idempotencia; modo SIMULADO; PDF no disponible; pago por pasarela sin aviso; reservas caído → excepción sin marcar procesado; NO_ASISTIO sin aviso; nota de crédito; cancelación con y sin devolución. |
| `VariosTurnosTest` | Reserva de 2 turnos no seguidos: registrada, confirmada y cancelada llevan «9:00–10:00 a. m. y 7:00–8:00 p. m. (2 turnos)» en correo y WhatsApp; el recordatorio se programa 2 h antes del primer turno (7:00 a. m.) y se envía con ese horario; un recordatorio guardado sin `horarioTexto` ni `cantidadTurnos` (JSON de la v1) se envía con el rango de horas. |
| `RecordatorioServicioTest` | Con `Tiempo.fijar`: programado (con las horas del catálogo), no se envía antes de tiempo, enviado a su hora con el contenido completo, saldo actual, reservas caído, OMITIDO (< 30 min), CANCELADO por evento y por reservas, reprogramación, ejecución manual auditada y listado. |
| `EvolutionClienteTest` | MockRestServiceServer: `sendText` (cabecera `apikey` y cuerpo), `sendMedia` (base64, `fileName`, `caption`), errores 401/400 (número sin WhatsApp)/500, `connectionState` + número, instancia inexistente, `connect` con QR y código, creación automática de la instancia, `logout`, sin conexión. |
| `EnvioServicioImplTest` | Evolution simulado: ENVIADO; documento con leyenda; texto largo + documento; error → **FALLIDO con el intento sumado** (y vuelve a sumar en el siguiente); error del documento; PDF no disponible; respuesta que no es PDF; descarga única; modo SIMULADO. |
| `CorreoSmtpTest` | **GreenMail 2.1.3** (SMTP real): ENVIADO, asunto, remitente, HTML, logo inline con Content-ID y constancia PDF adjunta; SMTP caído → FALLIDO. |
| `PlantillasTest` | Las 8 plantillas renderizan en HTML y TEXT: marca, colores, pie legal, sin variables sin resolver, **sin emojis**, firma y menos de 1000 caracteres; enlaces; tarjeta; textos condicionales. |
| `ControladoresAdminTest` | 401 sin token o con token inválido; **403** para RECEPCION en reintentar, recordatorios y WhatsApp; bandeja con filtros y paginación; 400 por filtros inválidos (canal, `size`, `sort`, fechas, `q`); detalle con vista previa; 404; reintentar (422 y OK, auditado); recordatorios; WhatsApp NO_CONFIGURADO y 409; validaciones de `/prueba` (teléfono, mensaje vacío o de más de 1000 caracteres, JSON inválido); prueba válida SIMULADA y auditada. |
| `ReintentosTest` | Reintenta FALLIDOS con menos de 3 intentos y PENDIENTES colgados; respeta los agotados, las pruebas de WhatsApp, los recientes y los enviados. |
| `ClientesInternosTest` | URLs internas, 404 → NO_ENCONTRADO, 500 → 503, respuestas plana y anidada, JSON ilegible, configuración como objeto o lista. |
| Otras | `ComplejoServicioImplTest` (caché y respaldo), `WhatsappServicioImplTest` (Evolution configurado; errores → 503), `ModeloMensajesTest`, `TareasProgramadasTest`, `DatosDemoTest`. |

Las pruebas usan la BD `alcanfores_notificaciones_test` (MariaDB local) con el perfil `test`: sin RabbitMQ ni Eureka, y con `RabbitTemplate`, `PublicadorEventos`, `Auditor` y los clientes de otros servicios reemplazados por mocks.

## Muestras

En `docs/muestras/`, generadas por el servicio en ejecución (los datos demo pasaron por el flujo real):

- `correo-confirmacion.html` y `correo-recordatorio.html`: la vista previa del panel, con el logo incrustado.
- `whatsapp-confirmacion.txt` y `whatsapp-recordatorio.txt`.
