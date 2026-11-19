# SPEC v2 · cambios de contrato y avisos del back-end

Lo escribe el agente del back-end para el agente del front (19-nov-2026). Complementa `docs/SPEC_V2.md`.

## Reinicios de los servicios locales
- 00:10 — reinicio de TODOS los servicios con la MISMA versión (v1): ahora corren desde una copia en `C:\tmp\alcanfores-run`
  para poder compilar mientras están arriba. Corte de ~2 min.
- 00:52 — reinicio de TODOS los servicios con la **versión 2** (Flyway migra las BD: catálogo V4, reservas V4, auth V2).
  Corte de ~3 min.
- 01:00 — reinicio de TODOS los servicios (v2 + arreglo en reservas: si catálogo arranca después, reservas lo vuelve a
  leer cada 30 s y al recibir un evento; antes quedaba hasta 5 min sin aplicar horarios ni feriados). Corte de ~3 min.

## Contratos que precisa o cambia el back-end (respecto a SPEC_V2)
Todo lo que no aparece aquí queda EXACTAMENTE como dice `SPEC_V2.md`.

### Generales
- Horas en JSON como `"HH:mm"`. El cierre a medianoche se envía y se recibe como `"23:59"` o `"24:00"` (el back guarda
  23:59:59 y lo devuelve `"24:00"`); se muestra «12:00 a. m.».
- Las listas de errores siguen el formato de siempre (`errores:[{campo, mensaje}]`).

### A · Horario por cancha (catalogo-service)
- `GET /api/admin/canchas` y `GET /api/admin/canchas/{id}` (y el detalle público) traen `horario:{duracionTurnoMin, inicioNoche,
  dias:[{diaSemana, abierto, apertura, cierre}], texto}` y `feriado:{modo, recargo}`. Público: sin `inicioNoche` no cambia nada
  (puede venir igual).
- `PUT /api/admin/canchas/{id}/horario` responde lo mismo que el `GET` (ya guardado). Los 7 días son obligatorios (1..7, sin repetir).
- `GET .../horario/vista-previa?dia=N` y `POST .../horario/vista-previa?dia=N` (cuerpo del PUT) →
  `[{horaInicio, horaFin, precio, franja}]` (`precio` sin recargo de feriado).

### B · Calendario (catalogo-service)
- Ítem: `{id, fecha, tipo, nombre, canchas}` (`canchas` = null → todas). `GET /api/admin/calendario?anio=` (por defecto el año actual)
  ordenado por fecha; el público `GET /api/catalogo/calendario?desde&hasta` (máx. 366 días; por defecto hoy … hoy + 60).
- `POST/PUT` validan: `fecha` obligatoria, `tipo` FERIADO | NO_LABORABLE, `nombre` 3–120 sin enlaces; `canchas` solo en
  NO_LABORABLE (ids existentes; vacío o null = todas). Fecha repetida → 409 `DIA_ESPECIAL_DUPLICADO` (campo `fecha`).
  Con reservas vigentes ese día en las canchas que se cerrarían → 409 `DIA_CON_RESERVAS` con `errores:[{campo:"fecha",
  mensaje:"ALC-XXXXXX, ALC-YYYYYY"}]` y el mensaje «Ese día ya hay reservas en esas canchas: ALC-…. Cancélalas o cámbialas antes.».
  En un FERIADO solo cuentan las canchas con `feriado.modo = CERRADA`.
- `POST /api/admin/calendario/feriados-peru?anio=` → `{creados, existentes}` (no duplica; si la fecha ya tiene un día especial, lo respeta).
- `DELETE /api/admin/calendario/{id}` → 204.
- Clave de configuración nueva `dias_generados` (120; 14–365 y ≥ `dias_visibles`) en `GET/PUT /api/admin/configuracion`.

### C · Turnos (reservas-service)
- `GET /api/disponibilidad`: cada turno agrega `feriado` (bool) y `recargo` (número; `precio` YA incluye el recargo).
  Un turno bloqueado por feriado / no laborable sale `BLOQUEADO`.
- `GET /api/admin/turnos` (agenda): cada turno agrega `feriado`, `recargo` y `tipoBloqueo` (MANUAL | FERIADO | NO_LABORABLE |
  CANCHA_INACTIVA | null); una reserva de varios turnos aparece en cada una de sus celdas con el mismo `codigoReserva`.
- `POST /api/admin/turnos/bloquear-rango` `{idsCancha:[1,2], fecha, fechaHasta?, horaDesde, horaHasta, motivo}` (se sigue aceptando
  `idCancha` suelto) → `{bloqueados, omitidosConReserva:[códigos]}`. Hasta 31 días; `horaHasta > horaDesde`; motivo 3–120.
- `POST /api/admin/turnos/desbloquear-rango` `{idsCancha, fecha, fechaHasta?, horaDesde, horaHasta}` → `{desbloqueados}`
  (solo quita bloqueos MANUALES; los de feriado / no laborable se quitan desde el calendario).

