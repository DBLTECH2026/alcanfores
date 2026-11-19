# catalogo-service

Puerto **8092** · paquete `pe.alcanfores.catalogo` · BD `alcanfores_catalogo` (pruebas: `alcanfores_catalogo_test`).

## Responsabilidad
Es la «vitrina» del complejo: canchas (datos, características, precios de día y de noche, estado), sus fotos, las
**reseñas verificadas** (solo las deja quien jugó; lo confirma reservas-service) y la **configuración del complejo**
(tarifas, reglas de reserva, horario, datos de pago y contacto) que leen reservas-service y pagos-service.
Desde la **v2** (19/11/2026, `docs/SPEC_V2.md` §A, §B y §E) también guarda el **horario propio de cada cancha** (días,
duración del turno, inicio de la tarifa de noche y qué hace en feriado), el **calendario** de feriados de Perú y días no
laborables, y las **reseñas del complejo** (las carga el administrador desde cada cancha); la reseña de un cliente ahora
exige iniciar sesión con su cuenta (rol CLIENTE).

## Capas
`controlador` (5 controladores REST + internos) → `servicio` (interfaces `CanchaServicio`, `FotoServicio`,
`ResenaServicio`, `ConfiguracionServicio`, `AlmacenFotos`; implementaciones en `servicio.impl`) → `repositorio`
(Spring Data JPA) → `modelo` (entidades + `ClaveConfiguracion`, catálogo tipado de claves). Además `dto` (records con
Bean Validation), `mapeador`, `cliente` (`ReservasCliente` HTTP con circuit breaker), `evento`
(`PublicadorCanchaActualizada`), `config` (seguridad, clientes, OpenAPI, propiedades), `semilla` (`SemillaFotos`) y
`util` (`Imagenes`, `Slugs`, `Calificaciones`).

## Tablas (Flyway `V1__esquema.sql`, datos en `V2__datos_demo.sql`, v2 en `V4__horario_calendario_resenas.sql`)
| Tabla | Notas |
|---|---|
| `cancha` | `slug` y `nombre` únicos (collation `utf8mb4_unicode_ci`: «Fútbol» = «futbol»), `deporte` ENUM, `caracteristicas` JSON (lista de textos), `precio_dia`/`precio_noche` DECIMAL(6,2), `estado` ENUM, `orden`. CHECK: capacidad 2–30, precios 10–999 y noche ≥ día. |
| `cancha_foto` | `archivo` = UUID + extensión (único), `leyenda`, `orden`, `principal`, `ancho`, `alto` (nulos en WebP). |
| `resena` | `codigo_reserva` único (una reseña por reserva), `puntuacion` TINYINT 1–5 (CHECK), `estado` PUBLICADA/OCULTA, `respuesta` y `respondido_en` del administrador. |
| `configuracion` | clave/valor/descripción (23 claves del SPEC + `dias_generados` = 120 desde la v2). |
| `cancha` (v2) | + `duracion_turno_min` (30–240, múltiplo de 15; CHECK), `inicio_noche` TIME (toma el valor global al migrar), `feriado_modo` ENUM('ABIERTA','CERRADA'), `recargo_feriado` DECIMAL(8,2) ≥ 0 (adicional por turno en feriado). |
| `cancha_horario` (v2) | `PK(id_cancha, dia_semana)`, `dia_semana` 1..7 (ISO, 1 = lunes), `abierto`, `apertura`, `cierre` (hora en que TERMINA el último turno; medianoche = 23:59:59). Un día cerrado conserva sus horas. |
| `dia_especial` (v2) | `fecha` UNIQUE, `tipo` ENUM('FERIADO','NO_LABORABLE'), `nombre` (120), `canchas` JSON (ids; NULL = todas; solo NO_LABORABLE). |
| `resena` (v2) | + `origen` ENUM('CLIENTE','COMPLEJO') (las antiguas quedan CLIENTE), `id_cuenta_cliente` NULL; `codigo_reserva` pasa a NULL (las del complejo no tienen). |
| `evento_procesado` | contrato común de idempotencia (este servicio no consume eventos hoy). |

Semilla v2 (`V4`): canchas de fútbol lunes a domingo 08:00–23:00 y la losa (deporte MULTIUSO) 08:00–22:00, turnos de 60 min,
feriado ABIERTA con S/ 10.00; feriados nacionales de Perú de 2026 y 2027 (16 por año, con Jueves y Viernes Santo).

