# Alcanfores · Versión 2 (pedido de Luis, 19-nov-2026) — contrato de cambios

Complementa `docs/SPEC.md` (sigue vigente en lo que no se cambie aquí). Referencia funcional: el sistema de reservas de
**Taste Perú** (`C:\xampp\htdocs\TASTE-PERU\frontend` y `backend`): tours en tarjetas con portada; ficha con 3 apartados
(Datos = editor VISUAL: se ve la página pública real y al hacer clic en cada parte se edita en un panel a la derecha ·
Calendario y precios · Configuración); feriados globales; franjas como plantilla que genera la disponibilidad.

## 0. Regla de oro (Luis): «equilibrar funcionalidad con simpleza»
- Cada pantalla tiene UNA acción principal clara; lo avanzado va plegado en «Más opciones».
- Valores por defecto que funcionen sin tocar nada (horario 8:00 a. m.–11:00 p. m., turnos de 1 h, feriado abierto con S/ 10).
- Formularios cortos, textos de una línea, íconos + ilustraciones; nada de jerga técnica para el administrador.
- En el back-end: lo mínimo que cumpla el contrato, sin abstracciones ni opciones que nadie usa. Si algo de este documento complica
  más de lo que aporta, simplifícalo y anótalo en la entrega.

## A. Horario propio de cada cancha (catalogo-service)
- `cancha.duracion_turno_min` (30–240, múltiplo de 15; por defecto 60) y `cancha.inicio_noche` (HH:mm; por defecto la
  clave global `inicio_noche`, 18:00): un turno que EMPIEZA a esa hora o después cobra `precio_noche`.
- Tabla `cancha_horario(id_cancha, dia_semana 1..7 [1 = lunes, ISO], abierto BOOLEAN, apertura TIME, cierre TIME, PK(id_cancha, dia_semana))`.
  `cierre` = hora en que TERMINA el último turno. Validaciones: si abierto, `cierre > apertura` y `(cierre − apertura)` divisible
  entre la duración (mensaje: «Con turnos de 2 h, de 8:00 a. m. a 11:00 p. m. sobra 1 hora: ajusta el cierre a 10:00 p. m. o a 12:00 a. m.»; se permite cierre 24:00 guardado como 23:59:59 → se muestra «12:00 a. m.»).
- Feriados por cancha: `cancha.feriado_modo` ENUM('ABIERTA','CERRADA') y `cancha.recargo_feriado` DECIMAL(8,2) ≥ 0
  (adicional en soles POR TURNO que se suma al precio cuando la cancha abre en feriado).
- Semilla: canchas 1–3 lunes a domingo 08:00–23:00, turnos de 60 min; losa 08:00–22:00, 60 min; feriado ABIERTA con recargo S/ 10.00.
- Endpoints (ADMIN): `GET/PUT /api/admin/canchas/{id}/horario` →
  `{duracionTurnoMin, inicioNoche, dias:[{diaSemana, abierto, apertura, cierre}], feriado:{modo, recargo}}`;
  `GET /api/admin/canchas/{id}/horario/vista-previa?dia=1..7` → turnos que saldrían `[{horaInicio, horaFin, precio, franja}]` (sin guardar; acepta el cuerpo del PUT por POST `.../vista-previa`).
- Público: el detalle de cancha agrega `horario:{duracionTurnoMin, dias:[...], texto:"Lun a vie 8:00 a. m.–11:00 p. m. · Sáb y dom 7:00 a. m.–11:00 p. m."}` y `feriado:{modo, recargo}`.
- `cancha.actualizada` agrega `duracionTurnoMin, inicioNoche, dias, feriadoModo, recargoFeriado` (record nuevo o ampliado en `comun`).

## B. Calendario: feriados y días no laborables (catalogo-service)
- Tabla `dia_especial(id, fecha UNIQUE, tipo ENUM('FERIADO','NO_LABORABLE'), nombre VARCHAR(120), canchas JSON NULL [ids; NULL = todas; solo NO_LABORABLE], creado_en, actualizado_en)`.
- Semilla + endpoint `POST /api/admin/calendario/feriados-peru?anio=` que carga los feriados nacionales oficiales de Perú del año
  (verifica la lista: 1 ene; Jueves y Viernes Santo; 1 may; 7 jun Batalla de Arica; 29 jun; 23 jul Fuerza Aérea; 28 y 29 jul;
  6 ago Junín; 30 ago; 8 oct; 1 nov; 8 dic; 9 dic Ayacucho; 25 dic) sin duplicar. Semilla para 2026 y 2027.
- Regla: **FERIADO** → cada cancha según su `feriado_modo` (CERRADA → turnos BLOQUEADOS con motivo «Feriado: {nombre}»;
  ABIERTA → precio + `recargo_feriado`, turno marcado `feriado`). **NO_LABORABLE** → turnos BLOQUEADOS con el motivo en las
  canchas indicadas (o todas). Si ese día ya hay reservas vigentes en esas canchas → 409 `DIA_CON_RESERVAS` con la lista de códigos.