### D · Varios turnos (reservas, pagos, notificaciones)
- `POST /api/reservas` y `POST /api/admin/reservas`: `idsTurno:[..]` (o `idTurno`). Errores 400 `VALIDACION` en el campo `idsTurno`:
  «Elige entre 1 y 8 turnos.», «No repitas el mismo turno.», «Los turnos deben ser de la misma cancha y del mismo día.».
  Si uno ya no está libre → 409 `TURNO_OCUPADO` (como antes).
- Las respuestas de reserva (registro, consulta del cliente, panel, listado del panel, «Mis reservas») agregan `turnos:[{idTurno,
  horaInicio, horaFin, precio}]`, `horarioTexto` y `cantidadTurnos`; `horaInicio` = primer turno y `horaFin` = último.

### E · Cuentas de clientes
- Con token CLIENTE en `POST /api/reservas`, el `cliente.dni` del cuerpo se REEMPLAZA por el del token; los demás datos del cliente
  siguen siendo obligatorios en el cuerpo (el portal los llena con la cuenta). Simplificación: así no hace falta otra consulta
  entre servicios ni relajar la validación.
- `POST /api/auth/clientes/registro` → 201 `{token, tipo, expiraEn, cliente:{id, dni, nombres, apellidos, correo, telefono}}`.
  Validación como el SPEC §3 (DNI, nombres, apellidos, celular, correo, contraseña segura + confirmación, términos y privacidad).
  409 `DNI_REGISTRADO` (campo `dni`) / `CORREO_REGISTRADO` (campo `correo`).
- `POST /api/auth/clientes/login` `{usuario, contrasena}`: 401 `CREDENCIALES_INVALIDAS`, 423 `USUARIO_BLOQUEADO` (5 intentos → 15 min).
- `GET /api/auth/clientes/yo` → `cliente`; `PUT /api/auth/clientes/yo {nombres, apellidos, correo, telefono}` (el DNI no se cambia)
  → `cliente`; `POST /api/auth/clientes/cambiar-contrasena {actual, nueva, confirmacion}` → igual que el de administradores.
  Un token de administrador en `/api/auth/clientes/yo` → 403; un token CLIENTE en `/api/auth/yo` o en `/api/admin/**` → 403.
- `GET /api/cliente/reservas?estado&page&size` (orden: fecha y hora más recientes primero) → página de
  `{codigoReserva, estado, estadoPago, asistencia, origen, cancha:{id, nombre, deporte}, fecha, horaInicio, horaFin, horarioTexto,
  cantidadTurnos, turnos, montoTotal, montoAdelanto, montoPagado, saldo, venceEn, fechaRegistro, puedeCancelar,
  puedeReenviarComprobante, puedeResenar, resenada}`. `GET /api/cliente/reservas/{codigo}` → el mismo cuerpo que
  `GET /api/reservas/{codigo}?dni=` + `resenada` (404 si no es de su DNI). Para cancelar o reenviar el comprobante se usan los
  endpoints públicos de siempre con el DNI de la cuenta.
- Reseñas: `POST /api/catalogo/canchas/{id}/resenas {codigoReserva, puntuacion, comentario}` con token CLIENTE (sin token → 401
  «Inicia sesión para calificar la cancha.»; token de administrador → 403). El ítem público agrega `origen` (CLIENTE | COMPLEJO);
  `verificada` = origen CLIENTE.
- Reseñas del complejo (ADMIN): `GET /api/admin/canchas/{id}/resenas` → lista (no paginada) con `{id, autor, puntuacion, comentario,
  fecha, estado, respuesta, respondidoEn, origen, codigoReserva}`. `POST` `{autor, puntuacion, comentario, fecha}` (autor 2–60 letras,
  espacios y puntos; fecha ≤ hoy) → 201. `PUT /api/admin/resenas/{id}` (mismo cuerpo) y `DELETE` solo para origen COMPLEJO;
  con una reseña de cliente → 422 `RESENA_DE_CLIENTE` («Las reseñas de clientes no se editan ni se borran; puedes ocultarlas o
  responderlas.»). `PATCH {estado}` y `POST .../respuesta` sirven para ambas.

### Contratos internos (solo entre servicios; el front no los usa)
- catalogo `GET /interno/canchas[/{id}]` agrega `duracionTurnoMin, inicioNoche, dias, feriadoModo, recargoFeriado`.
- catalogo `GET /interno/calendario?desde&hasta` → `[{id, fecha, tipo, nombre, canchas}]`.
- reservas `GET /interno/reservas/vigentes?fecha=YYYY-MM-DD[&idsCancha=1,2]` → `{codigos:[...]}` (PENDIENTE o CONFIRMADA con algún
  turno esa fecha en esas canchas; sin `idsCancha` = todas).
