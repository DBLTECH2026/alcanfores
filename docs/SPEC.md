# Alcanfores · Sistema de reservas — Especificación técnica (contrato)

Proyecto UTP «Desarrollo Web Integrado» (docente Mendoza Arce). Sistema REAL y funcional de reservas del
**Complejo Deportivo Los Alcanfores** (Carretera Central km 11.5, Santa Clara, Ate · paradero Caseta, a dos cuadras
de la UTP Santa Clara · WhatsApp +51 914 995 187 · informes.alcanfores@gmail.com).
El profesor califica sobre todo **que la app funcione y que TODOS los campos estén validados** (front y back).

Este documento es el **contrato** entre servicios y con el front. Si algo no está aquí, decide lo más sensato y
anótalo en `docs/DECISIONES.md` (una línea por decisión).

---------------------------------------------------------------------------------------------------------------
## 0. Reglas generales

- Idioma: **todo en español** (código de dominio, mensajes, validaciones, UI, logs de negocio). Nombres de clases y
  métodos en español (`ReservaServicio`, `registrarReserva`) salvo convenciones de framework.
- Zona horaria **America/Lima** en todo (JVM `-Duser.timezone=America/Lima`, `spring.jackson.time-zone`, `serverTimezone`).
- Fechas `YYYY-MM-DD`, horas `HH:mm`, fecha-hora ISO-8601 sin zona (`2026-11-20T19:00:00`), dinero `BigDecimal` con
  2 decimales (JSON número `80.00`). Moneda PEN (S/).
- JSON camelCase. Paginación: `?page=0&size=20&sort=campo,desc` → respuesta
  `{ "contenido": [...], "pagina": 0, "tamanio": 20, "totalElementos": 57, "totalPaginas": 3 }`.
- Errores SIEMPRE con este cuerpo (lo arma `comun`):
  `{ "timestamp": "...", "status": 409, "codigo": "TURNO_OCUPADO", "mensaje": "Ese turno acaba de ser reservado por otra persona. Elige otro horario.", "ruta": "/api/reservas", "errores": [ { "campo": "cliente.dni", "mensaje": "El DNI debe tener 8 dígitos." } ] }`
  400 validación (`codigo: "VALIDACION"`), 401 `NO_AUTENTICADO`, 403 `SIN_PERMISO`, 404 `NO_ENCONTRADO`,
  409 conflictos de negocio, 422 reglas de negocio, 429 `DEMASIADAS_SOLICITUDES`, 503 `SERVICIO_NO_DISPONIBLE`.
- Arquitectura por capas en cada servicio: `controlador` → `servicio` (interfaz + impl) → `repositorio` (Spring Data JPA)
  → `modelo` (entidades) ; `dto` (records con Bean Validation) ; `mapeador` ; `config` ; `evento` (publicadores/consumidores) ;
  `cliente` (clientes HTTP a otros servicios). Inyección por constructor (Lombok `@RequiredArgsConstructor`). SOLID.
- Toda acción administrativa que cambie estado se audita (ver evento `auditoria.accion`).
- Secretos SOLO por variables de entorno (el repo es PÚBLICO). Nunca subir `.env`.
- Sin emojis en UI, mensajes ni código.

---------------------------------------------------------------------------------------------------------------
## 1. Arquitectura de microservicios

```
React (Vite)  ──HTTPS──>  api-gateway :8086  ──lb://──>  auth-service          :8091  BD alcanfores_auth
                               │                    ├─>  catalogo-service      :8092  BD alcanfores_catalogo
                         Eureka :8761 <── registro ─├─>  reservas-service      :8093  BD alcanfores_reservas
                                                    ├─>  pagos-service         :8094  BD alcanfores_pagos
                                                    └─>  notificaciones-service:8095  BD alcanfores_notificaciones
                     RabbitMQ (exchange topic `alcanfores.eventos`)  ← eventos de dominio entre servicios
                     Evolution API (WhatsApp, Baileys) · SMTP · Factiliza (RENIEC/SUNAT) · Mercado Pago (sandbox)
```

| Módulo Maven | Paquete base | Puerto | Responsabilidad |
|---|---|---|---|
| `comun` | `pe.alcanfores.comun` | — | Librería compartida: errores, JWT, validadores (DNI, RUC, celular…), eventos, utilidades de tiempo y dinero, auditoría. |
| `discovery-server` | `pe.alcanfores.discovery` | 8761 | Eureka Server. |
| `api-gateway` | `pe.alcanfores.gateway` | 8086 | Spring Cloud Gateway (WebFlux): rutas, CORS, JWT previo en `/api/admin/**`, bloquea `/interno/**`, límite de solicitudes, circuit breaker con fallback, `X-Request-Id`, `/api/estado`. |
| `auth-service` | `pe.alcanfores.auth` | 8091 | Administradores, login JWT, roles, bloqueo por intentos, cambio de contraseña, **bitácora** (consume `auditoria.accion`). |
| `catalogo-service` | `pe.alcanfores.catalogo` | 8092 | Canchas, fotos, características, **reseñas verificadas**, configuración del complejo (tarifas, reglas, datos de pago). |
| `reservas-service` | `pe.alcanfores.reservas` | 8093 | Clientes, turnos, disponibilidad, reservas, cancelaciones, asistencia, consulta RENIEC/SUNAT, constancia PDF, dashboard y reportes de reservas. |
| `pagos-service` | `pe.alcanfores.pagos` | 8094 | Pagos (comprobante Yape/Plin, caja, pasarela), validación, **Pasarela Alcanfores Pay (beta)**, **Mercado Pago (sandbox)**, **boletas/facturas/notas de crédito simuladas** (PDF + XML UBL), reportes de ingresos. |
| `notificaciones-service` | `pe.alcanfores.notificaciones` | 8095 | Correo (SMTP o modo simulado), **WhatsApp por Evolution API**, PDFs adjuntos, **recordatorio 2 h antes (cron)**, bitácora de envíos, conexión del WhatsApp por QR. |

- Versiones: **Java 17**, **Spring Boot 3.5.16**, **Spring Cloud 2025.0.3**, MySQL 8 (prod `mysql:8.4`; local MariaDB 10.4 de XAMPP),
  Flyway, Spring Data JPA/Hibernate, Spring Security, jjwt 0.12.x (usar `jjwt-gson` o `jjwt-jackson`), springdoc-openapi 2.8.x,
  OpenFeign o `RestClient` con `@LoadBalanced`, Resilience4j, Spring AMQP, Lombok, MapStruct opcional,
  JUnit 5 + Mockito + AssertJ, JaCoCo (meta ≥ 70 % en servicios de dominio).
- Base de datos **por servicio** (database-per-service): un mismo servidor MySQL con 5 esquemas. Ningún servicio
  lee tablas de otro: se comunican por HTTP interno o por eventos.
- Comunicación **síncrona** (consultas que necesitan respuesta inmediata) por HTTP con Eureka (`lb://reservas-service`)
  a endpoints `/interno/**`, protegidos con cabecera `X-Interno-Token: ${INTERNO_TOKEN}` (filtro de `comun`).
  El gateway NUNCA enruta `/interno/**`. Circuit breaker + timeout 3 s en los clientes.
