# Guía de construcción (para cada agente que construye un módulo)

Sistema REAL de reservas con microservicios (proyecto final UTP). Se califica que **funcione** y que **todos los campos
estén validados**; «mientras más detalle, más nota». Varios agentes trabajan **en paralelo** en el mismo repositorio.

## Antes de empezar
1. Lee COMPLETO `docs/SPEC.md` (contrato: endpoints, JSON, tablas, eventos, validaciones y mensajes). Es la fuente de verdad.
2. Lee la librería compartida `backend/comun` (ya construida e instalada en `~/.m2` como `pe.alcanfores:comun:1.0.0`). ÚSALA:
   - Errores: `NegocioExcepcion` (fábricas `noEncontrado/conflicto/regla/validacion/noDisponible`), `ErrorApi`, manejadores autoconfigurados.
   - Seguridad: `JwtServicio`, `ComponentesSeguridad` (bean), `SeguridadComun.configurarBase(http, seg)` (ver su javadoc), `SeguridadUtil`, `UsuarioToken`, `InterceptorInterno` (bean, para `RestClient` interno), `FiltroIdSolicitud`.
   - Validación: `@Dni @Ruc @Celular @NombrePersona @CodigoReserva @SinEnlaces`, `Documentos`, `Texto`.
   - Utilidades: `Tiempo` (reloj Lima; usa SIEMPRE `Tiempo.ahora()/hoy()` para poder fijar el reloj en pruebas con `Tiempo.fijar`), `Dinero` (IGV incluido, `enLetras`, `porcentaje`), `PaginaRespuesta`.
   - Eventos: records en `pe.alcanfores.comun.eventos`, `Eventos.cola(...)/enlace(...)`, `PublicadorEventos` (bean), `Auditor` (bean).
3. **No modifiques** `backend/comun`, `backend/pom.xml` ni carpetas de otros módulos. Si te falta algo de `comun`, créalo dentro de tu
   módulo y menciónalo en tu entrega como «propuesta para comun». Tu zona: `backend/<modulo>/` y `docs/servicios/<modulo>.md`
   (el front: `frontend/`). **No hagas commits** (los hace el integrador).

## Entorno (Windows; Git Bash o PowerShell)
- JDK 17: `export JAVA_HOME="/c/Program Files/Java/jdk-17"` (el `java` del PATH es 23).
- Compila y prueba SOLO tu módulo: `mvn -B -f backend/<modulo>/pom.xml verify` (nunca el reactor completo ni `-am`: el parent se lee
  por `relativePath` y `comun` viene de `~/.m2`). Casi todas las dependencias ya están en `~/.m2` (Spring Cloud 2025.0.3, JPA,
  Security, AMQP, Eureka, Gateway WebFlux, Resilience4j, OpenFeign, LoadBalancer, Flyway + flyway-mysql, mysql-connector-j,
  springdoc 2.8.17, openhtmltopdf-pdfbox 1.1.114, zxing javase 3.5.4, poi-ooxml 5.5.1, Thymeleaf, Mail, H2, spring-rabbit-test).
  Las versiones están en el `dependencyManagement` del parent: no pongas versiones a mano.
- Base de datos local: **MariaDB 10.4** de XAMPP en `localhost:3306`, usuario `root` sin clave. Tu BD `alcanfores_<x>` y la de
  pruebas `alcanfores_<x>_test` ya existen. Producción será **MySQL 8.4**: escribe SQL válido en ambos (columna generada
  `GENERATED ALWAYS AS (...) STORED`; `JSON` sirve en ambos; evita `DEFAULT (expresión)`, CHECK con subconsultas o funciones solo de MySQL 8).
  Driver `mysql-connector-j`; Flyway (`flyway-core` + `flyway-mysql`), scripts en `db/migration` (`V1__esquema.sql`, …).
  Si Flyway se niega a trabajar con MariaDB 10.4, NO cambies de motor: repórtalo y usa H2 en modo MySQL solo en pruebas.
- **RabbitMQ y Eureka NO están corriendo en esta PC**: las pruebas no deben necesitarlos. En `src/test/resources/application-test.yml`:
  `spring.rabbitmq.listener.simple.auto-startup: false`, `management.health.rabbit.enabled: false`, `eureka.client.enabled: false`,
  `spring.cloud.discovery.enabled: false`. Usa `@MockitoBean` para `PublicadorEventos`, `RabbitTemplate` y los clientes de otros
  servicios (o `MockRestServiceServer`). Prueba los consumidores llamando directo a sus métodos.