Semilla: las 4 canchas del SPEC (Cancha 1 · Fútbol 5, Cancha 2 · Fútbol 6, Cancha 3 · Fútbol 7, Losa multiusos) con
descripción larga, medidas y características; 19 reseñas ficticias (4–5 por cancha, una de 3 estrellas en cada cancha y 6
respuestas del admin); la configuración por defecto (Yape/Plin, RUC y razón social ficticios; WhatsApp y correo reales del complejo).

## Endpoints
| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/catalogo/complejo` | público |
| GET | `/api/catalogo/canchas` · `/api/catalogo/canchas/{idOSlug}` | público (solo ACTIVAS) |
| GET / POST | `/api/catalogo/canchas/{id}/resenas` (`orden=recientes|mejor|peor`, `page`, `size` 1–50) · POST `{codigoReserva, puntuacion, comentario}` | GET público · POST token CLIENTE |
| GET | `/api/catalogo/fotos/{archivo}` | público · `Cache-Control: max-age=604800, public` · `nosniff` |
| GET / POST | `/api/admin/canchas` · GET / PUT `/api/admin/canchas/{id}` | ADMIN |
| PATCH | `/api/admin/canchas/{id}/estado` | ADMIN |
| POST | `/api/admin/canchas/{id}/fotos` (multipart `archivo`, `leyenda`) | ADMIN |
| PATCH / DELETE | `/api/admin/canchas/{id}/fotos/{idFoto}` | ADMIN |
| GET | `/api/admin/resenas` (`estado`, `idCancha`, `puntuacion`, `page`, `size`) | ADMIN |
| PATCH | `/api/admin/resenas/{id}` · POST `/api/admin/resenas/{id}/respuesta` | ADMIN |
| GET / PUT | `/api/admin/configuracion` | GET con token (ADMIN o RECEPCION) · PUT ADMIN |
| GET / PUT | `/api/admin/canchas/{id}/horario` → `{duracionTurnoMin, inicioNoche, dias:[{diaSemana, abierto, apertura, cierre}], texto, feriado:{modo, recargo}}` | ADMIN |
| GET / POST | `/api/admin/canchas/{id}/horario/vista-previa?dia=1..7` (POST con el cuerpo del PUT, no guarda) → `[{horaInicio, horaFin, precio, franja}]` | ADMIN |
| GET / POST | `/api/admin/canchas/{id}/resenas` (todas, también ocultas · crear reseña del complejo `{autor, puntuacion, comentario, fecha}`) | ADMIN |
| PUT / DELETE | `/api/admin/resenas/{id}` (solo origen COMPLEJO; 422 `RESENA_DE_CLIENTE` si es de un cliente) | ADMIN |
| GET | `/api/admin/calendario?anio=` | ADMIN o RECEPCION |
| POST · PUT / DELETE | `/api/admin/calendario` · `/api/admin/calendario/{id}` `{fecha, tipo, nombre, canchas?}` | ADMIN |
| POST | `/api/admin/calendario/feriados-peru?anio=` → `{creados, existentes}` | ADMIN |
| GET | `/api/catalogo/calendario?desde&hasta` → `[{id, fecha, tipo, nombre, canchas}]` (por defecto hoy … hoy + 60; máx. 366 días) | público |
| GET | `/interno/canchas` · `/interno/canchas/{id}` (con `duracionTurnoMin, inicioNoche, dias, feriadoModo, recargoFeriado`) · `/interno/configuracion` | `X-Interno-Token` |
| GET | `/interno/calendario?desde&hasta` | `X-Interno-Token` |
| GET | `/interno/resenas/{codigoReserva}` → `{codigoReserva, existe}` (reservas lo usa para `puedeResenar`) | `X-Interno-Token` |

Swagger: `http://localhost:8092/swagger-ui.html`.

## Validaciones (mensajes en español, 400 con error por campo)
- **Cancha**: nombre 3–60 (único, 409 `CANCHA_DUPLICADA`), deporte obligatorio, superficie 3–40, capacidad 2–30,
  descripción 10–255, descripción larga ≤ 2000, medidas «30 x 18 m», hasta 12 características de 2–60, precios
  10.00–999.00 con 2 decimales y **noche ≥ día** (restricción de clase `@PreciosCoherentes` que marca `precioNoche`).
