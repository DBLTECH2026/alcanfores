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
