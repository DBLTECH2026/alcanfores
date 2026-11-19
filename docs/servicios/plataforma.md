# Plataforma: discovery-server, api-gateway y auth-service

Estos tres módulos forman la base de la arquitectura de microservicios del sistema de reservas del Complejo Deportivo
Los Alcanfores. Con ellos los servicios se encuentran entre sí (Eureka), el front tiene una sola puerta de entrada
(gateway) y se resuelven el acceso y la auditoría (auth).

```
React (5186) ──> api-gateway :8086 ──lb://──> auth-service :8091 (BD alcanfores_auth)
                      │                 └──> catalogo / reservas / pagos / notificaciones
                      └── consulta el registro ── discovery-server :8761 (Eureka) <── se registran todos
RabbitMQ: todos publican auditoria.accion ──> cola auth.auditoria ──> tabla bitacora
```

---

## 1. discovery-server (`pe.alcanfores.discovery`, puerto 8761)

**Responsabilidad:** registro de servicios. Cada microservicio se anuncia con su `spring.application.name` y el gateway
los encuentra por nombre (`lb://reservas-service`), sin IP fijas.

- Spring Cloud Netflix Eureka Server (`@EnableEurekaServer`), nodo único: `register-with-eureka: false` y
  `fetch-registry: false` (no se registra a sí mismo).
- `enable-self-preservation: ${EUREKA_AUTOPRESERVACION:true}`. En producción queda activa; en local o en una demo se
  apaga con `EUREKA_AUTOPRESERVACION=false` para que una instancia caída desaparezca rápido del registro.
  `EUREKA_DESALOJO_MS` (30 s) fija cada cuánto se limpian las instancias vencidas.
- Actuator: `/actuator/health` y `/actuator/info`. Panel web en `http://localhost:8761`.
- No usa base de datos ni `comun`.
- **Pruebas (3):** la salud responde UP, el registro no se incluye a sí mismo y la autopreservación se puede apagar por
  propiedad.

---

## 2. api-gateway (`pe.alcanfores.gateway`, puerto 8086)

**Responsabilidad:** es la única puerta de entrada del front. Spring Cloud Gateway sobre **WebFlux**
(`spring-cloud-starter-gateway-server-webflux`, propiedades bajo `spring.cloud.gateway.server.webflux.*`) con cliente
Eureka y Resilience4j reactivo.

### 2.1 Rutas (SPEC §1.1)
| Ruta (id) | Prefijos | Destino | Timeout |
|---|---|---|---|
| `auth` | `/api/auth/**`, `/api/admin/usuarios/**`, `/api/admin/bitacora/**` | `lb://auth-service` | 10 s |
| `catalogo-archivos` | `/api/admin/canchas/*/fotos/**`, `/api/catalogo/fotos/**` | `lb://catalogo-service` | 20 s |
| `catalogo` | `/api/catalogo/**`, `/api/admin/canchas/**`, `/api/admin/resenas/**`, `/api/admin/configuracion/**`, `/api/admin/calendario/**` (v2) | `lb://catalogo-service` | 10 s |
| `reservas-archivos` | `/api/reservas/*/constancia.pdf`, `/api/admin/reportes/reservas.xlsx` | `lb://reservas-service` | 20 s |
| `reservas-documentos` | `/api/documentos/**` (Factiliza tarda ~11 s en decir «no existe»; reservas espera 15 s) | `lb://reservas-service` | 20 s |
| `reservas` | `/api/disponibilidad/**`, `/api/reservas/**`, `/api/documentos/**`, `/api/admin/reservas/**`, `/api/admin/turnos/**`, `/api/admin/clientes/**`, `/api/admin/dashboard/**`, `/api/admin/reportes/reservas/**`, `/api/cliente/**` (v2, «Mis reservas») | `lb://reservas-service` | 10 s |
| `pagos-archivos` | `/api/pagos/comprobante`, `/api/comprobantes/**`, `/api/admin/pagos/*/archivo`, `/api/admin/comprobantes/*/pdf`, `/api/admin/comprobantes/*/xml`, `/api/admin/reportes/ingresos.xlsx` | `lb://pagos-service` | 20 s |
| `pagos` | `/api/pagos/**`, `/api/pasarela/**`, `/api/webhooks/**`, `/api/admin/pagos/**`, `/api/admin/comprobantes/**`, `/api/admin/reportes/ingresos/**` | `lb://pagos-service` | 10 s |
| `notificaciones-lentas` | `/api/admin/whatsapp/**`, `/api/admin/recordatorios/ejecutar`, `/api/admin/notificaciones/*/reintentar` | `lb://notificaciones-service` | 20 s |
| `notificaciones` | `/api/admin/notificaciones/**`, `/api/admin/recordatorios/**` | `lb://notificaciones-service` | 10 s |
| — | `GET /api/estado` | el propio gateway | — |