- reservas `GET /interno/reservas/{codigo}` agrega `horarioTexto`, `cantidadTurnos`, `turnos`.
- Evento `calendario.actualizado` (`CalendarioActualizadoEvento` en comun) y `cancha.actualizada` ampliado (horario, ver comun).

## Precisiones al implementar (lo que el front debe saber)
- **Hora de fin a medianoche**: catálogo (horario de la cancha) devuelve el cierre `"24:00"`; reservas (turnos y reservas)
  devuelve la `horaFin` del último turno como `"23:59"`. Ambas se muestran «12:00 a. m.». Las entradas aceptan `"24:00"` y `"23:59"`.
- **Textos de horario** (`horarioTexto`, `horario.texto`, comprobantes, avisos): 12 h y sin repetir el sufijo si coincide
  («7:00–8:00 p. m.», «11:00 a. m.–1:00 p. m.», «7:00 a. m.–12:00 a. m.»). Un turno suelto también sale así.
- **Reseñas (panel)**: el ítem de `GET /api/admin/resenas` y de `GET /api/admin/canchas/{id}/resenas` trae además `idCancha`, `cancha`
  y `creadoEn`. `GET/PUT .../horario` incluye `texto`.
- **`feriados-peru`** no revisa reservas (es una carga masiva); los turnos con reserva se conservan siempre.
- **Nombre del día especial**: sin enlaces ni `<` `>`; se permite escribirlo en mayúsculas.
- **Desbloquear un turno suelto** que cerró el calendario (feriado o no laborable) → 422 `BLOQUEO_DE_CALENDARIO`
  («se quita desde Feriados y días no laborables»). Los bloqueos MANUALES se quitan como siempre.
- **`idsTurno`**: si además hay otros campos inválidos, el error de `idsTurno` sale en la siguiente respuesta (cuando lo demás ya es válido).
- **Login de clientes**: 401 `CREDENCIALES_INVALIDAS` con «DNI, correo o contraseña incorrectos.»; cuenta desactivada → 403 `USUARIO_INACTIVO`.
  El correo con espacios al inicio o al final → 400 (igual que en la reserva).
- **Turnos al arrancar**: mientras reservas no haya leído canchas y calendario de catálogo, solo crea turnos (no cambia ni borra).

## Recuperar contraseña y correo (19-nov-2026, back-end)

Todo pasa por el gateway. Errores con el formato de siempre (`{status, codigo, mensaje, errores:[{campo, mensaje}]}`).

### F · ¿Olvidaste tu contraseña? (auth-service, PÚBLICO, clientes del portal y usuarios del panel)
Límite del gateway: **5 llamadas por minuto por IP** en `/api/auth/recuperar/**` (429 `DEMASIADAS_SOLICITUDES` con
`Retry-After`). Tiempo máximo de respuesta: 20 s (el código sale en el momento por WhatsApp o correo).

1. `POST /api/auth/recuperar/opciones` `{tipoCuenta: "CLIENTE"|"ADMIN", usuario}` (cliente: DNI o correo; panel: usuario
   o correo; sin distinguir mayúsculas) → `{canales:[{canal:"CORREO", destino:"l****@gmail.com"},
   {canal:"WHATSAPP", destino:"*** *** 254"}]}`. WhatsApp solo si la cuenta tiene celular.
   404 `CUENTA_NO_ENCONTRADA` «No encontramos una cuenta con esos datos.» (también si está desactivada).
2. `POST /api/auth/recuperar/enviar` `{tipoCuenta, usuario, canal}` → `{canal, destino (enmascarado), expiraEn,
   reenviarEn, reenviarEnSegundos: 60, simulado}` (+ `codigoPrueba` SOLO si el servidor corre con
   `RECUPERAR_DEVOLVER_CODIGO=true`; nunca en producción).
   - `simulado: true` → el canal aún no está configurado (sin `MAIL_*`/correo del panel o sin Evolution): avisar
     «El correo/WhatsApp del complejo aún no está configurado; el código no llegará».
   - El código: 6 dígitos, vence a los 10 min, 5 intentos; solo vale el ÚLTIMO enviado.
   - 422 `CANAL_NO_DISPONIBLE` (WhatsApp sin celular) · 429 `DEMASIADOS_ENVIOS`: «Espera N segundos para pedir otro
     código.» (antes de 60 s) o «Ya te enviamos 3 códigos en los últimos 15 minutos. Intenta de nuevo en N minutos.» ·
     503 `ENVIO_FALLIDO` «No pudimos enviar el código por WhatsApp/correo. Prueba con el otro medio…» ·
     503 `SERVICIO_NO_DISPONIBLE` (notificaciones caído) · 404 `CUENTA_NO_ENCONTRADA`.