- **Foto**: JPG/PNG/WebP por **bytes mágicos** (FF D8 FF · 89 PNG · RIFF…WEBP), ≤ 5 MB, JPG/PNG legibles por ImageIO y
  de al menos 200 px por lado, leyenda ≤ 120, máximo 10 por cancha (422 `LIMITE_FOTOS`); una sola principal (422
  `FOTO_PRINCIPAL_REQUERIDA` si se intenta dejar la cancha sin principal).
- **Reseña**: `@CodigoReserva`, puntuación 1–5, comentario 10–500 `@SinEnlaces`; respuesta del admin 10–500. El DNI ya no va
  en el cuerpo: sale del token CLIENTE. La sesión se revisa ANTES que los campos (401 «Inicia sesión para calificar la
  cancha.», 403 con token de administrador).
- **Reseña del complejo**: autor 2–60 (letras, espacios, puntos, apóstrofo y guion), puntuación 1–5, comentario 10–500 sin
  enlaces, fecha obligatoria y no futura (se guarda a las 12:00).
- **Horario**: duración 30–240 y múltiplo de 15; `inicioNoche` HH:mm; los 7 días (1..7 sin repetir); día abierto con apertura
  y cierre HH:mm (cierre «24:00» o «23:59» = medianoche), cierre > apertura, que entre al menos un turno y que no sobre tiempo:
  «Con turnos de 2 h, de 8:00 a. m. a 11:00 p. m. sobra 1 hora: ajusta el cierre a 10:00 p. m. o a 12:00 a. m.» (campo
  `dias[i].cierre`; si todos los errores dicen lo mismo, ese es el `mensaje`); recargo de feriado 0.00–999.99.
- **Día especial**: fecha y tipo obligatorios, nombre 3–120 sin enlaces ni `< >`; `canchas` solo en NO_LABORABLE y deben existir;
  fecha repetida → 409 `DIA_ESPECIAL_DUPLICADO`; reservas vigentes ese día en las canchas que cerraría (NO_LABORABLE: las
  indicadas o todas; FERIADO: las de modo CERRADA) → 409 `DIA_CON_RESERVAS` con los códigos (consulta
  `GET /interno/reservas/vigentes?fecha&idsCancha` de reservas-service; fechas pasadas no se revisan; reservas caído → 503).
- **`dias_generados`**: 14–365 y no menor que `dias_visibles`.
- **Configuración** (PUT parcial `{clave: valor}`, acepta números o textos): porcentaje 0–100; enteros con rango por
  clave (devolución 1–168 h, duración 30–180 min y múltiplo de 15, días visibles 1–60, minutos para pagar 5–1440,
  recordatorio 1–48 h); horas `HH:mm`; celulares con el validador de `@Celular` (se guardan normalizados a 9 dígitos);
  correo; enlace `https://`; latitud/longitud en rango; RUC con `Documentos.rucValido`; textos con longitud y sin `<`/`>`;
  clave desconocida → error en esa clave. Reglas entre claves: cierre posterior al primer turno, que entre al menos un
  turno, e inicio de la tarifa de noche dentro del horario.

## Reseña verificada
`POST /api/catalogo/canchas/{id}/resenas` → 404 si la cancha no existe o está inactiva →
`GET http://reservas-service/interno/reservas/{codigo}/elegible-resena?dni&idCancha` (primero código + DNI: así un tercero
no puede saber si un código existe y ya tiene reseña) → 409 `RESENA_DUPLICADA` solo si la reserva es elegible y ya tiene
reseña
(`RestClient` `@LoadBalanced` + `InterceptorInterno`, timeout 3 s, circuit breaker Resilience4j: 50 % de fallos en 10
llamadas abre 20 s) → si no es elegible, 422 `RESENA_NO_ELEGIBLE` con el `motivo` de reservas (un 404 de reservas se
traduce a «No encontramos una reserva con ese código y DNI.») → si reservas falla o no responde, 503
`SERVICIO_NO_DISPONIBLE`. El autor lo da reservas («Diego R.»). Dos envíos simultáneos chocan en el índice único y el
segundo recibe el mismo 409. El resumen calcula promedio con 1 decimal (HALF_UP: 4.25 → 4.3), total y distribución 5…1.

