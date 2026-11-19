# asistente-service (Canchita)

Asistente con IA del Complejo Deportivo Los Alcanfores: atiende por **WhatsApp** (Evolution API) y por el **chat del portal**
(botón flotante), reserva canchas en 3 pasos, lee la foto del comprobante de Yape/Plin y, si todo calza, valida el pago sola.
Contrato: `docs/SPEC_IA.md`. Carpeta `asistente/` (Node 22 + TypeScript + Fastify + mysql2), puerto **8096**, BD
`alcanfores_asistente`. Cerebro: **SDK de agentes de Claude** (`@anthropic-ai/claude-agent-sdk`) con la suscripción del dueño
(`CLAUDE_CODE_OAUTH_TOKEN`) o una `ANTHROPIC_API_KEY`, igual que ELIA y el agente del ERP.

## Responsabilidad
- Conversar con el cliente (español peruano, breve, sin emojis) y llevarlo por el flujo: (1) cancha, día y turnos (varios),
  (2) DNI → RENIEC, celular, correo, boleta o factura, (3) Yape/Plin o enlace de tarjeta. Resumen y «¿Confirmo?» antes de reservar.
- Recibir la foto del comprobante, leerla con visión, registrarla en pagos y aplicar las **reglas de auto-validación**.
- Consultar y cancelar reservas (siempre con código + DNI).
- Guardar las conversaciones (para el contexto del modelo y para el panel) y respetar la **pausa** cuando escribe el equipo.

## Arquitectura interna (`asistente/src`)
| Archivo | Qué hace |
|---|---|
| `servidor.ts` / `app.ts` | Arranque y armado de Fastify con sus dependencias (las pruebas lo arman con dobles). |
| `config.ts` | Variables de entorno con valores por defecto de local; tipo de credencial (nunca su valor). |
| `canchita.ts` | Orquestador: guarda la entrada, revisa la pausa, lee fotos, corre el turno del cerebro, guarda y devuelve respuestas. |
| `cola.ts` | Cola por conversación: un turno a la vez por chat; chats distintos en paralelo. |
| `herramientas.ts` | Las 12 herramientas (abajo; `mis_reservas` solo por WhatsApp). Validan sus argumentos con Zod y llaman a la API pública por el gateway (salvo `mis_reservas` y la confirmación de `reprogramar_reserva`, que van directo a reservas-service). |
| `reglasPago.ts` | Reglas de auto-validación del comprobante (código puro, con pruebas). |
| `reglasReprogramar.ts` | Reglas del cambio de horario del back (motivo de `!puedeReprogramar`, mismo horario, monto menor, adelanto insuficiente con el redondeo de `Dinero.porcentaje`, plazo para pagar tras el cambio y cambio ya aplicado en un reintento) y sus mensajes (código puro, con pruebas). |
| `prompt.ts` / `respuesta.ts` | Instrucciones de Canchita, mensaje del turno (historial + estado + mensaje nuevo) y parser de la respuesta (`---`, `OPCIONES:`, enlaces). |
| `cerebro/cerebroClaude.ts` | Cerebro real: `query()` del SDK con un servidor MCP en proceso (`createSdkMcpServer` + `tool`). Lectura de la foto en una llamada aparte con `outputFormat` JSON. |
| `cerebro/tipos.ts` | Interfaz `Cerebro` (se reemplaza por uno simulado en las pruebas). |
| `api.ts` / `ajustes.ts` | Cliente HTTP (gateway + `/interno` de catálogo, pagos y reservas + `reprogramar` directo a reservas) e interruptores con caché de 30 s. |
| `whatsapp/*` | Cliente de Evolution, procesador del webhook y sincronizador del webhook con el interruptor. |
| `rutas/*` | Chat web, panel (JWT) y utilidades (error estándar, JWT HS256). |
| `bd/*` | Repositorio MySQL (migraciones al arrancar) y repositorio en memoria (pruebas). |

### Cómo usa el SDK de agentes
- `query({ prompt, options })` con `systemPrompt` propio, `tools: []` (sin Bash/Read/… de Claude Code), `mcpServers: { canchita }`,
  `allowedTools: ["mcp__canchita__…"]`, `permissionMode: "dontAsk"`, `strictMcpConfig: true`, `settingSources: []` (no lee ajustes
  ni CLAUDE.md del disco), `persistSession: false`, `maxTurns` 10, `model` = `IA_MODELO` (por defecto `claude-opus-5-5`),
  `effort` = `IA_ESFUERZO` (por defecto `low`) y un `AbortController` de 60 s.