- Endpoints (ADMIN): `GET /api/admin/calendario?anio=`, `POST /api/admin/calendario {fecha, tipo, nombre, canchas?}`,
  `PUT/DELETE /api/admin/calendario/{id}`, `POST /api/admin/calendario/feriados-peru?anio=`.
  Público: `GET /api/catalogo/calendario?desde=&hasta=` → `[{fecha, tipo, nombre, canchas}]` (para pintar el calendario del portal).
- Evento nuevo `calendario.actualizado` (record en `comun`): `{idEvento, ocurridoEn, fecha, tipo, nombre, canchas, eliminado}` → reservas reevalúa ese día.

## C. Turnos automáticos (reservas-service)
- SIN botón «Generar turnos»: la tarea diaria (00:05) y el arranque generan hasta hoy + `dias_generados` (clave nueva, 120 por defecto)
  usando el horario de CADA cancha; una fecha pedida dentro de `diasVisibles` que aún no tenga turnos se genera al vuelo.
- Al consumir `cancha.actualizada`: regenera los turnos FUTUROS de esa cancha — borra los libres que ya no calzan, crea los nuevos,
  reprecia; los turnos con reserva vigente se conservan (aunque queden fuera del nuevo horario) y los bloqueos MANUALES se respetan.
- Al consumir `calendario.actualizado`: aplica o quita el bloqueo/recargo de ese día (sin tocar turnos con reserva ni bloqueos manuales).
- Turno nuevo: columnas `feriado BOOLEAN`, `recargo DECIMAL(8,2)`, `tipo_bloqueo ENUM('MANUAL','FERIADO','NO_LABORABLE','CANCHA_INACTIVA') NULL`.
- Bloqueo por rango (agenda): `POST /api/admin/turnos/bloquear-rango {idsCancha:[...], fecha, fechaHasta?, horaDesde, horaHasta, motivo}`
  → bloquea los turnos libres que se crucen con el rango; responde `{bloqueados, omitidosConReserva:[codigos]}`. Igual `desbloquear-rango`.
- El endpoint `POST /api/admin/turnos/generar` puede quedar para soporte, pero el panel ya no lo muestra.

## D. Reserva de varios turnos (reservas, pagos, notificaciones, comun)
- `POST /api/reservas` y la reserva manual aceptan `idsTurno: [..]` (1 a 8 turnos de la MISMA cancha y la MISMA fecha; pueden no ser
  seguidos). `idTurno` (uno) se sigue aceptando. Total = suma de precios (con recargo de feriado); adelanto = total × adelantoPct.
- Modelo: tabla `reserva_turno(id_reserva, id_turno, precio, turno_activo INT GENERATED [id_turno si la reserva está PENDIENTE o CONFIRMADA] STORED UNIQUE, PK(id_reserva, id_turno))`
  — mantiene la garantía de no doble reserva por turno. Migración que copia `reserva.id_turno` existente. Bloquear los turnos con
  `SELECT … FOR UPDATE` ordenados por id (evita interbloqueos); prueba de concurrencia con dos reservas que comparten un turno.
- Respuestas de reserva (cliente y admin): `turnos:[{idTurno, horaInicio, horaFin, precio}]`, `horaInicio` (del primero), `horaFin`
  (del último) y `horarioTexto` («7:00–9:00 p. m.» si son seguidos; «9:00–10:00 a. m. y 7:00–8:00 p. m.» si no).
- `comun.DatosReserva` agrega al final `String horarioTexto` y `int cantidadTurnos` (actualizar TODOS los servicios y pruebas).
- Pagos: la descripción del comprobante usa `horarioTexto` y «(2 turnos)». Notificaciones: textos con `horarioTexto`; el recordatorio
  se programa 2 h antes del PRIMER turno. Cancelar libera todos los turnos. La agenda muestra la reserva en todas sus celdas.

## E. Cuentas de clientes (auth, reservas, catalogo, gateway, comun)
- Reservar SIN cuenta sigue funcionando igual. Con cuenta, el cliente tiene su portal **«Mis reservas»**: TODAS las reservas de su
  DNI, incluidas las que hizo antes de registrarse (el DNI es el vínculo).
- auth-service: tabla `cuenta_cliente(id, dni CHAR(8) UNIQUE, nombres, apellidos, correo UNIQUE, telefono CHAR(9), contrasena_hash,
  estado, intentos_fallidos, bloqueado_hasta, creado_en, ultimo_acceso)`.
  `POST /api/auth/clientes/registro {dni, nombres, apellidos, correo, telefono, contrasena, confirmacion, aceptaTerminos, aceptaPrivacidad}`
  → 201 con sesión iniciada (mismo cuerpo que el login); 409 `DNI_REGISTRADO` / `CORREO_REGISTRADO`.
  `POST /api/auth/clientes/login {usuario (DNI o correo), contrasena}` → `{token, tipo, expiraEn, cliente:{id, dni, nombres, apellidos, correo, telefono}}`
  (mismo bloqueo por intentos que los administradores). `GET/PUT /api/auth/clientes/yo`, `POST /api/auth/clientes/cambiar-contrasena`.