## Eventos
- Publica `cancha.actualizada` (`CanchaActualizadaEvento`) al crear, editar o cambiar de estado (si el estado no cambia,
  no se publica). Se registra con `ApplicationEventPublisher` y sale a RabbitMQ en `@TransactionalEventListener(AFTER_COMMIT)`.
- Publica `auditoria.accion` (vía `Auditor`) en: CREAR_CANCHA, EDITAR_CANCHA, CAMBIAR_ESTADO_CANCHA, SUBIR_FOTO,
  EDITAR_FOTO, BORRAR_FOTO, OCULTAR_RESENA / PUBLICAR_RESENA, RESPONDER_RESENA, EDITAR_CONFIGURACION (detalle «clave:
  antes → después») y CREAR_RESENA (actor = autor, rol CLIENTE).
- `cancha.actualizada` lleva desde la v2 el horario completo (`duracionTurnoMin, inicioNoche, dias, feriadoModo,
  recargoFeriado`) y también se publica al guardar el horario (auditoría EDITAR_HORARIO_CANCHA).
- Publica `calendario.actualizado` (`CalendarioActualizadoEvento`) tras el commit al crear, editar (si cambia la fecha, uno
  con la fecha vieja y `eliminado = true` y otro con la nueva), borrar (`eliminado = true`) o cargar feriados de Perú.
  Auditoría: CREAR_DIA_ESPECIAL, EDITAR_DIA_ESPECIAL, BORRAR_DIA_ESPECIAL, CARGAR_FERIADOS_PERU, CREAR_RESENA_COMPLEJO,
  EDITAR_RESENA_COMPLEJO, BORRAR_RESENA_COMPLEJO.
- No consume eventos ni tiene tareas programadas.

## Fotos
Se guardan en `${UPLOADS_DIR:./uploads}/canchas` con nombre UUID y escritura atómica. Si la transacción se revierte, el
archivo recién escrito se borra; al borrar una foto, el archivo se elimina después del commit y, si era la principal, la
siguiente por orden pasa a serlo. El GET público solo acepta nombres `UUID.(jpg|png|webp)` dentro de la carpeta
(bloquea `..`, barras y rutas absolutas → 404 o 400 del firewall).

**Semilla de fotos** (`SemillaFotos`, `alcanfores.catalogo.semilla-fotos`, por defecto `true`): copia
`src/main/resources/semilla/fotos/cancha<idCancha>-<n>.jpg|png|webp` a `uploads/canchas/` y las registra solo en las
canchas que aún no tienen fotos; la `-1` es la principal; leyendas en `semilla/fotos/leyendas.properties`. El nombre
en disco es un UUID derivado del nombre de la semilla, así que **si se reemplazan las imágenes de la semilla y se
reinicia el servicio, los archivos en disco se actualizan** sin crear registros nuevos. Hoy son rellenos JPG hechos de
las ilustraciones del prototipo (IL-05, ARQ-03, ARQ-05 para fútbol; ARQ-04, IL-06, IL-07 para la losa).

## Decisiones
- `caracteristicas` se mapea con un `AttributeConverter` (lista ↔ texto JSON) y tipo `LONGVARCHAR`: MariaDB 10.4 guarda
  JSON como LONGTEXT y MySQL 8 como JSON; con el tipo JSON nativo de Hibernate `ddl-auto: validate` falla en MariaDB.
- La URL JDBC agrega `allowPublicKeyRetrieval=true&useSSL=false` (necesario con MySQL 8.4 y usuario con clave en Docker).
- Se define un `RestClient.Builder` `@Primary` sin balanceo además del `@LoadBalanced`, para que ningún otro componente
  (p. ej. Eureka) reciba por error el balanceado.
- El detalle público y el del panel comparten `CanchaDetalleRespuesta` (incluye `estado`, `orden`, fechas y `calificacion`
  además de lo pedido por el SPEC).