- Memoria: no se reanudan sesiones del SDK; cada turno recibe los **últimos 20 mensajes** guardados en MySQL + un resumen del estado
  (cliente, reserva de la conversación con su DNI, método y comprobante elegidos). El texto del cliente va entre `<<<` `>>>`.
- La foto del comprobante NO entra al turno de chat: se lee antes, en una llamada aparte sin herramientas que devuelve JSON
  (`esComprobante, app, monto, codigoOperacion, fechaHora, destinatario, numeroDestino, marcaDeAgua, pareceEditada, descripcion`),
  se guarda en `imagen.lectura` y el turno la recibe como texto `[Foto idImagen=N …]`. Se lee una sola vez.

## Herramientas (MCP en proceso)
| Herramienta | Llama a |
|---|---|
| `info_complejo` | `GET /api/catalogo/complejo` |
| `listar_canchas` | `GET /api/catalogo/canchas` |
| `ver_disponibilidad(fecha, idCancha?)` | `GET /api/disponibilidad` (solo turnos DISPONIBLE; precio con recargo de feriado; valida hoy … hoy + diasVisibles) |
| `consultar_dni(dni)` | `GET /api/documentos/dni/{dni}` (404 o 503 → pide los nombres al cliente) |
| `crear_reserva(idsTurno[], cliente, metodoPago, comprobante?, clienteConfirmo)` | `POST /api/reservas` (acepta términos y privacidad; exige `clienteConfirmo = true`) |
| `consultar_reserva(codigo, dni)` | `GET /api/reservas/{codigo}?dni=` |
| `link_pago_tarjeta(codigo, dni, tipoPago?)` | `POST /api/pagos/pasarela/intentos` → `FRONT_URL/pasarela/ALP-…` |
| `registrar_comprobante(codigo, dni, metodo, codigoOperacion, idImagen)` | `POST /api/pagos/comprobante` (multipart con la foto guardada) + reglas + `validar-ia` |
| `validar_pago_ia(idPago)` | `POST /interno/pagos/{id}/validar-ia` (reintento; usa la lectura guardada) |
| `cancelar_reserva(codigo, dni, motivo, clienteConfirmo)` | `POST /api/reservas/{codigo}/cancelar` (exige la confirmación explícita, tras explicar la política del adelanto) |
| `reprogramar_reserva(codigo, dni, fecha, idsTurno[], clienteConfirmo)` | `clienteConfirmo=false`: vista previa (antes/después, nuevo total, pagado, comprobante en revisión, saldo; sin pagos, el adelanto nuevo) con `GET /api/reservas/{codigo}` + `GET /api/disponibilidad` y las reglas del back (`reglasReprogramar.ts`): si `!puedeReprogramar` explica el motivo y no hay vista previa; mismo horario, monto menor y adelanto insuficiente con el mensaje del back; avisa que es su único cambio. `true`: las mismas reglas y `POST ${RESERVAS_URL}/api/reservas/{codigo}/reprogramar {dni, idsTurno}` DIRECTO (no por el gateway) con `X-Interno-Token` y `X-Canchita-Conversacion: c<id>` → actor «Canchita (IA)»; 409/422 → `{error: mensaje del back, codigo, siguientePaso}`; 404 → «aún no disponible»; 5xx/sin respuesta → vuelve a consultar la reserva: con esos turnos, `reprogramada: true`; si no, «no pude hacer el cambio» y ofrecer reintentar. Un reintento de un cambio ya guardado (mismos turnos, `reprogramaciones > 0`, `!puedeReprogramar`) → `{reprogramada: true, yaEstabaAplicado: true}` en vez de «Ya cambiaste el horario una vez». Sin pagos, el plazo es `min(venceEn, nuevo inicio)` como el back, con el día si no es hoy |
| `mis_reservas(dni)` (solo WhatsApp) | `GET /interno/reservas/por-cliente?dni=&telefono=` en reservas-service; el teléfono sale de la conversación (51XXXXXXXXX), nunca del modelo (404 → «aún no disponible») |
Contratos de las dos últimas en `docs/SPEC_IA.md` §7. Las llamadas al gateway llevan `X-Interno-Token` y
`X-Canchita-Conversacion: c<id>`: el limitador del gateway da un cupo por conversación (sin eso, todos los clientes de
Canchita compartirían el cupo de su única IP). El gateway borra el token antes de reenviar al servicio; por eso el cambio de
horario va directo a reservas-service (con el token llega y la bitácora registra «Canchita (IA) · conversación cN»; por el
gateway quedaba como «Cliente»).