Las rutas «de archivos» y `reservas-documentos` tienen `order: -1` (se evalúan primero) y `metadata.response-timeout: 20000` (`timeout-archivos-ms` / `timeout-documentos-ms`; su circuit breaker espera ese tiempo + 2 s). Conexión: 3 s.
Filtros por defecto: `RemoveRequestHeader=X-Interno-Token` (nadie de afuera puede hacerse pasar por un servicio) y
`DedupeResponseHeader` para las cabeceras CORS y `X-Request-Id`. Ningún filtro quita `Authorization`: así llega el token
del cliente a `/api/catalogo/**` (reseña con sesión, la valida catalogo-service) y a `/api/auth/clientes/**`.

### 2.2 Filtros globales (`WebFilter`, en este orden)
Se hicieron como `WebFilter` y no como `GlobalFilter` para que también cubran rutas que no van a ningún servicio
(`/interno/**`, `/api/estado`, rutas inexistentes).

1. **`FiltroIdSolicitud`**: respeta el `X-Request-Id` del front si es válido (8 a 64 caracteres `[A-Za-z0-9._-]`); si
   no, genera un UUID. Lo envía al servicio y lo devuelve en la respuesta. `comun` lo pone en el MDC de cada servicio.
2. **`FiltroRegistroAcceso`**: log de acceso `GET /api/catalogo/canchas -> 200 (35 ms) ip=... id=...`. Se escribe al
   confirmar la respuesta, así el estado es el real aunque lo ponga el manejador de errores.
3. **CORS (`FiltroCors`)**: orígenes de `FRONT_URL` (lista separada por comas, sin la barra final; en local por
   defecto `http://localhost:5186,http://localhost:5187`: portal y panel). Un `Origin` no permitido (preflight o
   solicitud simple) recibe **403 `ORIGEN_NO_PERMITIDO`** con el cuerpo estándar `ErrorApi` (antes salía vacío). Métodos
   GET/POST/PUT/PATCH/DELETE/OPTIONS, cabeceras `Authorization`, `Content-Type`, `X-Request-Id`; expone `X-Request-Id`
   y `Content-Disposition`; sin credenciales (el front usa Bearer); `max-age` 1 h. Va antes que los rechazos para que
   el navegador pueda leer los 401/404/413/429.
4. **`FiltroRutasInternas`**: `/interno/**` responde **404** con `ErrorApi` («La ruta solicitada no existe.»). Antes de
   comparar normaliza la ruta (quita `;parámetros`, decodifica `%69`/`%2e` incluso dos veces, resuelve `..`) y además
   rechaza cualquier segmento `..`: así trucos como `/api/reservas/..;/../interno/x` también dan 404.
5. **`FiltroTamanioCuerpo`**: 6 MB como máximo. Con `Content-Length` rechaza al instante (413 `CUERPO_MUY_GRANDE`); si
   el cuerpo llega por partes cuenta los bytes al vuelo y corta al pasarse.
6. **`FiltroLimiteSolicitudes`**: token bucket en memoria por IP y por regla (la IP es la primera de `X-Forwarded-For`
   o la remota). Límites: `POST /api/auth/login` 10/min, `/api/documentos/**` 20/min, `POST /api/reservas` 10/min,
   `/api/pasarela/**` 30/min y, desde la v2, `POST /api/auth/clientes/registro` 5/min y `POST /api/auth/clientes/login`
   10/min (cada regla con su propio cupo; configurables en `alcanfores.gateway.limites`). Al agotarse: **429**
   `DEMASIADAS_SOLICITUDES` con `Retry-After`; las respuestas permitidas llevan `X-RateLimit-Limit` y
   `X-RateLimit-Remaining`. Una tarea cada 5 min descarta las cubetas llenas sin uso.