- Para probar a mano: arranca tu servicio (`mvn -f backend/<modulo>/pom.xml spring-boot:run` con `eureka.client.enabled=false`) y usa
  curl; para rutas admin genera un token con `JwtServicio` y el secreto por defecto de `AlcanforesPropiedades`.

## Convenciones obligatorias
- Spring Boot 3.5.16 (parent), Java 17, paquete `pe.alcanfores.<x>`. Capas: `controlador / servicio (interfaz + impl) / repositorio /
  modelo / dto (records con Bean Validation y los mensajes del SPEC §3) / mapeador / config / evento / cliente`. Lombok permitido.
  Inyección por constructor. SOLID. Código y comentarios en español.
- `application.yml`: `spring.application.name=<modulo>`, `server.port`, datasource por variables (`DB_HOST`, `DB_PORT`, `DB_USUARIO`,
  `DB_CLAVE`, defaults `localhost/3306/root/` vacío; URL con `?serverTimezone=America/Lima&useUnicode=true&characterEncoding=UTF-8`),
  `spring.jpa.open-in-view: false`, `ddl-auto: validate`, `spring.jackson.time-zone: America/Lima`, Flyway,
  `eureka.client.service-url.defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}`, `eureka.instance.prefer-ip-address: true`,
  Rabbit por variables (`RABBIT_HOST/RABBIT_PORT/RABBIT_USUARIO/RABBIT_CLAVE`, defaults `localhost/5672/guest/guest`) con reintentos
  del listener (3 intentos, `default-requeue-rejected: false` → DLX), `alcanfores.jwt.secreto: ${JWT_SECRETO:<default dev de comun>}`,
  `alcanfores.interno-token: ${INTERNO_TOKEN:token-interno-solo-para-desarrollo-local}`, `alcanfores.front-url: ${FRONT_URL:http://localhost:5186}`,
  `alcanfores.public-api-url: ${PUBLIC_API_URL:http://localhost:8086}`, `management.endpoints.web.exposure.include: health,info`,
  springdoc. En `main`: `TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))`.
- Clientes a otros servicios: `RestClient` con `@LoadBalanced RestClient.Builder` + `InterceptorInterno`, URL `http://<servicio>`,
  timeout 3 s, circuit breaker Resilience4j (`spring-cloud-starter-circuitbreaker-resilience4j`) → `NegocioExcepcion.noDisponible`.
- Seguridad: `SecurityFilterChain` con `SeguridadComun.configurarBase` + reglas del SPEC; `@EnableMethodSecurity`; ADMIN-only con `@PreAuthorize("hasRole('ADMIN')")`.
- Eventos: publícalos DESPUÉS del commit (`ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` que llama a
  `PublicadorEventos`). Consumidores idempotentes con la tabla `evento_procesado(id_evento CHAR(36) PK, tipo, procesado_en)`.
- Auditoría: toda acción administrativa que cambia datos llama a `auditor.registrar(accion, entidad, id, detalle)`.
- OpenAPI: `@Tag/@Operation` en español en cada controlador.
- Pruebas: JUnit 5 + Mockito + AssertJ + MockMvc (`@WebMvcTest` con la seguridad real importada o `@SpringBootTest` + perfil test).
  Cubre las del SPEC §7 que te tocan, validaciones (400 con errores por campo), 401/403. Plugin JaCoCo en tu pom; meta ≥ 70 % en la
  capa de servicio.
- NO escribas Dockerfile ni docker-compose (los hace el integrador).
- Sin TODOs ni código muerto. Mensajes de error en español, amables y concretos. Sin emojis.

## Entrega (tu último mensaje)
1. Qué construiste (endpoints, tablas, eventos publicados/consumidos, tareas programadas).
2. Resultado REAL de `mvn verify` (n.º de pruebas, fallos, cobertura JaCoCo de `target/site/jacoco/index.html`).
3. Cómo lo arrancaste y qué probaste a mano (comandos y respuestas resumidas).
4. Desviaciones del SPEC y propuestas para `comun`.
5. Pendientes o riesgos.
Escribe además `docs/servicios/<modulo>.md` (para el informe del curso): responsabilidad, tablas, endpoints, eventos, decisiones y pruebas.