## Reglas de auto-validación (`reglasPago.ts`)
Solo si el interruptor **«Valida pagos automáticamente»** (`ia_valida_pagos`) está encendido y TODO calza:
- la imagen es un comprobante, sin marca EJEMPLO/MUESTRA ni señales de edición;
- monto = adelanto **o** total pendiente (total − pagado);
- número de operación leído = el declarado (pagos ya rechaza una operación repetida: 409 `OPERACION_REPETIDA`);
- destinatario = titular de Yape (todas las palabras leídas calzan con el titular, aceptando recortes con `*`) **o** el número
  visible (completo o sus últimos 3 dígitos) = Yape o Plin del complejo (`/api/catalogo/complejo`);
- app leída (Yape/Plin) = método declarado (si se reconoce la app);
- fecha de hoy o de las últimas 24 h (15 min de tolerancia hacia el futuro).
Entonces llama a `POST /interno/pagos/{id}/validar-ia {lectura, validadoPor: "Canchita (IA)"}` (pagos valida como siempre →
reserva CONFIRMADA → constancia y boleta/factura por las notificaciones de siempre). Si algo no calza, o pagos no responde, el pago
queda **PENDIENTE** y Canchita dice que el equipo lo revisa. El veredicto y sus motivos quedan en `imagen.validacion`.
**El modelo nunca decide la validación**: `validar_pago_ia` solo recibe el `idPago` y usa la lectura guardada de la foto.

## WhatsApp
- Webhook `POST /webhook/evolution/{ASISTENTE_WEBHOOK_TOKEN}` (red `sirac_default`, alias `alcanfores-asistente`; el gateway no
  lo enruta), eventos `MESSAGES_UPSERT` y `SEND_MESSAGE`.
- El **sincronizador** revisa el interruptor cada 30 s (y al abrir el estado del panel): encendido → `POST /webhook/set/{instancia}`
  con `enabled: true` y la URL `ASISTENTE_URL_INTERNA/webhook/evolution/…`; apagado → `enabled: false`.
- Se ignoran grupos (`@g.us`), estados (`status@broadcast`), canales, reacciones y tipos sin texto; los ids repetidos (reintentos de
  Evolution) se descartan por `mensaje.id_externo`. Soporta remitentes `@lid` con su número alterno.
- Mensaje propio (`fromMe`): Evolution 2.3.x crea Baileys con `emitOwnEvents: false` (verificado en
  `whatsapp.baileys.service.ts` del tag 2.3.7): lo que se ENVÍA por su API (avisos de notificaciones-service y respuestas
  de Canchita) NO llega como `MESSAGES_UPSERT`, solo como `SEND_MESSAGE`. Lo que el equipo escribe desde el celular,
  WhatsApp Web o Desktop SÍ llega como `MESSAGES_UPSERT` con `fromMe`. El campo `source` no sirve para separarlos (la API y
  WhatsApp Web generan ids `3EB0…` y ambos salen «web»). Regla: todo `fromMe` en `MESSAGES_UPSERT` es del EQUIPO → se
  guarda, lo pendiente del cliente se da por atendido y la conversación se **pausa 2 h** (`IA_PAUSA_EQUIPO_HORAS`), salvo
  que su id esté registrado como enviado por la API (evento `SEND_MESSAGE`, al que también se suscribe el webhook, o la
  respuesta de `sendText` de Canchita, guardada en `mensaje.id_externo`).
- Responde con `sendText` (3 reintentos), marca «escribiendo…» con `sendPresence`, descarga la foto con
  `getBase64FromMediaMessage` y la valida (JPG/PNG por bytes mágicos, ≤ 5 MB). Los botones `OPCIONES` no se mandan por WhatsApp.