7. **`FiltroJwtAdmin`**: en `/api/admin/**` y (v2) `/api/cliente/**` exige un JWT válido (`JwtServicio` de `comun`).
   Sin token: 401 «Inicia sesión para continuar.»; vencido: 401 «Tu sesión venció. Vuelve a iniciar sesión.»; alterado o
   de otra firma: 401 «El token de sesión no es válido.». En `/api/admin/**` un token de cliente (rol `CLIENTE`) recibe
   **403 `SIN_PERMISO`** ya en el gateway. El login, el registro de clientes y el catálogo público no pasan por aquí.
   Cada servicio vuelve a validar el token y el rol (reservas exige `CLIENTE` en `/api/cliente/**`).
8. **`FiltroIpCliente`**: deja `X-Forwarded-For` con un solo valor, la IP del cliente; el gateway le agrega la del
   salto (`trusted-proxies: ${GATEWAY_PROXIES_CONFIABLES:.*}`). Sin `trusted-proxies`, Spring Cloud Gateway 4.3 borra
   todo `X-Forwarded-*` hacia los servicios y la bitácora guardaría la IP del gateway. En producción nginx debe
   **escribir** `X-Forwarded-For $remote_addr` (no agregarlo a lo que mande el cliente) para que no se pueda falsear.

### 2.3 Circuit breaker y fallback
Cada ruta tiene un `CircuitBreaker` de Resilience4j (configurado en `CircuitoConfig` con un `Customizer`, porque el
starter reactivo no trae el enlace con `resilience4j.*` del yml): ventana de 20 llamadas, mínimo 10, se abre con 50 %
de fallos, queda abierto 15 s y luego deja pasar 3 de prueba. El limitador de tiempo es el timeout HTTP + 2 s, para que
corte primero el cliente HTTP del gateway. Si el servicio no tiene instancias, no responde a tiempo o el circuito está
abierto, se reenvía a `forward:/fallback/<servicio>` y el `FallbackControlador` responde **503**
`SERVICIO_NO_DISPONIBLE`: «El servicio de *acceso / catálogo / reservas / pagos / notificaciones* no responde. Intenta
en unos segundos.», con la `ruta` original del cliente.

### 2.4 `GET /api/estado` (público)
`MonitorServiciosImpl` consulta con `ReactiveDiscoveryClient` las instancias de cada servicio esperado
(`alcanfores.gateway.servicios`) y llama en paralelo a su `/actuator/health` (2 s máximo). Eureka no se registra a sí
mismo: se consulta por la URL de `EUREKA_URL`. El gateway, si aún no figura en Eureka, se consulta en su puerto local.

```json
{ "generadoEn": "2026-11-18T21:50:03",
  "servicios": [ { "nombre": "discovery-server", "estado": "UP", "instancias": 1, "latenciaMs": 12 },
                 { "nombre": "api-gateway", "estado": "UP", "instancias": 1, "latenciaMs": 8 },
                 { "nombre": "auth-service", "estado": "UP", "instancias": 1, "latenciaMs": 15 },
                 { "nombre": "catalogo-service", "estado": "DOWN", "instancias": 0, "latenciaMs": null } ] }
```
UP si al menos una instancia responde UP; sin instancias = DOWN; `latenciaMs` = la instancia UP más rápida.

### 2.5 Errores del propio gateway
`ManejadorErroresGateway` (`ErrorWebExceptionHandler`) convierte todo error en `ErrorApi` en español: 404
`NO_ENCONTRADO`, 405 `METODO_NO_PERMITIDO`, 413 `CUERPO_MUY_GRANDE`, 502/503/504 `SERVICIO_NO_DISPONIBLE`, 500
`ERROR_INTERNO`.

### 2.6 Uso de `comun` en WebFlux
Solo se usan `JwtServicio`, `ErrorApi`, `AlcanforesPropiedades` y `Tiempo`. Las partes Servlet, Security y AMQP de
`comun` son dependencias opcionales, así que no llegan al classpath (comprobado con `dependency:tree`: no hay
`spring-webmvc` ni `jakarta.servlet`). Su autoconfiguración Servlet no se activa (`ContextoReactivoTest`).