3. `POST /api/auth/recuperar/verificar` `{tipoCuenta, usuario, codigo}` → `{tokenRestablecer, expiraEn}` (permiso de un
   solo uso, 10 min). 422 `CODIGO_INCORRECTO` «El código no es correcto. Te quedan 4 intentos.» (también en
   `errores[{campo:"codigo"}]`), 422 `CODIGO_VENCIDO` «El código venció o ya no es válido. Pide uno nuevo.»,
   422 `DEMASIADOS_INTENTOS` (al 5.º error el código se anula). 400 si `codigo` no son 6 dígitos.
4. `POST /api/auth/recuperar/restablecer` `{tokenRestablecer, nueva, confirmacion}` → `{mensaje}`. Mismas reglas de
   contraseña de siempre (400 en `nueva`/`confirmacion`). Desbloquea la cuenta, anula códigos y permisos, registra en la
   bitácora (`RECUPERAR_CONTRASENA`) y avisa «Tu contraseña se cambió» por el mismo canal. 422 `TOKEN_INVALIDO`
   «El permiso para cambiar la contraseña venció o ya se usó. Vuelve a pedir un código.». No inicia sesión: el front
   manda al login.

### G · Celular de los usuarios del panel (auth-service)
- `GET /api/auth/yo`, el login (`usuario`) y `GET/POST/PUT /api/admin/usuarios` traen `telefono` (9 dígitos o `null`).
- `POST` y `PUT /api/admin/usuarios` aceptan `telefono` opcional (`@Celular`; vacío o `null` = sin celular).
- `PUT /api/auth/yo` `{telefono, correo}` (ADMIN o RECEPCION) → el usuario (`UsuarioRespuesta`). 400 por campo,
  409 `CORREO_DUPLICADO`. Así el usuario del panel completa su celular para recibir el código por WhatsApp.

### H · Correo configurable desde el panel (notificaciones-service, ADMIN; RECEPCION → 403)
- `GET /api/admin/correo` → `{configurado, origen: "PANEL"|"VARIABLES"|"NINGUNO", host, puerto, usuario, remitente,
  nombreRemitente, seguridad: "SSL"|"STARTTLS", tieneClave, actualizadoEn, actualizadoPor}`. La contraseña NUNCA viaja.
- `PUT /api/admin/correo` `{host, puerto, usuario, clave, remitente, nombreRemitente, seguridad}` → lo mismo que el GET.
  `clave` vacía o ausente = conservar la guardada (la primera vez es obligatoria: 400 en `clave`). `nombreRemitente`
  vacío = «Complejo Deportivo Los Alcanfores». Validación: `host` sin `http://` ni espacios, `puerto` 1–65535, `usuario`
  y `remitente` correos, `seguridad` obligatoria. Hostinger: `smtp.hostinger.com`, 465 + SSL o 587 + STARTTLS; usuario =
  correo completo del buzón y remitente = el mismo correo.
- `POST /api/admin/correo/prueba` `{destino}` → 200 `{enviado: true, mensaje}` o 422 `CORREO_NO_ENVIADO` con la causa
  explicada (usuario o contraseña, servidor no encontrado, puerto, SSL/STARTTLS, remitente). Tarda hasta ~20 s.
- Prioridad del envío: configuración del panel → variables `MAIL_*` → SIMULADO. Se aplica al instante al guardar.

### I · Avisos de WhatsApp ilustrados (notificaciones-service)
- Cada aviso sale como IMAGEN (tarjeta 1200×800) con el texto como leyenda y después cada PDF como documento con una
  leyenda breve («Constancia ALC-…», «Boleta B001-…», «Factura F001-…», «Nota de crédito BC01-…»). Si la imagen falla,
  sale solo el texto. El correo lleva la misma tarjeta como cabecera.
- Bandeja del panel: `adjuntos` incluye la tarjeta como `{tipo:"IMAGEN", referencia:"reserva-confirmada",
  nombreArchivo:"reserva-confirmada.jpg", estado}` (siempre la PRIMERA); `NO_DISPONIBLE` si no se pudo enviar la imagen.
  El detalle (`GET /api/admin/notificaciones/{id}`) agrega `imagenTarjeta` (data URI JPG, o `null`) y la `vistaPrevia`
  del correo ya trae la tarjeta incrustada.
- Eventos nuevos en la bandeja: `pago.validado` («Saldo pagado»: ¡Terminaste de pagar tu cancha!, solo con
  `tipoPago = SALDO`), `cuenta.codigo` («Código para cambiar la contraseña», el código se guarda como `******`; no es
  reintentable: `reintentable:false` y 422 `NO_REINTENTABLE`), `cuenta.contrasena` («Contraseña cambiada») y
  `correo.prueba`. Filtro `evento` admite esos valores.

### J · Varios recordatorios y asistencia configurable
- Configuración (`GET/PUT /api/admin/configuracion`): la clave `recordatorio_horas` se REEMPLAZA por
  `recordatorio_minutos` = texto «120,15» (1 a 3 valores de 5 a 1440, sin repetir; se guarda ordenado de mayor a menor).
  Clave nueva `asistencia_desde_min` (0–180, por defecto 30). Errores 400 en el campo de la clave.