- Comunicación **asíncrona** por RabbitMQ: exchange topic durable `alcanfores.eventos`; cada servicio declara SUS colas
  `<servicio>.<evento>` (durables) con DLQ `alcanfores.eventos.dlx`. Mensajes JSON (Jackson2JsonMessageConverter).
  Consumidores idempotentes (tabla `evento_procesado(id_evento PK, tipo, procesado_en)` en cada servicio que consuma).
- Patrón **Saga coreografiada**: la reserva nace PENDIENTE con plazo (`venceEn`); si el pago no llega, el sistema la
  cancela (compensación) y libera el turno.
- Cada servicio: Actuator (`/actuator/health`, `/actuator/info`), springdoc (`/v3/api-docs`, `/swagger-ui.html`),
  perfil `local` (MySQL localhost:3306 root sin clave) y perfil `docker`. Flyway `V1__esquema.sql`, `V2__datos_demo.sql`…
- Configuración por variables de entorno (con valores por defecto para local):
  `DB_HOST, DB_PORT, DB_USUARIO, DB_CLAVE, EUREKA_URL (http://localhost:8761/eureka), RABBIT_HOST, RABBIT_PORT,
  RABBIT_USUARIO, RABBIT_CLAVE, JWT_SECRETO (≥ 32 bytes), JWT_HORAS (8), INTERNO_TOKEN, FRONT_URL (http://localhost:5186),
  PUBLIC_API_URL (http://localhost:8086)`, más las de cada servicio (abajo).

### 1.1 Rutas del gateway (prefijo → servicio)
| Prefijo | Servicio |
|---|---|
| `/api/auth/**`, `/api/admin/usuarios/**`, `/api/admin/bitacora/**` | auth-service |
| `/api/catalogo/**`, `/api/admin/canchas/**`, `/api/admin/resenas/**`, `/api/admin/configuracion/**` | catalogo-service |
| `/api/disponibilidad/**`, `/api/reservas/**`, `/api/documentos/**`, `/api/admin/reservas/**`, `/api/admin/turnos/**`, `/api/admin/clientes/**`, `/api/admin/dashboard/**`, `/api/admin/reportes/reservas/**` | reservas-service |
| `/api/pagos/**`, `/api/pasarela/**`, `/api/webhooks/**`, `/api/comprobantes/**`, `/api/admin/pagos/**`, `/api/admin/comprobantes/**`, `/api/admin/reportes/ingresos/**` | pagos-service |
| `/api/admin/notificaciones/**`, `/api/admin/recordatorios/**`, `/api/admin/whatsapp/**` | notificaciones-service |
| `/api/estado` | gateway (consulta `/actuator/health` de cada servicio vía Eureka y devuelve `{ servicios: [{ nombre, estado: "UP|DOWN", instancias, latenciaMs }] }`) |

Límites (token bucket en memoria, por IP): `POST /api/auth/login` 10/min, `/api/documentos/**` 20/min,
`POST /api/reservas` 10/min, `/api/pasarela/**` 30/min → 429 con el cuerpo de error estándar.
Tamaño máximo de cuerpo 6 MB. CORS: `FRONT_URL` (lista separada por comas). Fallback del circuit breaker →
503 `{codigo:"SERVICIO_NO_DISPONIBLE", mensaje:"El servicio de <x> no responde. Intenta en unos segundos."}`.

---------------------------------------------------------------------------------------------------------------
## 2. Seguridad

- JWT **HS256** firmado con `JWT_SECRETO`, emitido por auth-service. Claims: `sub` (idAdministrador), `usuario`,
  `nombre`, `rol` (`ADMIN` | `RECEPCION`), `iat`, `exp` (8 h). Cabecera `Authorization: Bearer <token>`.
- El gateway valida el token en `/api/admin/**` (401 temprano) y CADA servicio lo vuelve a validar con `JwtFiltro`
  de `comun` (defensa en profundidad) → `ROLE_ADMIN` / `ROLE_RECEPCION`; `@PreAuthorize` en los métodos solo-ADMIN.
- Contraseñas BCrypt (fuerza 12). Login: 5 intentos fallidos → bloqueo 15 min (mensaje con minutos restantes).
  Contraseña nueva: 8–64 caracteres, al menos una mayúscula, una minúscula y un número.
- Matriz de permisos:
  | Función | ADMIN | RECEPCION |
  |---|---|---|
  | Dashboard, reservas (listar, ver, manual, cancelar, asistencia), agenda (ver, bloquear/desbloquear turno) | sí | sí |
  | Validar/rechazar comprobantes, cobrar saldo en caja, ver/descargar comprobantes, ver notificaciones | sí | sí |
  | Canchas y fotos, tarifas/configuración, generar turnos, reseñas (moderar), reportes, usuarios, bitácora, WhatsApp, anular comprobantes, reintentar notificaciones | sí | no |
- Archivos subidos: comprobantes JPG/PNG (verificar **bytes mágicos**, no solo la extensión), ≤ 5 MB, lado mínimo 200 px;
  fotos de cancha JPG/PNG/WebP ≤ 5 MB. Nombre en disco = UUID. Nunca servir rutas del usuario.
- Datos personales (Ley 29733): checkbox obligatorio de privacidad; DNI enmascarado en vistas públicas (`7****321`);
  nunca guardar número de tarjeta completo ni CVV (solo marca + últimos 4).

---------------------------------------------------------------------------------------------------------------
## 3. Validaciones (front con Zod y back con Bean Validation; MISMOS mensajes)