- `GET /api/admin/canchas` devuelve una lista (no paginada): son pocas canchas y el panel las muestra todas.
- v2: el horario va en `cancha_horario` (`@ElementCollection`, se reemplaza completo al guardar). Una cancha nueva nace con
  el horario global (`primer_turno`, `ultimo_turno`, `duracion_min`, `inicio_noche`) en los 7 días, ajustando el cierre a la
  duración, y feriado ABIERTA con S/ 10. El resumen `texto` une días SEGUIDOS con el mismo horario («Lun a vie 8:00 a. m.–11:00
  p. m. · Sáb y dom 7:00 a. m.–12:00 a. m. · Dom cerrado») y usa siempre la forma completa de cada hora.
- v2: `POST /api/admin/calendario/feriados-peru` no revisa reservas (carga masiva de feriados oficiales); los turnos con
  reserva los conserva reservas-service. Jueves y Viernes Santo se calculan con la Pascua (algoritmo de Meeus/Jones/Butcher).
- v2: el ítem de reseña del panel trae `fecha` (= `creadoEn`) y `origen`; el público trae `origen` y `verificada` = origen CLIENTE.
- v2: las reseñas del complejo cuentan en el promedio y la distribución igual que las de clientes.
- Coordenadas del complejo aproximadas (-12.0196, -76.8865); Yape/Plin ficticios 900 111 222 (del prototipo).

## Pruebas (`mvn -B -f backend/catalogo-service/pom.xml verify`)
**147 pruebas, 0 fallos (19/11/2026, v2).** Nuevas: `HorarioTest` (semilla, cancha nueva con horario global, guardado +
evento con la semana + interno con 23:59:59, mensaje de «sobra 1 hora», vista previa GET/POST, cierre 24:00, día cerrado,
400 por campo, 401/403/404), `CalendarioTest` (semilla 2026/2027, público e interno, NO_LABORABLE con canchas, 409
`DIA_ESPECIAL_DUPLICADO` y `DIA_CON_RESERVAS`, feriado con canchas que cierran, fechas pasadas, 503, editar cambiando la
fecha (dos eventos), borrar, feriados de Perú sin duplicar, permisos), `ResenaComplejoYSesionTest` (401/403 antes de validar,
DNI del token, CRUD de reseñas del complejo, 422 `RESENA_DE_CLIENTE`, fecha futura), `HorasYFeriadosTest` (Pascua de 7 años,
feriados, horas «24:00», resumen de la semana, ids en JSON), `HorarioServicioImplTest` y casos de `/interno/reservas/vigentes`
en `ReservasClienteHttpTest`. Migración `V4` probada además sobre una copia de la BD local con datos (5 canchas, 23 reseñas,
14 fotos: nada se pierde; 35 filas de horario, 32 días especiales, `dias_generados` = 120). Cobertura JaCoCo:
95,7 % de instrucciones en total, **95,6 % en la capa de servicio** (100 % de líneas en `HorarioServicioImpl`,
`CalendarioServicioImpl` y `ResenaServicioImpl`).

Antes de la v2: 98 pruebas, 0 fallos (18/11/2026). Integración con `@SpringBootTest` + MockMvc contra `alcanfores_catalogo_test` (Flyway hace clean +
migrate V1/V2 al iniciar; RabbitMQ y reservas simulados con `@MockitoBean`; tokens JWT reales):
`CatalogoPublicoTest` (complejo, listado, detalle por slug/id, orden de reseñas, foto con caché, rutas manipuladas),
`CanchaAdminTest` (CRUD, 400 por campo, noche < día, deporte desconocido, duplicado 409, 401/403 con RECEPCION, evento
tras crear/editar/desactivar y no publicado si falla, internos con y sin token), `FotoAdminTest` (subida válida,
extensión cambiada, JPG disfrazado, imagen pequeña, WebP, leyenda larga, principal única, borrado del archivo, límite de
10), `ResenaTest` (elegible, no elegible 422, duplicada 409, reservas caído 503, validaciones, moderación y respuesta),
`ConfiguracionTest` (lectura con RECEPCION, PUT 403, 11 claves inválidas a la vez, reglas de horario, normalización y
auditoría). Unitarias: `ResenaServicioImplTest` (resumen, redondeo, carrera → 409), `ReservasClienteHttpTest`
(`MockRestServiceServer`: 200, 404, 422 con mensaje, 400 sin JSON, 500 → 503), `ValidadorConfiguracionTest` (30 casos),
`UtilidadesTest`. Cobertura JaCoCo: 92,3 % de líneas en total, 93,9 % en la capa de servicio.