- `GET /api/catalogo/complejo` → `reglas.recordatorioMinutos: [120, 15]`, `reglas.asistenciaDesdeMin: 30` y
  `reglas.recordatorioHoras` (derivado del mayor, por compatibilidad).
- `GET /api/admin/recordatorios?estado&codigo&page&size&sort` → cada fila agrega `minutosAntes`; una reserva tiene una
  fila por cada tiempo. `sort` admite además `minutosAntes`. Un cambio de la lista aplica a las reservas que se confirmen
  desde ese momento. Un recordatorio se omite solo si su hora pasó hace más de 10 min.
- Reservas (panel): `GET /api/admin/reservas` y `/{codigo}` traen `puedeMarcarAsistencia`; la asistencia se marca desde
  `asistencia_desde_min` minutos antes del inicio (422 `ASISTENCIA_ANTICIPADA` con los minutos vigentes).

### Contratos internos nuevos (solo entre servicios)
- notificaciones `POST /interno/codigos` `{canal: CORREO|WHATSAPP, destino (correo o celular de 9 dígitos), nombre,
  codigo (6 dígitos; obligatorio con RECUPERAR_CONTRASENA), motivo: RECUPERAR_CONTRASENA|CONTRASENA_CAMBIADA}` →
  `{estado: ENVIADO|SIMULADO|FALLIDO, simulado, detalle}` (síncrono).
- notificaciones consume `pago.validado` (cola `notificaciones.pago-validado`).
- catálogo `/interno/configuracion` trae `recordatorio_minutos` y `asistencia_desde_min` (ya no `recordatorio_horas`).

### Pendientes para el front
- Pantalla «¿Olvidaste tu contraseña?» en el login del portal y del panel (pasos F1–F4, contador de 60 s, aviso si
  `simulado`).
- Panel: «Mis datos» (celular), campo celular en Usuarios, pantalla «Correo» (H) y la configuración con
  `recordatorio_minutos` y `asistencia_desde_min` (el front aún usa `recordatorio_horas` en `esquemasPanel.ts`).
- Bandeja: mostrar `imagenTarjeta` y los adjuntos `IMAGEN`; recordatorios con `minutosAntes`.

## Reprogramar y reservas por cliente (19-nov-2026, back-end)

Pedido de Luis: cambiar la fecha, la hora y/o la cancha de una reserva **conservando lo pagado**, desde el panel, el portal
y Canchita (WhatsApp); y que Canchita reconozca al cliente por su DNI cuando escribe desde el celular registrado.
Errores con el formato de siempre. Detalle técnico en `docs/servicios/reservas-service.md` y `notificaciones-service.md`.

### K · Cambiar el horario de una reserva (reservas-service)
- **Cliente / Canchita** (público): `POST /api/reservas/{codigo}/reprogramar` `{dni, idsTurno:[..]}` → 200 con el MISMO
  cuerpo que `GET /api/reservas/{codigo}?dni=`. 404 si código + DNI no calzan (mismo mensaje de siempre); 400 en `codigo`,
  `dni` o `idsTurno` (mismos mensajes que al crear); 409 `TURNO_OCUPADO` o `TURNO_NO_DISPONIBLE`; 422:

  | Código | Mensaje |
  |---|---|
  | `RESERVA_NO_REPROGRAMABLE` | «Esta reserva está cancelada; ya no se puede cambiar el horario.» / «Ya se registró la asistencia de esta reserva; ya no se puede cambiar el horario.» / «El partido de esta reserva ya empezó; ya no se puede cambiar el horario.» (también el panel) |
  | `REPROGRAMACION_NO_PERMITIDA` | «Ya cambiaste el horario una vez; escríbenos para ayudarte.» / «Los cambios de horario se hacen hasta 2 horas antes del partido; escríbenos para ayudarte.» (solo cliente y Canchita) |
  | `REPROGRAMAR_MONTO_MENOR` | «El nuevo horario cuesta menos de lo que ya pagaste. Elige otro horario o acércate al complejo.» |
  | `REPROGRAMAR_ADELANTO_INSUFICIENTE` | «El nuevo horario pide un adelanto mayor del que ya pagaste. Elige otro horario o escríbenos para ayudarte.» (solo cliente y Canchita) |
  | `REPROGRAMAR_MISMO_HORARIO` | «Elegiste el mismo horario que ya tienes. Elige otro día, hora o cancha.» |

  Con `X-Interno-Token` válido (el mismo validador de `/interno/**`) y `X-Canchita-Conversacion` (`^[A-Za-z0-9_-]{1,80}$`)
  el actor es «Canchita (IA)»; un token inválido o una conversación con otros caracteres se ignoran (actor Cliente).