| Campo | Regla | Mensaje |
|---|---|---|
| DNI | `^\d{8}$` | «El DNI debe tener 8 dígitos.» |
| RUC | `^(10|15|16|17|20)\d{9}$` + dígito verificador módulo 11 (pesos 5,4,3,2,7,6,5,4,3,2; `d = 11 - (suma % 11)`; 10→0, 11→1) | «El RUC no es válido.» |
| Factura | RUC que empiece con 10 o 20, razón social 3–150, dirección fiscal 5–200 | «Para factura necesitas un RUC válido y la razón social.» |
| Nombres / apellidos | 2–60, `^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]+$`, sin espacios dobles; se guardan en Título | «Usa solo letras (2 a 60).» |
| Celular | `^9\d{8}$` (se acepta con espacios o +51 y se normaliza) | «Ingresa un celular peruano de 9 dígitos que empiece con 9.» |
| Correo | email RFC, ≤ 100, dominio con punto, **obligatorio** | «Ingresa un correo válido; ahí te enviamos la copia de tu reserva.» |
| Fecha de reserva | hoy … hoy + `diasVisibles` (14) | «Elige una fecha entre hoy y los próximos 14 días.» |
| Turno | existe, DISPONIBLE, empieza ≥ ahora + 30 min | «Ese turno ya no está disponible.» |
| Método de pago | `YAPE | PLIN | TARJETA | MERCADO_PAGO` (web); caja: `EFECTIVO | YAPE | PLIN | TARJETA_POS` | «Elige un método de pago.» |
| Código de operación (Yape/Plin) | `^\d{6,12}$` | «El número de operación tiene de 6 a 12 dígitos.» |
| Imagen del comprobante | JPG/PNG por bytes mágicos, ≤ 5 MB, ≥ 200 px | «Sube una imagen JPG o PNG de hasta 5 MB.» |
| Monto (caja) | > 0, ≤ saldo pendiente, 2 decimales | «El monto no puede superar el saldo de S/ X.» |
| Motivo de cancelación / observación de rechazo | 10–255 | «Cuéntanos el motivo (mínimo 10 caracteres).» |
| Reseña | puntuación 1–5 entera; comentario 10–500, sin enlaces (`http`, `www.`), sin solo mayúsculas | «Escribe entre 10 y 500 caracteres, sin enlaces.» |
| Código de reserva | `^ALC-[A-HJ-NP-Z2-9]{6}$` (sin I, O, 0, 1) | «El código tiene el formato ALC-XXXXXX.» |
| Términos y privacidad | ambos `true` | «Debes aceptar los términos y la política de privacidad.» |
| Tarjeta (pasarela) | Luhn, 13–19 dígitos, marca detectada (Visa 4, Mastercard 51–55/2221–2720, Amex 34/37, Diners 36/38); vencimiento MM/AA futuro; CVV 3 (Amex 4); titular 3–60 letras | «Revisa el número de tarjeta.» etc. |
| Usuario admin | `^[a-z][a-z0-9._]{3,29}$` | «Usuario de 4 a 30 caracteres en minúsculas.» |
| Cancha | nombre 3–60 único; capacidad 2–30; precios 10.00–999.00 y noche ≥ día; descripción 10–255; descripción larga ≤ 2000 | — |

Las anotaciones reutilizables viven en `comun`: `@Dni`, `@Ruc`, `@Celular`, `@NombrePersona`, `@CodigoReserva`,
`@SinEnlaces`, y la utilidad `Documentos.rucValido(String)`.

---------------------------------------------------------------------------------------------------------------
## 4. Eventos de dominio (RabbitMQ · exchange `alcanfores.eventos`, tipo topic)

Todos los eventos llevan `idEvento` (UUID), `ocurridoEn` (fecha-hora) y el `codigoReserva` cuando aplique.
Registros (records) en `pe.alcanfores.comun.eventos`. `ClienteContacto = { dni, nombres, apellidos, telefono, correo }`.
`DatosReserva = { idReserva, codigoReserva, idCancha, nombreCancha, deporte, fecha, horaInicio, horaFin, montoTotal, montoAdelanto, montoPagado, cliente: ClienteContacto }`.

| Routing key | Record | Lo publica | Lo consumen |
|---|---|---|---|
| `reserva.registrada` | `ReservaRegistradaEvento(idEvento, ocurridoEn, reserva: DatosReserva, origen, metodoPagoPrevisto, venceEn)` | reservas | notificaciones (aviso «reserva registrada, completa tu pago» + constancia) |
| `reserva.confirmada` | `ReservaConfirmadaEvento(idEvento, ocurridoEn, reserva: DatosReserva, saldoPendiente)` | reservas | notificaciones (confirmación + constancia PDF + programa recordatorio) |
| `reserva.cancelada` | `ReservaCanceladaEvento(idEvento, ocurridoEn, reserva: DatosReserva, canceladoPor (CLIENTE/ADMINISTRADOR/SISTEMA), motivo, devolucion, montoDevolucion)` | reservas | pagos (anula pago pendiente; si hubo cobro y `devolucion` → reembolso simulado + **nota de crédito**), notificaciones (aviso + cancela recordatorio) |
| `reserva.asistencia` | `AsistenciaRegistradaEvento(idEvento, ocurridoEn, reserva: DatosReserva, asistencia)` | reservas | notificaciones (si ASISTIO: invitación a calificar la cancha con enlace `FRONT_URL/canchas/{idCancha}?resena=ALC-XXXXXX`) |
| `pago.registrado` | `PagoRegistradoEvento(idEvento, ocurridoEn, idPago, codigoReserva, metodo, canal, monto, tipoPago)` | pagos | reservas (estadoPago=EN_REVISION, quita `venceEn`), notificaciones («recibimos tu comprobante») |
| `pago.validado` | `PagoValidadoEvento(idEvento, ocurridoEn, idPago, codigoReserva, metodo, canal, monto, tipoPago (ADELANTO/TOTAL/SALDO), validadoPor)` | pagos | reservas (confirma o suma saldo) |
| `pago.rechazado` | `PagoRechazadoEvento(idEvento, ocurridoEn, idPago, codigoReserva, observacion, rechazadoPor)` | pagos | reservas (estadoPago=OBSERVADO, nuevo plazo), notificaciones (aviso con la observación y enlace para reenviar) |
| `comprobante.emitido` | `ComprobanteEmitidoEvento(idEvento, ocurridoEn, idComprobante, codigoReserva, tipo (BOLETA/FACTURA/NOTA_CREDITO), serieNumero, total, cliente: ClienteContacto)` | pagos | notificaciones (envía el PDF por correo y WhatsApp) |
| `cancha.actualizada` | `CanchaActualizadaEvento(idEvento, ocurridoEn, idCancha, nombre, deporte, precioDia, precioNoche, estado)` | catalogo | reservas (repone precio de turnos futuros DISPONIBLES; si INACTIVA bloquea turnos futuros libres con motivo «Cancha inactiva»; si vuelve a ACTIVA los desbloquea) |
| `auditoria.accion` | `AccionAuditadaEvento(idEvento, ocurridoEn, servicio, idActor, actor, rol, accion, entidad, idEntidad, detalle, ip)` | todos | auth (tabla `bitacora`) |

---------------------------------------------------------------------------------------------------------------
## 5. Servicios: datos y endpoints

### 5.1 auth-service (BD `alcanfores_auth`)
Tablas: `administrador(id_administrador PK, nombres, apellidos, usuario UQ, correo UQ, contrasena_hash, rol ENUM('ADMIN','RECEPCION'), estado ENUM('ACTIVO','INACTIVO'), intentos_fallidos, bloqueado_hasta, ultimo_acceso, creado_en, actualizado_en)`,
`bitacora(id_bitacora PK, servicio, id_actor, actor, rol, accion, entidad, id_entidad, detalle VARCHAR(500), ip, fecha)`, `evento_procesado`.
Semilla (claves por variables `SEED_CLAVE_ADMIN`, `SEED_CLAVE_RECEPCION`, `SEED_CLAVE_DOCENTE`; por defecto local
`Alcanfores2026`, `Recepcion2026`, `UTP2026`): `rhuaman` Rosa Huamán ADMIN · `mtorres` Marco Torres RECEPCION · `docente` Docente UTP ADMIN.
Las claves se cargan con un `CommandLineRunner` (BCrypt) si la tabla está vacía; nunca en el SQL.

