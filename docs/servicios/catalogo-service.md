# catalogo-service

Puerto **8092** · paquete `pe.alcanfores.catalogo` · BD `alcanfores_catalogo` (pruebas: `alcanfores_catalogo_test`).

## Responsabilidad
Es la «vitrina» del complejo: canchas (datos, características, precios de día y de noche, estado), sus fotos, las
**reseñas verificadas** (solo las deja quien jugó; lo confirma reservas-service) y la **configuración del complejo**
(tarifas, reglas de reserva, horario, datos de pago y contacto) que leen reservas-service y pagos-service.

## Capas
`controlador` (5 controladores REST + internos) → `servicio` (interfaces `CanchaServicio`, `FotoServicio`,
`ResenaServicio`, `ConfiguracionServicio`, `AlmacenFotos`; implementaciones en `servicio.impl`) → `repositorio`
(Spring Data JPA) → `modelo` (entidades + `ClaveConfiguracion`, catálogo tipado de claves). Además `dto` (records con
Bean Validation), `mapeador`, `cliente` (`ReservasCliente` HTTP con circuit breaker), `evento`
(`PublicadorCanchaActualizada`), `config` (seguridad, clientes, OpenAPI, propiedades), `semilla` (`SemillaFotos`) y
`util` (`Imagenes`, `Slugs`, `Calificaciones`).

## Tablas (Flyway `V1__esquema.sql`, datos en `V2__datos_demo.sql`)
| Tabla | Notas |
|---|---|
| `cancha` | `slug` y `nombre` únicos (collation `utf8mb4_unicode_ci`: «Fútbol» = «futbol»), `deporte` ENUM, `caracteristicas` JSON (lista de textos), `precio_dia`/`precio_noche` DECIMAL(6,2), `estado` ENUM, `orden`. CHECK: capacidad 2–30, precios 10–999 y noche ≥ día. |
| `cancha_foto` | `archivo` = UUID + extensión (único), `leyenda`, `orden`, `principal`, `ancho`, `alto` (nulos en WebP). |
| `resena` | `codigo_reserva` único (una reseña por reserva), `puntuacion` TINYINT 1–5 (CHECK), `estado` PUBLICADA/OCULTA, `respuesta` y `respondido_en` del administrador. |
| `configuracion` | clave/valor/descripción (23 claves del SPEC). |
| `evento_procesado` | contrato común de idempotencia (este servicio no consume eventos hoy). |

Semilla: las 4 canchas del SPEC (Cancha 1 · Fútbol 5, Cancha 2 · Fútbol 6, Cancha 3 · Fútbol 7, Losa multiusos) con
descripción larga, medidas y características; 19 reseñas ficticias (4–5 por cancha, una de 3 estrellas en cada cancha y 6
respuestas del admin); la configuración por defecto (Yape/Plin, RUC y razón social ficticios; WhatsApp y correo reales del complejo).

## Endpoints
| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/catalogo/complejo` | público |
| GET | `/api/catalogo/canchas` · `/api/catalogo/canchas/{idOSlug}` | público (solo ACTIVAS) |
| GET / POST | `/api/catalogo/canchas/{id}/resenas` (`orden=recientes|mejor|peor`, `page`, `size` 1–50) | público |
| GET | `/api/catalogo/fotos/{archivo}` | público · `Cache-Control: max-age=604800, public` · `nosniff` |
| GET / POST | `/api/admin/canchas` · GET / PUT `/api/admin/canchas/{id}` | ADMIN |
| PATCH | `/api/admin/canchas/{id}/estado` | ADMIN |
| POST | `/api/admin/canchas/{id}/fotos` (multipart `archivo`, `leyenda`) | ADMIN |
| PATCH / DELETE | `/api/admin/canchas/{id}/fotos/{idFoto}` | ADMIN |
| GET | `/api/admin/resenas` (`estado`, `idCancha`, `puntuacion`, `page`, `size`) | ADMIN |
| PATCH | `/api/admin/resenas/{id}` · POST `/api/admin/resenas/{id}/respuesta` | ADMIN |
| GET / PUT | `/api/admin/configuracion` | GET con token (ADMIN o RECEPCION) · PUT ADMIN |
| GET | `/interno/canchas` · `/interno/canchas/{id}` · `/interno/configuracion` | `X-Interno-Token` |

Swagger: `http://localhost:8092/swagger-ui.html`.