## Tablas (`alcanfores_asistente`, migraciones SQL al arrancar; válidas en MariaDB 10.4 y MySQL 8.4)
- `conversacion(id, canal WHATSAPP|WEB, clave [51XXXXXXXXX o id de sesión] UQ con canal, nombre, estado ACTIVA|PAUSADA,
  pausada_hasta [NULL = indefinida], codigo_reserva, dni, contexto JSON [comprobante, método, celular, correo], creada_en, ultima_actividad)`
- `mensaje(id, id_conversacion FK, rol CLIENTE|CANCHITA|EQUIPO, texto, adjunto [imagen:N], id_externo UQ [id de WhatsApp],
  latencia_ms, extras JSON [opciones, enlace], atendido, creado_en)`
- `imagen(id, id_conversacion FK, mime, datos MEDIUMBLOB, lectura JSON, id_pago, validacion TEXT [veredicto], creado_en)`
- `migracion(version, descripcion, aplicada_en)`

## Endpoints
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/asistente/web/estado` | público | `{activa, nombre, avatar}` (el front decide si muestra el botón) |
| POST | `/api/asistente/web/mensaje` | público | `{sesion (16–64 [A-Za-z0-9_-]), texto? (≤ 1000), imagen? (base64 JPG/PNG ≤ 5 MB)}`. Con `Accept: text/event-stream` → SSE: `escribiendo`, `herramienta {nombre, texto}`, `mensaje {texto, opciones?, enlace?}`, `fin {latenciaMs}` (`error` si falla); si no → JSON `{mensajes:[…], latenciaMs}`. 400 `VALIDACION` con errores por campo; 503 `CANCHITA_APAGADA` si `ia_web_activa` es false. Conversación pausada → texto fijo «te atiende una persona del equipo». |
| GET | `/api/asistente/web/historial?sesion=` | público | últimos 50 mensajes `{id, rol, texto, imagen, opciones?, enlace?, creadoEn}` + `codigoReserva` |
| GET | `/api/asistente/admin/estado` | ADMIN, RECEPCION | `{credencial:{estado CONECTADA|FALTA, tipo TOKEN_SUSCRIPCION|API_KEY|SESION_LOCAL|FALTA}, modelo, esfuerzo, interruptores:{whatsapp, web, validaPagos}, whatsapp:{evolutionConfigurado, webhookActivo, ultimoError, revisadoEn}, conversaciones:{activas, pausadas, hoy}, latenciaPromedioMs}` |
| GET | `/api/asistente/admin/conversaciones?limite=20` | ADMIN, RECEPCION | `{contenido:[{id, canal, cliente, clave, estado, pausadaHasta, codigoReserva, ultimaActividad, ultimoMensaje:{rol, texto, creadoEn}}]}` |
| POST | `/api/asistente/admin/conversaciones/{id}/pausar` | ADMIN, RECEPCION | cuerpo opcional `{horas: 1–72}`; sin horas = hasta reanudar |
| POST | `/api/asistente/admin/conversaciones/{id}/reanudar` | ADMIN, RECEPCION | |
| POST | `/webhook/evolution/{token}` | red interna | 401 con token incorrecto; responde `{recibido:true}` al instante y procesa en segundo plano |
| GET | `/actuator/health` | — | `{status:"UP"}` |
Errores con el cuerpo estándar del sistema (`timestamp, status, codigo, mensaje, ruta, errores`). JWT HS256 con `JWT_SECRETO`
(el mismo de auth-service); token CLIENTE → 403 `SIN_PERMISO`; sin token o inválido → 401 `NO_AUTENTICADO`.

## Configuración (variables de entorno)
| Variable | Por defecto (local) |
|---|---|
| `PORT` | 8096 |
| `DB_HOST`, `DB_PORT`, `DB_USUARIO`, `DB_CLAVE`, `DB_NOMBRE` | localhost, 3306, root, vacío, alcanfores_asistente |
| `API_URL` (gateway) | http://localhost:8086 |
| `CATALOGO_URL`, `RESERVAS_URL`, `PAGOS_URL` (internos) | http://localhost:8092, http://localhost:8093, http://localhost:8094 |
| `INTERNO_TOKEN`, `JWT_SECRETO`, `FRONT_URL` | los de desarrollo de `comun` |
| `CLAUDE_CODE_OAUTH_TOKEN` / `ANTHROPIC_API_KEY` | vacías → texto fijo con el portal y el WhatsApp del equipo |
| `IA_MODELO`, `IA_ESFUERZO` | claude-opus-5-5, low |
| `IA_USAR_SESION_LOCAL` | false (true solo para probar en una PC con Claude Code iniciado) |
| `IA_HISTORIAL`, `IA_MAX_TURNOS`, `IA_TIEMPO_MAXIMO_MS`, `IA_PAUSA_EQUIPO_HORAS` | 20, 10, 60000, 2 |
| `EVOLUTION_URL`, `EVOLUTION_APIKEY`, `EVOLUTION_INSTANCIA` | vacías (sin WhatsApp), alcanfores |
| `ASISTENTE_WEBHOOK_TOKEN`, `ASISTENTE_URL_INTERNA` | derivado de `INTERNO_TOKEN` (sha-256), http://alcanfores-asistente:8096 |
| `WHATSAPP_HUMANO` | 914995187 |
Interruptores (catálogo `V6__asistente_ia.sql`, leídos de `GET /interno/configuracion` cada 30 s): `ia_whatsapp_activa`
(false), `ia_web_activa` (true, también público en `GET /api/catalogo/complejo` → `asistente.webActiva`), `ia_valida_pagos`
(true). Si una clave falta se usa el valor por defecto; si catálogo no responde se mantiene lo último conocido.

## Despliegue
- `deploy/docker-compose.yml`, servicio `asistente-service`: build `../asistente`, redes `interna` y `sirac_default` (alias
  `alcanfores-asistente` para el webhook de Evolution), 512 MB, healthcheck con `node -e fetch(/actuator/health)`, depende de
  `mysql` y `api-gateway` sanos. El gateway recibe `ASISTENTE_URL=http://asistente-service:8096`.