| Método | Ruta | Acceso | Cuerpo → Respuesta |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{usuario, contrasena}` → `{token, tipo:"Bearer", expiraEn, usuario:{id, nombres, apellidos, usuario, correo, rol}}` · 401 `CREDENCIALES_INVALIDAS` · 423 `USUARIO_BLOQUEADO` |
| GET | `/api/auth/yo` | token | usuario actual |
| POST | `/api/auth/cambiar-contrasena` | token | `{actual, nueva, confirmacion}` |
| GET/POST | `/api/admin/usuarios` | ADMIN | lista paginada (`q`, `rol`, `estado`) / crear `{nombres, apellidos, usuario, correo, rol, contrasena}` |
| PUT | `/api/admin/usuarios/{id}` | ADMIN | editar datos y rol (no puede quitarse su propio rol ADMIN ni desactivarse) |
| PATCH | `/api/admin/usuarios/{id}/estado` | ADMIN | `{estado}` |
| POST | `/api/admin/usuarios/{id}/restablecer-contrasena` | ADMIN | `{nueva}` |
| GET | `/api/admin/bitacora` | ADMIN | `?servicio&actor&accion&desde&hasta&page&size` |

### 5.2 catalogo-service (BD `alcanfores_catalogo`)
Tablas: `cancha(id_cancha PK, slug UQ, nombre UQ, deporte ENUM('FUTBOL_5','FUTBOL_6','FUTBOL_7','VOLEY','BASQUET','MULTIUSO'), superficie, capacidad, descripcion VARCHAR(255), descripcion_larga TEXT, techada, iluminacion_led, medidas VARCHAR(40), caracteristicas JSON (lista de textos), precio_dia, precio_noche, estado ENUM('ACTIVA','INACTIVA'), orden, creado_en, actualizado_en)`,
`cancha_foto(id_foto PK, id_cancha FK, archivo, leyenda, orden, principal, ancho, alto, creado_en)`,
`resena(id_resena PK, id_cancha FK, codigo_reserva UQ, autor VARCHAR(60) (p. ej. «Diego R.»), puntuacion TINYINT, comentario VARCHAR(500), estado ENUM('PUBLICADA','OCULTA'), respuesta VARCHAR(500), respondido_en, creado_en)`,
`configuracion(clave PK, valor, descripcion)` (`adelanto_pct`=50, `devolucion_horas`=24, `primer_turno`=08:00,
`ultimo_turno`=23:00 (inicio del último turno = 22:00), `duracion_min`=60, `inicio_noche`=18:00, `dias_visibles`=14,
`minutos_para_pagar`=20, `recordatorio_horas`=2, `yape_numero`, `yape_titular`, `plin_numero`, `whatsapp`, `correo`,
`direccion`, `referencia`, `mapa_url`, `latitud`, `longitud`, `nombre_complejo`, `lema`, `ruc_emisor` (ficticio), `razon_social_emisor` (ficticia)), `evento_procesado`.
Semilla (datos reales del complejo; precios ficticios): **Cancha 1 · Fútbol 5** (techada, grass sintético, LED, 10 jug., S/ 60 día / 80 noche) ·
**Cancha 2 · Fútbol 6** (techada, 12 jug., S/ 70/90) · **Cancha 3 · Fútbol 7** (techada, 14 jug., S/ 90/120) ·
**Losa multiusos** (MULTIUSO vóley y básquet, losa de concreto, 12 jug., S/ 40/50). Características comunes: camerinos,
estacionamiento, iluminación LED, explanada para eventos. 3–4 fotos por cancha (archivos en `uploads/canchas/`,
copiados por la semilla desde `src/main/resources/semilla/fotos/`). 4–6 reseñas de ejemplo por cancha (autores ficticios).

| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/catalogo/complejo` | público | datos públicos del complejo + reglas: `{nombre, lema, direccion, referencia, whatsapp, correo, mapaUrl, latitud, longitud, horario:{primerTurno, ultimoTurno, duracionMin, inicioNoche, diasVisibles}, reglas:{adelantoPct, devolucionHoras, minutosParaPagar, recordatorioHoras}, pagos:{yapeNumero, yapeTitular, plinNumero}}` |
| GET | `/api/catalogo/canchas` | público | activas: `[{id, slug, nombre, deporte, deporteTexto, superficie, capacidad, techada, iluminacionLed, descripcion, precioDia, precioNoche, fotoPrincipal (URL), calificacion:{promedio, total}}]` |
| GET | `/api/catalogo/canchas/{idOSlug}` | público | lo anterior + `descripcionLarga, medidas, caracteristicas[], fotos:[{id, url, leyenda, principal}], resumenResenas:{promedio, total, distribucion:{"5":n,…,"1":n}}` |
| GET | `/api/catalogo/canchas/{id}/resenas` | público | paginado, `orden=recientes|mejor|peor`: `[{id, autor, puntuacion, comentario, fecha, respuesta, verificada:true}]` |
| POST | `/api/catalogo/canchas/{id}/resenas` | público | `{codigoReserva, dni, puntuacion, comentario}` → verifica con reservas `GET /interno/reservas/{codigo}/elegible-resena?dni&idCancha` (elegible si la reserva es de esa cancha, el DNI coincide, está CONFIRMADA y su hora de inicio ya pasó o tiene asistencia ASISTIO). Una reseña por reserva (409 `RESENA_DUPLICADA`). Autor = nombre + inicial del apellido. |
| GET | `/api/catalogo/fotos/{archivo}` | público | sirve la imagen (Cache-Control 7 días; nombre UUID) |
| GET/POST/PUT | `/api/admin/canchas`, `/api/admin/canchas/{id}` | ADMIN | CRUD (incluye inactivas). Publica `cancha.actualizada`. |
| PATCH | `/api/admin/canchas/{id}/estado` | ADMIN | `{estado}` |
| POST | `/api/admin/canchas/{id}/fotos` | ADMIN | multipart `archivo`, `leyenda` |
| PATCH/DELETE | `/api/admin/canchas/{id}/fotos/{idFoto}` | ADMIN | `{leyenda, orden, principal}` / borrar |
| GET | `/api/admin/resenas` | ADMIN | `?estado&idCancha&puntuacion&page` |
| PATCH | `/api/admin/resenas/{id}` | ADMIN | `{estado}` (ocultar/publicar) |
| POST | `/api/admin/resenas/{id}/respuesta` | ADMIN | `{respuesta}` (10–500) |
| GET/PUT | `/api/admin/configuracion` | GET token, PUT ADMIN | todas las claves `{clave: valor}` validadas |
| GET | `/interno/canchas`, `/interno/canchas/{id}`, `/interno/configuracion` | interno | para reservas y pagos |