## Validaciones (mensajes en español, 400 con error por campo)
- **Cancha**: nombre 3–60 (único, 409 `CANCHA_DUPLICADA`), deporte obligatorio, superficie 3–40, capacidad 2–30,
  descripción 10–255, descripción larga ≤ 2000, medidas «30 x 18 m», hasta 12 características de 2–60, precios
  10.00–999.00 con 2 decimales y **noche ≥ día** (restricción de clase `@PreciosCoherentes` que marca `precioNoche`).
- **Foto**: JPG/PNG/WebP por **bytes mágicos** (FF D8 FF · 89 PNG · RIFF…WEBP), ≤ 5 MB, JPG/PNG legibles por ImageIO y
  de al menos 200 px por lado, leyenda ≤ 120, máximo 10 por cancha (422 `LIMITE_FOTOS`); una sola principal (422
  `FOTO_PRINCIPAL_REQUERIDA` si se intenta dejar la cancha sin principal).
- **Reseña**: `@CodigoReserva`, `@Dni`, puntuación 1–5, comentario 10–500 `@SinEnlaces`; respuesta del admin 10–500.
- **Configuración** (PUT parcial `{clave: valor}`, acepta números o textos): porcentaje 0–100; enteros con rango por
  clave (devolución 1–168 h, duración 30–180 min y múltiplo de 15, días visibles 1–60, minutos para pagar 5–1440,
  recordatorio 1–48 h); horas `HH:mm`; celulares con el validador de `@Celular` (se guardan normalizados a 9 dígitos);
  correo; enlace `https://`; latitud/longitud en rango; RUC con `Documentos.rucValido`; textos con longitud y sin `<`/`>`;
  clave desconocida → error en esa clave. Reglas entre claves: cierre posterior al primer turno, que entre al menos un
  turno, e inicio de la tarifa de noche dentro del horario.

## Reseña verificada
`POST /api/catalogo/canchas/{id}/resenas` → 404 si la cancha no existe o está inactiva → 409 `RESENA_DUPLICADA` si el
código ya tiene reseña (sin llamar a reservas) → `GET http://reservas-service/interno/reservas/{codigo}/elegible-resena?dni&idCancha`
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
- Coordenadas del complejo aproximadas (-12.0196, -76.8865); Yape/Plin ficticios 900 111 222 (del prototipo).

## Pruebas (`mvn -B -f backend/catalogo-service/pom.xml verify`)
94 pruebas, 0 fallos. Integración con `@SpringBootTest` + MockMvc contra `alcanfores_catalogo_test` (Flyway hace clean +
migrate V1/V2 al iniciar; RabbitMQ y reservas simulados con `@MockitoBean`; tokens JWT reales):
`CatalogoPublicoTest` (complejo, listado, detalle por slug/id, orden de reseñas, foto con caché, rutas manipuladas),
`CanchaAdminTest` (CRUD, 400 por campo, noche < día, deporte desconocido, duplicado 409, 401/403 con RECEPCION, evento
tras crear/editar/desactivar y no publicado si falla, internos con y sin token), `FotoAdminTest` (subida válida,
extensión cambiada, JPG disfrazado, imagen pequeña, WebP, leyenda larga, principal única, borrado del archivo, límite de
10), `ResenaTest` (elegible, no elegible 422, duplicada 409, reservas caído 503, validaciones, moderación y respuesta),
`ConfiguracionTest` (lectura con RECEPCION, PUT 403, 11 claves inválidas a la vez, reglas de horario, normalización y
auditoría). Unitarias: `ResenaServicioImplTest` (resumen, redondeo, carrera → 409), `ReservasClienteHttpTest`
(`MockRestServiceServer`: 200, 404, 422 con mensaje, 400 sin JSON, 500 → 503), `ValidadorConfiguracionTest` (30 casos),
`UtilidadesTest`. Cobertura JaCoCo: 92 % de líneas en total, 94 % en `servicio.impl`.