- JWT: rol `CLIENTE`, claim `dni`. `comun.UsuarioToken` agrega `String dni` (nulo para administradores); `JwtServicio` lo emite/lee.
  Los endpoints `/api/admin/**` siguen exigiendo ADMIN/RECEPCION (un token CLIENTE recibe 403).
- reservas-service: `GET /api/cliente/reservas?estado&page&size` y `GET /api/cliente/reservas/{codigo}` (rol CLIENTE; filtran por el DNI
  del token; cada ítem trae `puedeCancelar`, `puedeReenviarComprobante`, `puedeResenar`, `resenada`). Si `POST /api/reservas` llega con
  token CLIENTE, el DNI se toma del token (no se puede reservar a nombre de otro DNI) y los datos vacíos se completan con la cuenta.
- Reseñas (catalogo-service): tabla `resena` agrega `origen ENUM('CLIENTE','COMPLEJO')`, `id_cuenta_cliente` NULL y `codigo_reserva` NULL
  (para las del complejo). **Clientes**: `POST /api/catalogo/canchas/{id}/resenas {codigoReserva, puntuacion, comentario}` exige token
  CLIENTE; la elegibilidad usa el DNI del token (reserva de esa cancha ya jugada). **Complejo (ADMIN)**: `GET /api/admin/canchas/{id}/resenas`
  (todas, incluidas ocultas), `POST /api/admin/canchas/{id}/resenas {autor, puntuacion, comentario, fecha}`, `PUT /api/admin/resenas/{id}`,
  `PATCH /api/admin/resenas/{id} {estado}`, `POST /api/admin/resenas/{id}/respuesta`, `DELETE /api/admin/resenas/{id}` (solo origen COMPLEJO).
- Gateway: `/api/cliente/**` → reservas-service, valida JWT (401 sin token); límites: registro 5/min y login de clientes 10/min por IP.

## F. Front (panel y portal) — lo construye el agente de front
- **Barra lateral** rediseñada: sin el gran espacio vacío; grupos claros, ítem activo marcado, pie con la tarjeta del usuario (avatar,
  nombre, rol) y «Plegar menú» integrado; versión plegada solo con íconos y tooltips. Menú SIN «Reseñas» (ahora vive en cada cancha).
- **Configuración** = página de TARJETAS con ilustración (estilo B del prototipo) y una línea de texto, cada una abre su pantalla:
  Datos del complejo · Contacto y pagos · Reglas de reserva · Feriados y días no laborables (calendario anual: feriados de Perú
  precargados, agregar día no laborable con motivo y canchas, ver qué canchas abren/cierran) · Comprobantes (emisor ficticio) · WhatsApp (QR).
- **Canchas** como en Taste Perú: listado en TARJETAS con portada, estado, precios, horario resumido y calificación (editar · activar/desactivar).
  **Ficha de la cancha** con 3 apartados:
  1. **Vista y contenido** — se pinta el detalle PÚBLICO real de la cancha (el mismo componente del portal) y al hacer clic en cada parte
     (nombre, galería, descripción, características, precios, reseñas) se abre su panel a la derecha para editarla; fotos con subir/ordenar/
     principal/leyenda; **reseñas** de la cancha: crear (las del complejo), editar, ocultar/publicar, responder.
  2. **Horario y precios** — lunes a domingo con interruptor abierto/cerrado, apertura y cierre, duración del turno (30/60/90/120 min),
     precios día/noche e inicio de la noche, «copiar a todos los días», vista previa de los turnos que saldrán; **feriados**: cierra o abre
     con adicional (S/ por turno).
  3. **Configuración** — deporte, capacidad, superficie, medidas, techada/LED, orden, estado.
- **Campana de avisos** (barra superior): el número = comprobantes por validar; al hacer clic abre un MODAL «Te falta revisar estos
  comprobantes» con la lista (cliente, cancha, horario, monto, método, hace cuánto) y botón para ir a validar cada uno.
- **Agenda**: sin «Generar turnos»; «Bloquear rango» con varias canchas, desde–hasta (hora) y opcionalmente varios días; desbloquear rango;
  muestra feriados/no laborables y reservas de varios turnos.
- **Portal · Reservar**: en el paso 1 el cliente marca VARIOS turnos (chips seleccionables) y el resumen suma el total y el adelanto;
  el calendario marca feriados (con recargo) y días cerrados. Reserva manual del panel igual (varios turnos).
- **Portal · Cuenta**: «Crear cuenta» (DNI con RENIEC, celular, correo, contraseña, términos) e «Iniciar sesión»; «Mi cuenta» con
  **Mis reservas** (historial completo por DNI, filtros, detalle, cancelar, reenviar comprobante, constancia/comprobantes), **Mis reseñas**
  (calificar reservas jugadas) y datos personales. Si el cliente reserva con sesión iniciada, el paso 2 se llena con su cuenta.
  La reseña desde la web exige iniciar sesión.