### 5.3 reservas-service (BD `alcanfores_reservas`)
Tablas (respetan el diccionario del informe y lo amplían):
- `cliente(id_cliente PK, dni CHAR(8) UQ, nombres, apellidos, telefono CHAR(9), correo, verificado_reniec, fecha_registro, actualizado_en)`
- `turno(id_turno PK, id_cancha, nombre_cancha, deporte, fecha, hora_inicio, hora_fin, precio, franja ENUM('DIA','NOCHE'), estado ENUM('DISPONIBLE','RESERVADO','BLOQUEADO'), motivo_bloqueo, UQ(id_cancha, fecha, hora_inicio))`
- `reserva(id_reserva PK, codigo_reserva VARCHAR(12) UQ, id_cliente FK, id_turno FK, estado ENUM('PENDIENTE','CONFIRMADA','CANCELADA'), estado_pago ENUM('SIN_PAGO','EN_REVISION','OBSERVADO','ADELANTO_PAGADO','PAGADO_TOTAL'), asistencia ENUM('PENDIENTE','ASISTIO','NO_ASISTIO'), origen ENUM('WEB','PRESENCIAL','TELEFONICA'), metodo_pago_previsto, monto_total, monto_adelanto, monto_pagado, vence_en DATETIME NULL, fecha_registro, confirmada_en, registrado_por (id admin, NULL si web), nota VARCHAR(255), turno_activo INT GENERATED (id_turno si estado IN ('PENDIENTE','CONFIRMADA'), si no NULL) STORED UQ)`
  ← la columna generada + UQ es la garantía de **no doble reserva** (RNF-11); además `SELECT … FOR UPDATE` del turno en `@Transactional`.
- `cancelacion(id_cancelacion PK, id_reserva FK UQ, id_administrador NULL, cancelado_por ENUM('CLIENTE','ADMINISTRADOR','SISTEMA'), motivo, devolucion BOOLEAN, monto_devolucion, fecha_cancelacion)`
- `consulta_documento(tipo ENUM('DNI','RUC'), numero, respuesta JSON, fuente, consultado_en, PK(tipo, numero))` (caché 30 días)
- `evento_procesado`.
Código de reserva: `ALC-` + 6 caracteres de `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (SecureRandom; reintenta si existe).
Montos: `monto_total` = precio del turno; `monto_adelanto` = total × adelantoPct / 100 (redondeo a 0.50 hacia arriba no; HALF_UP a 2 decimales).
Semilla: clientes y reservas ficticias de los últimos 45 días y próximos 7 (para que el dashboard, la agenda y los reportes tengan datos; incluye canceladas, no-show y pendientes por validar), turnos generados para hoy + 14 días.

Públicos:
| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/disponibilidad?fecha=YYYY-MM-DD[&idCancha=]` | `{fecha, canchas:[{idCancha, nombre, deporte, turnos:[{idTurno, horaInicio, horaFin, precio, franja, estado:"DISPONIBLE|OCUPADO|BLOQUEADO|PASADO"}]}]}` (OCUPADO si tiene reserva vigente; PASADO si empieza en < 30 min) |
| GET | `/api/disponibilidad/calendario?desde=&dias=14[&idCancha=]` | `[{fecha, libres, total}]` para pintar el calendario |
| POST | `/api/reservas` | `{idTurno, cliente:{dni, nombres, apellidos, telefono, correo}, metodoPago, aceptaTerminos, aceptaPrivacidad}` → 201 `{codigoReserva, estado, estadoPago, venceEn, cancha:{id, nombre, deporte}, fecha, horaInicio, horaFin, montoTotal, montoAdelanto, cliente:{nombres, apellidos, dniEnmascarado, telefono, correo}}`. Transacción: upsert del cliente por DNI (actualiza teléfono/correo), bloquea el turno, crea la reserva PENDIENTE/SIN_PAGO con `venceEn = ahora + minutosParaPagar`, publica `reserva.registrada` (tras commit: `@TransactionalEventListener(AFTER_COMMIT)`). 409 `TURNO_OCUPADO` si choca. |
| GET | `/api/reservas/{codigo}?dni=` | detalle para el cliente: lo anterior + `asistencia, montoPagado, saldo, cancelacion:{canceladoPor, motivo, fecha, devolucion, montoDevolucion}, linea:[{evento, fecha, detalle}]` (línea de tiempo), `puedeCancelar`, `puedeReenviarComprobante`, `puedeResenar`. 404 si código+DNI no calzan (mismo mensaje para ambos casos). |
| POST | `/api/reservas/{codigo}/cancelar` | `{dni, motivo}` → reglas: solo PENDIENTE/CONFIRMADA y antes del inicio; `devolucion = true` si faltan ≥ `devolucionHoras` y hubo pago; publica `reserva.cancelada`. |
| GET | `/api/reservas/{codigo}/constancia.pdf?dni=` | **Constancia de reserva PDF** (logo, código grande, QR con el código, cancha, fecha, hora, montos, estado, condiciones, dirección y mapa). |
| GET | `/api/documentos/dni/{dni}` | `{dni, nombres, apellidoPaterno, apellidoMaterno, apellidos, fuente:"RENIEC (Factiliza)"|"CACHE"}` · 404 `DNI_NO_ENCONTRADO` · 503 `CONSULTA_NO_DISPONIBLE` (sin token o proveedor caído → el front permite llenar a mano) |
| GET | `/api/documentos/ruc/{ruc}` | `{ruc, razonSocial, direccion, estado, condicion, fuente}` (valida dígito verificador antes de consultar) |

Proveedor de documentos: **Factiliza** si `FACTILIZA_TOKEN` existe (`GET https://api.factiliza.com/v1/dni/info/{dni}` y `/v1/ruc/info/{ruc}`, cabecera `Authorization: Bearer`; respuesta `{success, data:{…}}`; DNI: `nombres, apellido_paterno, apellido_materno`; RUC: `nombre_o_razon_social, estado, condicion, direccion_completa`). Interfaz `ProveedorDocumentos` para cambiar de proveedor. Timeout 5 s.