- **Panel** (ADMIN y RECEPCION): `POST /api/admin/reservas/{codigo}/reprogramar` (acepta también el id numérico)
  `{idsTurno:[..], motivo}` → 200 con el detalle admin. `motivo` obligatorio, 5–200, sin enlaces ni todo en mayúsculas → 400
  «Cuéntanos el motivo del cambio (5 a 200 caracteres, sin enlaces ni todo en mayúsculas).». Sin token 401, token CLIENTE 403.
- Respuestas: el detalle del cliente (`GET /api/reservas/{codigo}`, `GET /api/cliente/reservas/{codigo}`, cancelar y
  reprogramar) agrega `reprogramaciones` (cuántos cambios tuvo), `puedeReprogramar` (reglas del cliente: muestra o no el
  botón) y `montoEnRevision` (comprobante que se está validando; 0 si no hay). El detalle admin agrega `puedeReprogramar`
  (estado y que no haya empezado) y `reprogramaciones:[{id, actor: CLIENTE|CANCHITA|PANEL, usuario, motivo, creadoEn,
  anterior:{idCancha, cancha, fecha, horaInicio, horaFin, horarioTexto, idsTurno, cantidadTurnos, total}, nuevo:{…}}]` (el
  más reciente primero). La línea de tiempo suma el hito `REPROGRAMADA` («Horario cambiado por el cliente | por Canchita
  (asistente) | por el complejo: Cancha 1 · 21/11/2026 7:00–8:00 p. m. (S/ 80.00) → …»). El motivo del panel es una
  **nota interna**: queda en el historial, la bitácora y el detalle admin; NO en la línea de tiempo ni en el aviso al
  cliente (decisión por defecto, a confirmar con Luis; si quiere que el cliente lo vea, el formulario del panel debe
  avisar «el cliente verá este motivo»).

Reglas (decididas por el dueño):
- Se puede cambiar una reserva **PENDIENTE** (sin pago, en revisión u observada) o **CONFIRMADA** (adelanto o total pagado)
  **sin asistencia registrada y cuyo partido aún no empieza** (también el panel: no hay tarea que cierre las reservas
  pasadas, así que sin esta regla se podía pasar a otro día un partido ya jugado). Nunca una cancelada, asistida o «no
  asistió».
- Turnos nuevos con las MISMAS reglas que al crear: 1 a 8 sin repetir, misma cancha y fecha entre sí (puede ser otra cancha
  y otro día que los actuales), existentes y dentro del horario de la cancha, fecha hoy … hoy + `diasVisibles`, cliente y
  Canchita ≥ ahora + 30 min (panel: que no haya terminado), cancha activa, sin bloqueo (manual, feriado cerrado, no
  laborable) y libres. Los turnos que ya ocupa la propia reserva cuentan como libres (correr una hora un partido de 2 turnos).
- Cliente y Canchita: **hasta 2 horas antes** del inicio actual (la cancelación del cliente no tiene una clave de plazo,
  solo «antes del inicio», así que se usa el margen por defecto de 2 h) y **un cambio por reserva** entre los dos.
  El panel no tiene esos dos límites.
- Dinero: el total se recalcula con los precios VIGENTES de los turnos nuevos (con recargo de feriado) y el adelanto con
  `adelanto_pct`. Lo pagado se conserva. «Comprometido» = lo pagado + el monto REAL del comprobante en revisión
  (`reserva.monto_en_revision`, que llega en `pago.registrado` y se limpia al validar o rechazar; el adelanto no sirve
  porque se recalcula en cada cambio). Nuevo total < comprometido → 422 `REPROGRAMAR_MONTO_MENOR`. Cliente y Canchita: si
  ya pagó algo (o tiene un comprobante en revisión) y no cubre el adelanto del nuevo horario → 422
  `REPROGRAMAR_ADELANTO_INSUFICIENTE` (si no, quedaba CONFIRMADA con menos del adelanto bloqueando turnos caros); el panel
  sí puede (queda ADELANTO_PAGADO con el saldo a cobrar). Estado del pago: sin pagos no cambia; con pagos, PAGADO_TOTAL si
  cubre el total y ADELANTO_PAGADO si no. `venceEn` se conserva (nunca queda después del nuevo inicio).
- Al validar un pago, el estado sale SOLO de los montos (pagado ≥ total → PAGADO_TOTAL): un pago «TOTAL» calculado antes de
  un cambio a un horario más caro deja ADELANTO_PAGADO con el saldo, ya no PAGADO_TOTAL con saldo.
- Atómico: la reserva se bloquea (`FOR UPDATE`) y los turnos viejos y nuevos se bloquean en orden de id en la MISMA
  transacción; si dos reservas quieren el mismo turno, gana una y la otra recibe 409 sin cambiar nada (el índice UNIQUE de
  `reserva_turno.turno_activo` es la última garantía). Dos cambios de la misma reserva se atienden uno detrás de otro.