- `deploy/desplegar.sh` crea la base `alcanfores_asistente` y el usuario `asistente_svc` si faltan (CREATE … IF NOT EXISTS +
  ALTER USER con la clave de `ASISTENTE_DB_CLAVE`; si la variable no está, la genera y la agrega al `.env` con respaldo). Los
  scripts `deploy/mysql/*` también la incluyen para instalaciones nuevas. El rollback vuelve a levantar la versión anterior
  completa (si no tenía a Canchita, la quita con `--remove-orphans`; la base queda).
- Credencial: `deploy/poner-llave-ia.sh` (lee el token por la entrada estándar sin mostrarlo, acepta `sk-ant-oat…` →
  `CLAUDE_CODE_OAUTH_TOKEN` o `sk-ant-api…` → `ANTHROPIC_API_KEY`, respaldo `.env.antes-token`, recrea solo
  `asistente-service`). Desde Windows: `deploy/conectar-canchita.ps1` (corre `claude setup-token` y envía el token por SSH).
- CI: job «Canchita · lint y pruebas (vitest)» (Node 22, `npm ci`, `npm run lint`, `npm test`, sin MySQL); el despliegue
  depende de él.

## Pruebas
`npm test` (vitest, 65 pruebas sin base de datos ni modelo: **cerebro simulado** que decide las herramientas con un guion, API de
Alcanfores en memoria y WhatsApp falso) + 1 del repositorio MySQL real con `PRUEBAS_MYSQL=1` (crea `alcanfores_asistente_test`).
- `reserva.test.ts`: flujo completo de 3 pasos (2 turnos, DNI, boleta, Yape), reserva sin confirmación rechazada, SSE, validación
  del cuerpo, sin credencial (texto fijo), modelo caído (disculpa sin dejar mensajes colgados).
- `comprobante.test.ts`: foto que calza → `validar-ia`; que no calza (EJEMPLO + monto) → PENDIENTE; interruptor apagado; pagos
  caído y reintento con `validar_pago_ia`; la foto se lee una sola vez; reglas unitarias (titular recortado, últimos 3 dígitos,
  fecha vieja o futura, operación distinta, app cambiada).
- `whatsapp.test.ts`: respuesta por `sendText` con «escribiendo», id repetido, eco de Canchita y avisos enviados por la API (`SEND_MESSAGE`) ignorados, mensaje del equipo desde WhatsApp Web que pausa, **pausa 2 h por
  mensaje del equipo**, interruptor apagado, foto descargada de Evolution, token del webhook, grupos/estados, sincronizador.