### 2.7 Pruebas (54 en el módulo, 34 de ellas con WebTestClient contra el puerto real; 19/11/2026)
Un «servicio simulado» (servidor Reactor Netty) se registra como instancia de `auth-service` con el
`SimpleDiscoveryClient`; los demás servicios quedan sin instancias. Casos: enrutamiento y propagación de `X-Request-Id`, IP del cliente primera en `X-Forwarded-For`,
id inválido regenerado, `X-Interno-Token` eliminado, login público, 404 y 405 con `ErrorApi`; CORS (preflight
permitido, varios orígenes, origen ajeno rechazado, cabeceras expuestas, CORS en los errores); bloqueo de `/interno` y
de 5 trucos de ruta; JWT ausente, vencido, de otra firma y válido; 429 al undécimo login de una IP (otra IP sigue
pasando) y el límite de reservas solo para POST; 413 con 7 MB y paso con 1 MB; fallback por falta de instancias, por
demora (responde en menos de 4 s) y con el nombre de cada servicio; `/api/estado`. **v2:** `/api/cliente/**` sin token
o con token alterado → 401, con token de cliente llega a reservas (fallback «servicio de reservas»), token de cliente en
`/api/admin/**` → 403 `SIN_PERMISO`, `/api/admin/calendario` llega a catálogo, el `Authorization` llega intacto al
servicio, 429 al sexto registro de clientes y al undécimo login de clientes de una IP (cupos independientes del login del
panel), ningún filtro del yml quita `Authorization` y las rutas nuevas están en el yml. Unitarias: normalización de rutas,
token bucket con reloj falso (recarga de una ficha cada 6 s, limpieza), lista de orígenes CORS. Cobertura JaCoCo:
90,1 % de instrucciones en total; capa `servicio` 97,9 %, `filtro` 90,5 %, `limite` 99,7 %.

---

## 3. auth-service (`pe.alcanfores.auth`, puerto 8091, BD `alcanfores_auth`)

**Responsabilidad:** usuarios del panel (ADMIN y RECEPCION), inicio de sesión con JWT, bloqueo por intentos, cambio y
restablecimiento de contraseña, **cuentas de clientes del portal** (v2: rol `CLIENTE`, «Mis reservas» por DNI) y
**bitácora central** de auditoría de todos los microservicios.

### 3.1 Tablas (Flyway `V1__esquema.sql` y `V2__cuenta_cliente.sql`, válidos en MariaDB 10.4 y MySQL 8.4)
- `administrador(id_administrador PK, nombres, apellidos, usuario UQ, correo UQ, contrasena_hash, rol ENUM('ADMIN','RECEPCION'), estado ENUM('ACTIVO','INACTIVO'), intentos_fallidos (CHECK ≥ 0), bloqueado_hasta, ultimo_acceso, creado_en, actualizado_en)`
- `bitacora(id_bitacora PK, servicio, id_actor, actor, rol, accion, entidad, id_entidad, detalle VARCHAR(500), ip, fecha)` con índices por fecha, servicio+fecha, acción+fecha y actor.
- `evento_procesado(id_evento CHAR(36) PK, tipo, procesado_en)` para consumir sin duplicar.
- **v2** `cuenta_cliente(id PK, dni CHAR(8) UQ, nombres, apellidos, correo UQ, telefono CHAR(9), contrasena_hash, estado ENUM('ACTIVO','INACTIVO'), intentos_fallidos (CHECK ≥ 0), bloqueado_hasta, creado_en, ultimo_acceso)`.
  La migración solo crea la tabla (no toca administradores ni bitácora); se probó sobre una copia de la BD local con datos.

**Semilla** (`SemillaUsuarios`, `CommandLineRunner`, solo si la tabla está vacía; nunca en SQL): `rhuaman` Rosa Huamán
ADMIN, `mtorres` Marco Torres RECEPCION, `docente` Docente UTP ADMIN. Claves por `SEED_CLAVE_ADMIN`,
`SEED_CLAVE_RECEPCION`, `SEED_CLAVE_DOCENTE` (por defecto local `Alcanfores2026`, `Recepcion2026`, `UTP2026`). Se
puede apagar con `SEED_HABILITADA=false`.