- Historial en la tabla nueva `reserva_reprogramacion` (Flyway `V5`). Bitácora `REPROGRAMAR_RESERVA`: panel = su usuario;
  cliente = «Cliente 7****321» (rol CLIENTE); Canchita = «Canchita (IA)» (rol SISTEMA) con «· conversación cN».
- Evento nuevo `reserva.reprogramada` (`ReservaReprogramadaEvento` en comun: la reserva DESPUÉS del cambio, `estado`,
  `saldoPendiente`, cancha, fecha, horas, horario y total ANTERIORES, `actor`, `motivo` (nota interna), `estadoPago` y
  `venceEn`) publicado tras el commit.
  No se publica `calendario.actualizado`: ese evento es de catálogo (días especiales) y reservas lo consume para regenerar
  turnos; la disponibilidad se consulta en vivo.

### L · Reservas de un cliente para Canchita (interno)
- `GET /interno/reservas/por-cliente?dni=XXXXXXXX&telefono=9XXXXXXXX` (`X-Interno-Token`) → `[{codigo, cancha, fecha,
  horaInicio, horaFin, horarioTexto, cantidadTurnos, turnos:[{idTurno, horaInicio, horaFin, precio}], estado, estadoPago,
  montoTotal, pagado, saldo}]`: reservas de ese DNI desde hoy − 30 días en adelante (todos los estados), por fecha y hora.
- Solo las reservas de ese DNI **hechas con ese celular**: cada reserva guarda su celular (`reserva.telefono_contacto`,
  V5; las anteriores toman el del cliente). Antes se comparaba con `cliente.telefono`, que es uno por DNI y lo sobrescribe
  cualquier reserva nueva (también una web anónima con un DNI ajeno): alguien podía reservar con el DNI de otra persona y
  su propio número y luego ver, cambiar o cancelar por Canchita las reservas de esa persona. El teléfono se normaliza a 9
  dígitos (se aceptan `987654321`, `51987654321`, `+51 987 654 321`). Sin coincidencias → `[]` (no se revela nada). 400
  con `@Dni` / `@Celular` (campos `dni`, `telefono`). Los avisos de cada reserva y su constancia usan también el celular
  de ESA reserva (no el último del cliente).
- `pagado` = lo cobrado y validado que reservas ya conoce por `pago.validado` (`monto_pagado`, el mismo de los listados del
  panel); `estadoPago` el de la reserva; `saldo` = total − pagado (0 si está cancelada).

### M · Aviso «¡Cambiamos tu horario!» (notificaciones-service)
- Cola `notificaciones.reserva-reprogramada` (idempotente). Correo + WhatsApp con la tarjeta nueva `reserva-reprogramada`
  (IL-02, tono gol), la constancia actualizada en PDF y el texto: código, «Antes: Viernes 20 de noviembre de 2026,
  7:00–8:00 p. m. (Cancha 1)», «Ahora»: cancha, fecha, hora, total, pagado, saldo, enlace a Mi reserva y, según el pago:
  confirmada → «Te enviaremos el recordatorio…»; comprobante en revisión → «Estamos validando tu comprobante…»; sin pago
  u observado → el adelanto nuevo y el plazo (o «en el complejo o en línea» si no hay plazo). Sin el motivo del panel
  (nota interna). En la bandeja: evento `reserva.reprogramada` («Cambio de horario»).
- Recordatorios: si la reserva está CONFIRMADA, cada tiempo de `recordatorio_minutos` vuelve a PROGRAMADO para el nuevo
  inicio (también el ya enviado del horario anterior) y se cancelan los que ya no están en la lista; si no está
  confirmada, no se crean. `reserva.confirmada` y `reserva.reprogramada` van por colas distintas: cada recordatorio guarda
  `datos_al` (Flyway `V3` de notificaciones) y un evento más viejo no lo reemplaza (una confirmación que llega tarde ya no
  lo devuelve al horario anterior). Si dos eventos crean a la vez la misma fila, el que choca se reintenta (antes se
  tomaba como duplicado y el aviso se perdía). Red de seguridad: si al enviar reservas dice que empieza a otra hora, se
  mueve a esa hora.

### Pendientes para el front y el integrador
- Portal («Mi reserva» y el detalle de «Mis reservas»; el listado no trae la bandera): botón «Cambiar horario» si
  `puedeReprogramar`; elegir día, cancha y turnos con `GET /api/disponibilidad` (los turnos propios salen OCUPADO: marcarlos
  como elegibles con `turnos[].idTurno` de la reserva); mostrar 409/422 con su `mensaje`.
- Panel (detalle de la reserva): botón «Cambiar horario» si `puedeReprogramar`, con motivo; lista `reprogramaciones`;
  icono para el hito `REPROGRAMADA` de la línea de tiempo y para el evento `reserva.reprogramada` de la bandeja.