- `gestion.test.ts`: `mis_reservas` encuentra las reservas del DNI hechas con el mismo celular (y la recuerda si es una),
  desde otro número no devuelve nada aunque el modelo invente el teléfono, no existe en la web, 404 → «aún no disponible»;
  prompt de WhatsApp con la política de devolución; `reprogramar_reserva` con vista previa (antes, después, pagado y saldo)
  y solo con confirmación, turnos ocupados / de otra cancha / reserva ajena rechazados, 404 → «aún no disponible»;
  `cancelar_reserva` exige confirmación.
- `reprogramar.test.ts`: `reprogramarReserva` por HTTP (fetch simulado) va a `RESERVAS_URL` con `X-Interno-Token` y
  `X-Canchita-Conversacion: c<id>` (sin conversación, solo el token) y las lecturas siguen por el gateway; 422/409 del back
  llegan al modelo con su mensaje y 404 → «aún no disponible»; vista previa: cambio válido con aviso de único cambio, sin
  pagos (adelanto nuevo y plazo), comprobante en revisión; `!puedeReprogramar` por cada motivo (ya cambió, < 2 h, empezó,
  asistencia, cancelada, genérico); monto menor (también sumando lo que está en revisión); adelanto insuficiente (también
  solo con comprobante en revisión); redondeo HALF_UP (33.35 × 30 % = 10.01); mismo horario; reservas sin la bandera;
  plazo para pagar con un comprobante OBSERVADO y un nuevo horario que empieza antes de `venceEn` (vence al nuevo inicio,
  igual en la vista previa y tras confirmar); **cambio guardado sin respuesta** (503 por `TimeoutError` en el fetch o en la
  `ApiFalsa` con `perderRespuestaReprogramar`): se informa hecho y el reintento dice `yaEstabaAplicado`, no «ya cambiaste
  una vez»; 503 sin cambio guardado → «sigue con su horario de antes» + reintentar; consulta caída → «no pude confirmar»;
  movida desde el panel o cancelada → no es «ya estaba hecho»; reglas puras (`cambioYaAplicado`, `plazoTrasCambio`) con
  sus límites exactos; prompt con las reglas (una vez, 2 h, sin prometer diferencias).
- `seguridad.test.ts`: **prompt injection** (reglas en el sistema, texto aislado, solo 10 herramientas, `validar_pago_ia` sin lectura
  del modelo; aunque el modelo obedeciera al atacante no puede validar pagos ni usar fotos de otra conversación ni reservar sin
  confirmación; otra reserva solo con código + DNI), interruptor web, panel 401/403/estado/lista/pausar/reanudar.
- `ajustes.test.ts`: lee `ia_whatsapp_activa`, `ia_web_activa` e `ia_valida_pagos` reales de catálogo (V6), usa los valores
  por defecto si faltan y conserva lo último conocido si catálogo no responde.
- `repositorioMysql.test.ts`: migraciones, conversaciones, mensajes, imágenes, conteos y latencia sobre MariaDB 10.4.

Prueba real con la suscripción (19-nov-2026): `docs/muestras/canchita-reserva-web.md` y `docs/muestras/canchita-seguridad-y-consulta.md`.
Latencia medida por respuesta: 4,0–7,6 s en los turnos normales (≈ 1 s es el arranque del proceso del SDK), 13,2 s cuando consulta
RENIEC (Factiliza tarda ~11 s en decir «no encontrado») y 11,0 s con foto (lectura con visión ≈ 4,8 s + turno).

## Decisiones
- La validación del pago es **código**, no criterio del modelo; la foto se lee aparte y el modelo solo recibe la lectura como texto.
  Por eso `validar_pago_ia(idPago)` no recibe la lectura del modelo (el SPEC decía `validar_pago_ia(idPago, lectura)`).
- `registrar_comprobante` aplica las reglas y valida en el mismo paso (un viaje menos); `validar_pago_ia` queda para reintentar.
- Sin reanudar sesiones del SDK: historial de MySQL (20 mensajes) + estado. Más simple y no depende del disco del contenedor.
- Las fotos se guardan en la BD (`MEDIUMBLOB`): no hace falta un volumen para el servicio.
- La pausa por mensaje del equipo NO depende de `source` (WhatsApp Web y la API se ven iguales): todo `fromMe` que llega como
  `MESSAGES_UPSERT` es del equipo, y los ids enviados por la API se registran con `SEND_MESSAGE` (ver WhatsApp).