Administración (token):
| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/admin/dashboard?fecha=` | `{fecha, kpis:{reservasHoy, porValidar, confirmadasHoy, ocupacionHoyPct, noShowMesPct, reservasSemana}, proximas:[…5], agenda:[{idCancha, nombre, turnos:[{horaInicio, estado, codigoReserva, cliente, estadoPago}]}], alertas:[{tipo, mensaje, codigoReserva}]}` |
| GET | `/api/admin/reservas` | filtros `estado, estadoPago, fecha, desde, hasta, idCancha, origen, q` (código, DNI, nombre o celular), paginado: `[{codigoReserva, cliente:{nombres, apellidos, dni, telefono}, cancha, fecha, horaInicio, horaFin, estado, estadoPago, asistencia, origen, montoTotal, montoPagado, fechaRegistro}]` + `conteos:{PENDIENTE, CONFIRMADA, CANCELADA, EN_REVISION}` |
| GET | `/api/admin/reservas/{codigo}` | detalle completo + línea de tiempo + cancelación |
| POST | `/api/admin/reservas` | reserva manual `{idTurno, cliente{…}, origen: PRESENCIAL|TELEFONICA, nota, pagoInicial?:{tipoPago: ADELANTO|TOTAL, metodo, monto, codigoOperacion?, comprobante:{tipo, ruc?, razonSocial?, direccionFiscal?}}}`. Sin `venceEn`. Si trae `pagoInicial`, tras crearla llama a pagos `POST /interno/pagos/caja` (pago validado + comprobante → `pago.validado` → CONFIRMADA). |
| POST | `/api/admin/reservas/{codigo}/cancelar` | `{motivo, devolucion}` |
| POST | `/api/admin/reservas/{codigo}/asistencia` | `{asistencia: ASISTIO|NO_ASISTIO}` solo CONFIRMADA y desde 15 min antes del inicio; publica `reserva.asistencia` |
| GET | `/api/admin/turnos?fecha=[&idCancha=]` | agenda: canchas × turnos con reserva (código, cliente, estado, estadoPago) |
| POST | `/api/admin/turnos/generar` | ADMIN · `{desde, hasta, idsCancha?}` idempotente (no duplica) → `{creados, existentes}` |
| POST | `/api/admin/turnos/{id}/bloquear` · `/desbloquear` | `{motivo}` (no si tiene reserva vigente) |
| POST | `/api/admin/turnos/bloquear-rango` | `{idCancha, fecha, horaDesde, horaHasta, motivo}` |
| GET | `/api/admin/clientes` · `/api/admin/clientes/{dni}` | lista con `totalReservas, asistidas, noShows, ultimaReserva` · historial |
| GET | `/api/admin/reportes/reservas?desde&hasta` | ADMIN · `{totales:{registradas, confirmadas, canceladas, pendientes, asistidas, noAsistio, tasaNoShowPct, ocupacionPct}, porCancha:[{idCancha, cancha, reservas, ocupacionPct}], porDia:[{fecha, registradas, confirmadas, canceladas}], porHora:[{hora, reservas}], porOrigen:[{origen, reservas}], cancelaciones:{porActor:{…}, motivosFrecuentes:[…]}, topClientes:[…5]}` |
| GET | `/api/admin/reportes/reservas.xlsx?desde&hasta` | ADMIN · Excel (Apache POI) |

Internos: `GET /interno/reservas/{codigo}` (DatosReserva + estado, estadoPago, montoPagado, saldo, inicio), `GET /interno/reservas/{codigo}/elegible-resena?dni&idCancha` → `{elegible, motivo, autor}`, `GET /interno/reservas/{codigo}/constancia.pdf`, `GET /interno/reservas/proximas?desde&hasta` (confirmadas por empezar).
Tareas programadas (`@Scheduled`, zona Lima): generar turnos de hoy + `diasVisibles` (00:05 y al arrancar) · **expirar reservas** PENDIENTE/SIN_PAGO (u OBSERVADO) con `venceEn < ahora` cada minuto → cancelación SISTEMA «Venció el plazo para pagar» + `reserva.cancelada`.
Consumidores: `pago.registrado`, `pago.validado` (ADELANTO→ADELANTO_PAGADO, TOTAL→PAGADO_TOTAL, SALDO→suma y PAGADO_TOTAL si completa; ADELANTO/TOTAL → estado CONFIRMADA + `reserva.confirmada`), `pago.rechazado` (OBSERVADO, `venceEn = min(ahora + 12 h, inicio − 1 h)`), `cancha.actualizada`.

### 5.4 pagos-service (BD `alcanfores_pagos`)
Tablas:
- `pago(id_pago PK, codigo_reserva, id_administrador NULL, metodo ENUM('YAPE','PLIN','TARJETA','MERCADO_PAGO','EFECTIVO','TARJETA_POS'), canal ENUM('COMPROBANTE','PASARELA','CAJA'), tipo_pago ENUM('ADELANTO','TOTAL','SALDO'), monto, codigo_operacion, archivo_comprobante, estado_validacion ENUM('PENDIENTE','VALIDADO','RECHAZADO','ANULADO','REEMBOLSADO'), observacion, referencia_pasarela, marca_tarjeta, ultimos4, codigo_autorizacion, fecha_envio, fecha_validacion, validado_por VARCHAR(60))`
- `datos_facturacion(id PK, codigo_reserva, id_pago, tipo ENUM('BOLETA','FACTURA'), tipo_doc ('1' DNI | '6' RUC), num_doc, nombre, direccion)`
- `intento_pago(id PK, referencia UQ ('ALP-' + 10), codigo_reserva, proveedor ENUM('BETA','MERCADO_PAGO'), tipo_pago, monto, estado ENUM('CREADO','REQUIERE_3DS','APROBADO','RECHAZADO','EXPIRADO'), motivo_rechazo, id_externo, expira_en, creado_en, actualizado_en)`
- `webhook_evento(id PK, proveedor, id_externo UQ por proveedor, tipo, firma_valida, payload JSON, procesado, recibido_en)` (idempotencia)
- `serie(serie PK, tipo, ultimo_correlativo)` → B001 (boleta), F001 (factura), BC01 / FC01 (notas de crédito). Numeración con `@Lock(PESSIMISTIC_WRITE)`.
- `comprobante(id_comprobante PK, tipo ENUM('BOLETA','FACTURA','NOTA_CREDITO'), codigo_tipo ('03','01','07'), serie, correlativo, UQ(serie, correlativo), fecha_emision, codigo_reserva, id_pago, cliente_tipo_doc, cliente_num_doc, cliente_nombre, cliente_direccion, descripcion, op_gravada, igv, total, monto_letras, hash, cadena_qr, estado ENUM('EMITIDO','ANULADO'), estado_sunat VARCHAR (siempre «ACEPTADO (SIMULADO)»), id_referencia NULL (para NC), motivo NULL)`
- `evento_procesado`.
IGV 18 % **incluido** en el precio: `op_gravada = round(total / 1.18, 2)`, `igv = total − op_gravada`. Emisor ficticio: RUC `20612345679`
(válido por módulo 11), razón social «Los Alcanfores Sport E.I.R.L. (ficticia)», dirección real del complejo.
**Un comprobante por cada cobro validado** (adelanto, total o saldo). Boleta al DNI del cliente si no pidió factura.

Públicos:
| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/pagos/metodos` | `{yape:{numero, titular}, plin:{numero}, tarjetaBeta:true, mercadoPago:<bool si hay MP_ACCESS_TOKEN>, tarjetasPrueba:[…], yapePrueba:{codigoAprueba:"123456", codigoRechaza:"000000"}}` |
| POST | `/api/pagos/comprobante` | multipart: `codigoReserva, dni, metodo (YAPE/PLIN), codigoOperacion, archivo, comprobante (JSON: tipo, ruc?, razonSocial?, direccionFiscal?)` → valida con reservas (`/interno/reservas/{codigo}`: DNI, estado PENDIENTE, estadoPago SIN_PAGO u OBSERVADO) → pago PENDIENTE por el monto del adelanto → `pago.registrado` → 201 `{idPago, estado:"PENDIENTE", mensaje}` |
| GET | `/api/pagos/reserva/{codigo}?dni=` | pagos de la reserva para el cliente `[{metodo, canal, tipoPago, monto, estado, observacion, fecha, comprobante:{serieNumero, urlPdf}}]` |
| POST | `/api/pagos/pasarela/intentos` | `{codigoReserva, dni, tipoPago: ADELANTO|TOTAL, comprobante:{…}}` → `{referencia, urlCheckout:"/pasarela/ALP-…", expiraEn}` |
| GET | `/api/pasarela/intentos/{ref}` | `{referencia, comercio:"Complejo Deportivo Los Alcanfores", concepto, monto, moneda, estado, expiraEn, codigoReserva}` |
| POST | `/api/pasarela/intentos/{ref}/tarjeta` | `{numero, titular, mesVencimiento, anioVencimiento, cvv, correo}` → `{estado: APROBADO|RECHAZADO|REQUIERE_3DS, codigoAutorizacion?, motivo?, marca, ultimos4}` |
| POST | `/api/pasarela/intentos/{ref}/3ds` | `{codigo}` (123456 aprueba) |
| POST | `/api/pasarela/intentos/{ref}/yape` | `{celular, codigoAprobacion}` |
| GET | `/api/pagos/intentos/{ref}/estado` | sondeo desde la página de resultado |
| POST | `/api/pagos/mercadopago/preferencias` | `{codigoReserva, dni, tipoPago, comprobante}` → `{referencia, initPoint}` (sandbox_init_point si token TEST). 409 `MP_NO_CONFIGURADO` si falta token. |
| POST | `/api/webhooks/mercadopago` | verifica `x-signature` (HMAC-SHA256 con `MP_WEBHOOK_SECRETO`, manifest `id:{data.id};request-id:{x-request-id};ts:{ts};`), consulta `GET /v1/payments/{id}`, si `approved` aprueba el intento (idempotente). |
| POST | `/api/webhooks/pasarela-beta` | webhook de la pasarela beta, firmado con HMAC-SHA256 (`X-Firma`, secreto `PASARELA_BETA_SECRETO`). |
| GET | `/api/comprobantes/{serieNumero}.pdf?codigo=&dni=` · `.xml` | PDF A4 y XML UBL 2.1 (sin firma digital, con nota) para el cliente |