- Si el panel muestra las tarjetas desde `frontend/public/whatsapp/`, copiar ahí `reserva-reprogramada.jpg`.
- **Canchita (asistente/), para que el actor sea «Canchita (IA)»**: el gateway borra `X-Interno-Token` en TODAS las rutas
  (`RemoveRequestHeader`, y está bien que lo haga), así que un cambio que Canchita hace por el gateway queda con actor
  «Cliente». Arreglo: en `asistente/src/api.ts`, `reprogramarReserva` debe llamar DIRECTO a
  `${cfg.reservasUrl}/api/reservas/{codigo}/reprogramar` con `X-Interno-Token` y `X-Canchita-Conversacion: c<id>` (como
  `misReservas`); reservas-service ya acepta esa ruta sin sesión. Sin tocar el gateway.
- **Canchita, vista previa de `reprogramar_reserva`** (`asistente/src/herramientas.ts`): (1) si `!actual.puedeReprogramar`,
  cortar antes de la vista previa con «Ya no se puede cambiar el horario de esta reserva por aquí; escríbenos para
  ayudarte.»; (2) `comprometido = montoPagado + montoEnRevision`; si `nuevoTotal < comprometido` devolver el mensaje de
  `REPROGRAMAR_MONTO_MENOR` y QUITAR la nota «el equipo revisa la diferencia» (no existe esa regla); (3) si
  `comprometido > 0` y `comprometido < nuevoTotal × adelantoPct / 100` (redondeado a 2 decimales) devolver el mensaje de
  `REPROGRAMAR_ADELANTO_INSUFICIENTE`. Igual, ante un 422 mostrar el `mensaje` del back.
- ~~**pagos-service (riesgo abierto)**~~ **RESUELTO (19-nov-2026, ver la línea siguiente)**: (a) los intentos de pago en línea ABIERTOS guardan el monto del momento en que se
  crearon (TOTAL = total − pagado) y `AprobacionEnLinea.aprobar` cobra ese monto sin volver a mirar la reserva: si entre
  medio se cambia a un horario más barato, el cliente paga de más (reservas lo deja en el log: «corresponde devolver la
  diferencia»; a uno más caro, reservas ya deja ADELANTO_PAGADO con saldo). (b) La instantánea de la reserva de un pago
  PENDIENTE (comprobante en revisión) conserva la cancha, fecha y hora anteriores, y la boleta que se emite al validar sale
  con el horario viejo. Arreglo propuesto: que pagos consuma `reserva.reprogramada` (cola `pagos.reserva-reprogramada`) y
  (1) dé por EXPIRADOS los intentos ABIERTOS de ese código, (2) refresque la instantánea (`concepto`, fecha, hora, cancha,
  total) de sus pagos PENDIENTE. Alternativa mínima para (a): en `aprobar`, consultar `/interno/reservas/{codigo}` y no
  aprobar si `intento.monto` supera el saldo vigente.
- **pagos-service · resuelto**: consume `reserva.reprogramada` (cola `pagos.reserva-reprogramada`, DLQ, idempotente) → intentos
  ABIERTOS EXPIRADOS con «El monto cambió porque la reserva cambió de horario; vuelve a iniciar el pago.» y pagos PENDIENTE con el concepto y % de adelanto nuevos (pagos no guarda
  el total aparte); además relee la reserva antes de cobrar en la beta (409 `INTENTO_EXPIRADO` con ese mensaje) y al aprobar
  (webhook beta/Mercado Pago y «Ya pagué»): monto > total − validado → no registra el pago, intento EXPIRADO («… Te devolveremos
  lo cobrado.») y bitácora `PAGO_EN_LINEA_NO_APLICADO` para devolverlo. Front: mostrar `motivoRechazo` en el checkout EXPIRADO y
  rotular esa acción en `textosBitacora.ts`. Detalle en `docs/servicios/pagos-service.md` («Reserva reprogramada»).
- **pagos-service · ajustes tras la revisión (19-nov-2026)**: (1) «No aplicado» es definitivo por operación del proveedor:
  si la misma operación vuelve («Ya pagué», `/pago/resultado`) no se registra aunque después el saldo alcance (la bitácora
  ya pidió devolverla). (2) El consumidor solo expira los intentos creados ANTES del cambio (`creado_en <= ocurridoEn`) que
  no tengan ya el monto y el horario nuevos: un `reserva.reprogramada` atrasado (reintentos, DLQ) ya no cierra el pago que
  el cliente (o Canchita) creó con el monto correcto. (3) Antes de cobrar, la beta exige el monto de un intento nuevo
  (adelanto vigente; total − lo validado): un adelanto viejo menor o un «Pago total» que ya no cubre el total → 409
  `INTENTO_EXPIRADO`. (4) Motivo nuevo para un cambio al MISMO precio: «Tu reserva cambió de horario; vuelve a iniciar el
  pago.» (el de «El monto cambió…» queda solo cuando el monto de verdad cambió). Para el front: `motivoRechazo` de un
  intento EXPIRADO puede traer cualquiera de los tres motivos (mostrarlo tal cual; ninguno es «venció el tiempo»).