### 3.2 Endpoints
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{usuario, contrasena}` → `{token, tipo:"Bearer", expiraEn, usuario:{id, nombres, apellidos, usuario, correo, rol}}` |
| GET | `/api/auth/yo` | token | usuario actual leído de la BD (401 si fue desactivado después del login) |
| POST | `/api/auth/cambiar-contrasena` | token | `{actual, nueva, confirmacion}` |
| GET | `/api/admin/usuarios` | ADMIN | `q` (nombre, apellido, usuario o correo), `rol`, `estado`, `page`, `size` (máx. 100), `sort` (solo campos permitidos) |
| GET | `/api/admin/usuarios/{id}` | ADMIN | detalle |
| POST | `/api/admin/usuarios` | ADMIN | `{nombres, apellidos, usuario, correo, rol, contrasena}` → 201 con `Location` |
| PUT | `/api/admin/usuarios/{id}` | ADMIN | `{nombres, apellidos, usuario, correo, rol}` |
| PATCH | `/api/admin/usuarios/{id}/estado` | ADMIN | `{estado}` |
| POST | `/api/admin/usuarios/{id}/restablecer-contrasena` | ADMIN | `{nueva}`; además desbloquea la cuenta |
| GET | `/api/admin/bitacora` | ADMIN | `servicio`, `actor` (contiene), `accion`, `entidad`, `desde`, `hasta` (YYYY-MM-DD), paginado, lo más reciente primero |
| GET | `/api/admin/bitacora/opciones` | ADMIN | `{servicios[], acciones[]}` para los combos de filtro |
| POST | `/api/auth/clientes/registro` | público (5/min por IP) | `{dni, nombres, apellidos, correo, telefono, contrasena, confirmacion, aceptaTerminos, aceptaPrivacidad}` → **201** `{token, tipo:"Bearer", expiraEn, cliente:{id, dni, nombres, apellidos, correo, telefono}}` (sesión iniciada) · 409 `DNI_REGISTRADO` (campo `dni`) / `CORREO_REGISTRADO` (campo `correo`) |
| POST | `/api/auth/clientes/login` | público (10/min por IP) | `{usuario (DNI o correo), contrasena}` → mismo cuerpo · 401 `CREDENCIALES_INVALIDAS` «DNI, correo o contraseña incorrectos.» · 423 `USUARIO_BLOQUEADO` · 403 `USUARIO_INACTIVO` |
| GET / PUT | `/api/auth/clientes/yo` | CLIENTE | `cliente` / editar `{nombres, apellidos, correo, telefono}` (el DNI no se cambia) → `cliente` · 409 `CORREO_REGISTRADO` |
| POST | `/api/auth/clientes/cambiar-contrasena` | CLIENTE | `{actual, nueva, confirmacion}` → `{mensaje}` (igual que el de administradores) |

Desde la v2 `/api/auth/yo`, `/api/auth/cambiar-contrasena` y `/api/admin/**` exigen rol ADMIN o RECEPCION (un token
CLIENTE recibe 403 `SIN_PERMISO`); `/api/auth/clientes/**` (salvo registro y login) exige rol CLIENTE (un token de
administrador recibe 403).

OpenAPI en `/swagger-ui.html`; Actuator `/actuator/health` e `/actuator/info`.

### 3.3 Reglas de negocio y seguridad
- **Contraseñas:** BCrypt fuerza 12. Política: 8 a 64 caracteres, al menos una mayúscula, una minúscula y un número;
  además no más de 72 bytes (límite de BCrypt) ni espacios al inicio o al final.
- **Login:** el usuario se compara sin espacios y en minúsculas. Error de credenciales: 401 `CREDENCIALES_INVALIDAS`
  con el mismo mensaje exista o no el usuario («Usuario o contraseña incorrectos.»). Si el usuario no existe se compara
  igual contra un hash señuelo, para que el tiempo de respuesta no lo delate.
- **Bloqueo:** 5 fallos seguidos bloquean 15 minutos → 423 `USUARIO_BLOQUEADO` «... Intenta de nuevo en N minutos»
  (minutos restantes, redondeados hacia arriba). Mientras dura, ni la clave correcta entra. Para no revelar qué
  cuentas existen, los usuarios inexistentes también «se bloquean» igual (`RegistroIntentosAnonimos`, en memoria, con
  tope de 10 000 entradas y limpieza). Un login exitoso reinicia el contador.
- **Usuario desactivado** con la clave correcta: 403 `USUARIO_INACTIVO`.
- **Cuentas de clientes (v2):** el JWT lleva `sub` = id de la cuenta, `usuario` = correo, `nombre` = «Nombres
  Apellidos», `rol` = `CLIENTE` y el claim **`dni`** (`UsuarioToken.dni` de `comun`; nulo en administradores). Login por
  DNI o correo (sin espacios, en minúsculas) con el mismo bloqueo (5 fallos → 15 min, también para cuentas inexistentes,
  en `RegistroIntentosAnonimos` con el prefijo `cliente:` para no mezclarse con el panel) y el mismo hash señuelo. Las
  validaciones usan los mensajes del formulario de reserva (SPEC §3): `@Dni`, `@NombrePersona`, `@Celular`, correo
  obligatorio de hasta 100 caracteres, contraseña segura, confirmación (`@ContrasenasCoinciden`, ahora genérico para
  cualquier solicitud con confirmación) y términos + privacidad obligatorios. Se guarda el correo en minúsculas, los
  nombres en Título y el celular normalizado a 9 dígitos. Dos registros simultáneos con el mismo DNI o correo chocan en
  el índice único y el segundo recibe el mismo 409. `/yo` relee la cuenta: si se desactivó, 401.
- **Autoprotección del ADMIN:** no puede quitarse su propio rol ADMIN ni desactivarse (422 `AUTOPROTECCION`), y
  siempre debe quedar al menos un ADMIN activo (422 `ULTIMO_ADMIN`).
- **Unicidad:** usuario y correo únicos → 409 `USUARIO_DUPLICADO` / `CORREO_DUPLICADO`. Nombres en Título, correo en
  minúsculas.
- **Validaciones** (400 `VALIDACION` con errores por campo): nombres y apellidos `@NombrePersona` de `comun`, usuario
  `^[a-z][a-z0-9._]{3,29}$`, correo válido con dominio con punto y hasta 100 caracteres, rol obligatorio,
  contraseña segura, confirmación igual a la nueva (error en el campo `confirmacion`), contraseña actual correcta,
  nueva distinta de la actual, rango de fechas de la bitácora coherente.
- **Seguridad HTTP:** `SeguridadComun.configurarBase` + reglas: login público, `/api/auth/**` con token,
  `/api/admin/**` ADMIN o RECEPCION y `@PreAuthorize("hasRole('ADMIN')")` en usuarios y bitácora (RECEPCION recibe
  403 `SIN_PERMISO`); todo lo demás cerrado. Sin el usuario por defecto de Spring Security.

### 3.4 Bitácora y eventos
- **Consume** `auditoria.accion` (`AccionAuditadaEvento`) desde la cola durable `auth.auditoria` (con DLQ de `comun`),
  reintentos 3 veces y `default-requeue-rejected: false`. Es **idempotente**: guarda `evento_procesado` y la fila en la
  misma transacción; una reentrega con el mismo `idEvento` se ignora; un evento sin id va a la cola de muertos. Los
  textos se recortan al tamaño de su columna.
- **Acciones propias** (se guardan directo en la tabla, sin pasar por RabbitMQ): `LOGIN_EXITOSO`, `LOGIN_FALLIDO`,
  `LOGIN_BLOQUEADO`, `LOGIN_INACTIVO`, `CAMBIAR_CONTRASENA`, `CREAR_USUARIO`, `EDITAR_USUARIO` (con el detalle de qué
  cambió, p. ej. `rol: RECEPCION -> ADMIN`), `ACTIVAR_USUARIO`, `DESACTIVAR_USUARIO`, `RESTABLECER_CONTRASENA`, con
  actor, rol e IP (primera de `X-Forwarded-For`). De los clientes (rol `CLIENTE`, actor = correo, entidad
  `cuenta_cliente`): `REGISTRAR_CUENTA`, `EDITAR_CUENTA`, `CAMBIAR_CONTRASENA` y `LOGIN_CLIENTE_BLOQUEADO` (los logins
  normales de clientes no se anotan, para no llenar la bitácora).
- No publica eventos.

### 3.5 Pruebas (86 al 19/11/2026; contra `alcanfores_auth_test` con Flyway, Rabbit y Eureka apagados)
Login correcto (token válido, `ultimoAcceso`, bitácora), clave incorrecta, usuario inexistente con el mismo mensaje,
bloqueo al 5.º intento, minutos restantes y desbloqueo con el reloj adelantado (`Tiempo.fijar`), bloqueo de usuario
inexistente, validaciones y JSON mal formado, usuario inactivo; `/yo` con token, sin token, vencido, alterado y de
usuario desactivado; cambio de contraseña correcto, actual incorrecta, débil, confirmación distinta e igual a la actual;
CRUD de usuarios: lista con filtros y paginación saneada, 403 para RECEPCION, 401 sin token, creación normalizada,
409 por usuario y por correo, 400 por campo, rol inexistente, autoprotección (quitarse el rol y desactivarse), edición
de otro con detalle en la bitácora, 404, activar y desactivar, restablecer contraseña que desbloquea; bitácora:
consumidor idempotente, evento sin id, recorte de textos, filtros, rango invertido, fecha mal escrita, opciones, 403.
**v2 · `CuentaClienteIntegracionTest` (13):** registro 201 con token cuyo `dni`, rol y nombre se leen con `JwtServicio`
(datos normalizados, hash BCrypt, bitácora `REGISTRAR_CUENTA`), DNI y correo repetidos (409 con el campo), 9 campos
inválidos a la vez (400 con los mensajes del SPEC §3), confirmación distinta, login por DNI y por correo, 401 igual con
clave mala o cuenta inexistente, bloqueo al 5.º intento (423, ni la clave correcta entra, desbloqueo a los 16 min con el
reloj adelantado), cuenta inexistente también «bloqueada», cuenta desactivada (403 al entrar y 401 en `/yo`), `/yo` y
edición (correo ocupado 409, 400 por campo), cambio de contraseña (actual mala, igual a la actual, correcto y login con
la nueva) y permisos cruzados (cliente → 403 en `/api/auth/yo`, `/api/auth/cambiar-contrasena`, `/api/admin/usuarios` y
`/api/admin/bitacora`; administrador → 403 en `/api/auth/clientes/yo`; sin token → 401), y el login del panel no acepta
cuentas de clientes. Unitarias: último ADMIN (Mockito), registros simultáneos que chocan en el índice único (409 DNI o
correo), contraseña de más de 72 bytes, registro de intentos anónimos, política de contraseñas (13 casos), semilla.
Cobertura JaCoCo: 96,3 % de instrucciones en total; capa `servicio` 96,4 % (ramas 84,7 %).

---

## 4. Decisiones
- Filtros del gateway como `WebFilter` ordenados (no `GlobalFilter`) para cubrir también lo que no se enruta.
- `/interno/**` responde 404 (no 403) para no confirmar que la ruta existe.
- Circuit breaker configurado por código (`Customizer`), con tiempos distintos para las rutas de archivos.
- Bloqueo también para usuarios inexistentes, para que el 423 no sirva para adivinar cuentas.
- `GET /api/admin/usuarios/{id}` y `GET /api/admin/bitacora/opciones` se agregaron (no estaban en el SPEC) para el panel.
- En pruebas del gateway se usa el `SimpleDiscoveryClient` (descubrimiento activo, Eureka apagado) para probar el
  enrutamiento real contra un servicio simulado.
- v2: las cuentas de clientes viven en auth-service (tabla aparte de `administrador`) y el vínculo con las reservas es
  solo el DNI del token: reservas-service no consulta a auth (no hay ruta interna nueva).
- v2: el gateway rechaza con 403 un token CLIENTE en `/api/admin/**` antes de llegar al servicio; en `/api/cliente/**`
  solo valida el token (el rol lo exige reservas-service).