**Pasarela Alcanfores Pay (beta)** — se modela como un PROVEEDOR EXTERNO dentro del servicio (paquete `pasarela.simulador`):
procesa la tarjeta, decide el resultado y avisa al comercio llamando por HTTP a `/api/webhooks/pasarela-beta` con firma HMAC
(así se demuestra el patrón webhook + firma + idempotencia). Tarjetas de prueba:
| Número | Resultado |
|---|---|
| 4111 1111 1111 1111 (Visa) | aprobada |
| 5555 5555 5555 4444 (Mastercard) | aprobada |
| 3782 822463 10005 (Amex) | aprobada |
| 4000 0027 6000 3184 (Visa) | pide 3-D Secure (código 123456 aprueba; otro rechaza) |
| 4000 0000 0000 0002 | rechazada: fondos insuficientes |
| 4000 0000 0000 9995 | rechazada: tarjeta bloqueada |
| 4000 0000 0000 0069 | rechazada: tarjeta vencida |
Yape beta: cualquier celular válido + código de aprobación `123456` aprueba, `000000` rechaza (saldo insuficiente).
El intento expira a los 15 min (tarea programada → EXPIRADO). Pago aprobado ⇒ pago VALIDADO automático (`validado_por = "Pasarela"`),
comprobante, `pago.validado` y `comprobante.emitido`. Nunca guardar ni registrar en logs el número completo ni el CVV.

Administración:
| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/admin/pagos?estado&metodo&canal&desde&hasta&q&page` | lista |
| GET | `/api/admin/pagos/{id}` · `/api/admin/pagos/{id}/archivo` | detalle + imagen del comprobante |
| POST | `/api/admin/pagos/{id}/validar` | → VALIDADO, `fecha_validacion`, `id_administrador`, comprobante, eventos |
| POST | `/api/admin/pagos/{id}/rechazar` | `{observacion}` → RECHAZADO + `pago.rechazado` |
| POST | `/api/admin/pagos/caja` | `{codigoReserva, tipoPago, metodo, monto, codigoOperacion?, comprobante}` cobro presencial (saldo o total) |
| GET | `/api/admin/comprobantes?tipo&desde&hasta&q&page` · `/{id}/pdf` · `/{id}/xml` | |
| POST | `/api/admin/comprobantes/{id}/anular` | ADMIN · `{motivo}` → emite **nota de crédito** (BC01/FC01) que referencia al original |
| GET | `/api/admin/reportes/ingresos?desde&hasta` | ADMIN · `{total, porMetodo:[…], porCanal:[…], porDia:[{fecha, total}], porTipoComprobante:[…], tiempoMedioValidacionMin, pagosRechazados}` |
Internos: `POST /interno/pagos/caja` (lo usa reservas para la reserva manual con pago), `GET /interno/comprobantes/{id}/pdf`.
Consumidor: `reserva.cancelada` (pagos PENDIENTE → ANULADO; si `devolucion` y hubo pagos VALIDADOS → REEMBOLSADO simulado + nota de crédito por cada comprobante).

### 5.5 notificaciones-service (BD `alcanfores_notificaciones`)
Tablas: `notificacion(id PK, codigo_reserva, evento, canal ENUM('WHATSAPP','CORREO'), destino, asunto, cuerpo MEDIUMTEXT, adjuntos JSON, estado ENUM('PENDIENTE','ENVIADO','FALLIDO','SIMULADO'), intentos, ultimo_error, creado_en, enviado_en)`,
`recordatorio(id PK, codigo_reserva UQ, enviar_en, inicio_reserva, estado ENUM('PROGRAMADO','ENVIADO','CANCELADO','OMITIDO'), datos JSON, enviado_en)`, `evento_procesado`.
- **Correo**: `JavaMailSender` si `MAIL_HOST` está definido; si no, modo **SIMULADO** (se guarda el HTML y se ve en el panel).
  Plantillas Thymeleaf HTML con la marca (logo, verde #27811F, azul noche #091A2A). Remitente `MAIL_REMITENTE`.
- **WhatsApp**: cliente de **Evolution API v2** (Baileys): `EVOLUTION_URL`, `EVOLUTION_APIKEY` (cabecera `apikey`), `EVOLUTION_INSTANCIA` (por defecto `alcanfores`).
  `POST /message/sendText/{inst}` `{number:"51XXXXXXXXX", text}` · `POST /message/sendMedia/{inst}` `{number, mediatype:"document", mimetype:"application/pdf", media:<base64>, fileName, caption}`.
  Gestión: `POST /instance/create {instanceName, integration:"WHATSAPP-BAILEYS", qrcode:true}`, `GET /instance/connect/{inst}` (QR base64), `GET /instance/connectionState/{inst}`, `DELETE /instance/logout/{inst}`.
  Sin configuración → SIMULADO. El servicio SOLO envía (no responde chats).
- Mensajes (texto con *negritas* de WhatsApp, sin emojis), por evento:
  `reserva.registrada` (código, cancha, fecha, hora, adelanto, plazo para pagar, enlace `FRONT_URL/mi-reserva?codigo=`) + constancia PDF ·
  `pago.registrado` («recibimos tu comprobante, lo validamos en minutos») · `pago.rechazado` (observación + enlace para reenviar) ·
  `reserva.confirmada` (confirmación + constancia PDF + saldo a pagar en el complejo + cómo llegar) · `comprobante.emitido` (boleta/factura PDF) ·
  `reserva.cancelada` (motivo, devolución) · recordatorio («tu partido es en 2 horas…», dirección, mapa, saldo) · `reserva.asistencia` ASISTIO (enlace para calificar).
  Cada aviso por los DOS canales (correo con PDF adjunto y WhatsApp con PDF como documento).
- **Recordatorio (cron)**: al consumir `reserva.confirmada` crea `recordatorio` con `enviar_en = inicio − recordatorioHoras`.
  `@Scheduled(cron = "0 * * * * *", zone = "America/Lima")` envía los vencidos; si faltan < 30 min para el inicio → OMITIDO.
  `reserva.cancelada` → CANCELADO. Reintentos: FALLIDO con `intentos < 3` se reintenta cada 5 min.
| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/admin/notificaciones?canal&estado&evento&q&page` · `/{id}` (con cuerpo HTML/texto) | token |
| POST | `/api/admin/notificaciones/{id}/reintentar` | ADMIN |
| GET | `/api/admin/recordatorios?estado&page` · POST `/api/admin/recordatorios/ejecutar` (corre el job ya, para la demo) | ADMIN |
| GET | `/api/admin/whatsapp/estado` → `{configurado, instancia, estado: "open|connecting|close|NO_CONFIGURADO", numero?}` | ADMIN |
| POST | `/api/admin/whatsapp/conectar` → `{qrBase64, codigoEmparejamiento?}` · `/desconectar` · `/prueba {telefono, mensaje}` | ADMIN |

---------------------------------------------------------------------------------------------------------------
## 6. Front-end (React 18 + Vite + TypeScript + Tailwind CSS)

Carpeta `frontend/`, puerto dev **5186** con proxy `/api` → `http://localhost:8086`. React Router **v6** (`createBrowserRouter`),
react-hook-form + **Zod** (`@hookform/resolvers`), **Axios** con interceptores (token Bearer, `X-Request-Id`, 401 → logout,
errores → toast con `mensaje` del back y errores por campo en el formulario), **Context API** (`AuthContext`, `ReservaContext`
del asistente de 3 pasos, `ToastContext`), TanStack Query opcional, lucide-react (íconos), recharts (gráficos), dayjs (es, America/Lima).
Estructura: `src/api` (clientes por servicio), `src/componentes` (ui reutilizable), `src/paginas/portal`, `src/paginas/panel`,
`src/rutas` (RutaProtegida por rol), `src/contexto`, `src/esquemas` (Zod), `src/hooks`, `src/utilidades`.

**Identidad visual = la del prototipo aprobado** (`C:\xampp\htdocs\alcanfores-diseno`): paleta azul noche #091A2A, verde
Alcanfores #27811F, verde grass #37A232, verde profundo #0B5D33, verde suave #B9DCC6, menta #E8F3EC, amarillo gol #F2B705
(solo acento); tipografías Barlow Semi Condensed (títulos) e Inter (texto); ilustraciones estilo B (`assets/ilustraciones/*.webp`),
bustos de Rosa (`assets/bustos/`), logo (`ref/logo-alcanfores.png` y versión horizontal). El panel usa el esqueleto «ELESS»
del prototipo (barra superior con migas, KPI con ícono en cuadro, filtros laterales, tabla con ⋯, modales oscuros).
Íconos + ilustraciones, texto mínimo, **sin emojis**. Mobile-first desde 360 px (RNF-01).

Portal (público): `/` inicio (héroe, canchas con foto y calificación, cómo reservar en 3 pasos, tarifas, ubicación con mapa,
preguntas frecuentes) · `/canchas` · **`/canchas/:slug` detalle** (galería con visor, descripción, características, precios
día/noche, calificación promedio + distribución, reseñas paginadas y ordenables, formulario «Califica tu experiencia» con código
de reserva + DNI, botón «Reservar esta cancha») · **`/reservar`** asistente de 3 pasos: (1) cancha + calendario + turnos,
(2) datos: DNI con lupa → RENIEC llena nombres y apellidos (si falla, se escriben a mano), celular y correo obligatorios,
boleta o factura (RUC con lupa → razón social y dirección), términos y privacidad, (3) pago: Yape/Plin (QR/número, código de
operación, subir imagen con vista previa) o Tarjeta/Yape por **Alcanfores Pay (beta)** o Mercado Pago (si está activo); resumen
lateral con montos y plazo (cuenta regresiva) · `/reservar/listo/:codigo` (éxito, descargar constancia, «te enviamos copia a tu
correo y WhatsApp») · `/mi-reserva` (código + DNI → estado, línea de tiempo, pagos y comprobantes PDF, reenviar comprobante,
cancelar con motivo, calificar) · `/pasarela/:ref` (checkout de la pasarela beta con aspecto de proveedor externo: tarjeta
animada, pestañas Tarjeta/Yape, tarjetas de prueba visibles en un recuadro «Modo beta», 3-D Secure en modal, resultado) ·
`/pago/resultado` (retorno de Mercado Pago, sondeo del estado) · `/terminos`, `/privacidad`.

Panel (`/admin`, protegido): login · Hoy (dashboard) · Reservas (lista con filtros laterales, detalle, nueva reserva manual,
validar/rechazar comprobante con visor, cancelar, asistencia, cobrar saldo) · Agenda (grilla canchas × horas, bloquear) ·
Canchas (CRUD + fotos) · Reseñas · Pagos y comprobantes (PDF/XML, anular → nota de crédito) · Clientes · Reportes (gráficos
+ Excel) · Notificaciones (bandeja con vista previa del correo y del WhatsApp, reintentar, recordatorios) · WhatsApp (QR para
conectar, estado, prueba) · Usuarios · Bitácora · Configuración · Estado del sistema (`/api/estado`, microservicios UP/DOWN).
Menú según rol (RECEPCION no ve lo que no puede usar).

---------------------------------------------------------------------------------------------------------------
## 7. Calidad y entrega
- Pruebas: unitarias (servicios con Mockito), de controlador (`@WebMvcTest` + MockMvc), de repositorio cuando aporte; JaCoCo.
  Casos obligatorios: doble reserva (409), validaciones (400 con errores por campo), 401/403, expiración de reservas,
  numeración de comprobantes, cálculo de IGV y monto en letras, RUC válido/ inválido, firma HMAC del webhook, tarjetas de prueba.
- Colección **Postman** `docs/postman/Alcanfores.postman_collection.json` (+ entorno local y producción) con pruebas (`pm.test`).
- Docker: Dockerfile multi-etapa por servicio (`eclipse-temurin:17-jre`, usuario no root, `-XX:MaxRAMPercentage=70`, `-Xss512k`);
  `deploy/docker-compose.yml` con mysql:8.4, rabbitmq:4-management, los 7 servicios Java y el front (nginx). Límite de memoria por servicio.
- CI/CD GitHub Actions: compilar y probar (`mvn -B verify`), construir el front, desplegar por SSH al VPS.
