# Alcanfores · Front-end (React 18 + Vite + TypeScript)

Portal público de reservas, pasarela «Alcanfores Pay · beta», login y armazón del panel del **Complejo Deportivo Los Alcanfores**.
Contrato con el back: `docs/SPEC.md` (§3 validaciones, §5 endpoints, §6 front). Identidad visual: prototipo aprobado
`C:\xampp\htdocs\alcanfores-diseno` (mismo sistema de clases, colores, radios, sombras, tipografías, ilustraciones y bustos).

## Arrancar

```bash
npm install
npm run dev:mock     # http://localhost:5186 con el back-end SIMULADO (MSW, VITE_MOCK=true; solo v1)
npm run dev:v2       # modo HÍBRIDO de desarrollo: MSW simula solo los endpoints NUEVOS de la v2 y el resto va al back real
npm run dev          # http://localhost:5186 contra el back real (proxy /api → http://localhost:8086)
npm run build        # tsc -b (estricto) + vite build → dist/
npm run lint         # ESLint sin advertencias
npm run capturas     # (con dev:mock corriendo) recorre todo y guarda capturas 390/1440 en ../docs/capturas-front
node scripts/capturas-v2.mjs [panel|portal|todo] [DNI]   # (con npm run dev y el back v2) capturas reales de la v2 en ../docs/capturas-reales/v2
```

Variables (`.env.example`): `VITE_MOCK` (true = MSW) y `VITE_API_URL` (por defecto `/api`).

## Stack

React 18 · Vite 6 · TypeScript estricto (`strict`, `noUncheckedIndexedAccess`) · Tailwind CSS 3.4 · React Router 6 (`createBrowserRouter`,
carga diferida por página) · react-hook-form + Zod (`@hookform/resolvers`) · Axios con interceptores · Context API · lucide-react 0.469
(la misma versión del prototipo) · dayjs (es, America/Lima) · MSW 2 (solo con `VITE_MOCK=true`; no entra al build) · Playwright (capturas).
Tipografías autoalojadas con @fontsource (Barlow Semi Condensed 600/700/800 + itálicas, Inter 400–700, subconjunto latino).

## Estructura (SPEC §6)

```
src/
  api/          cliente.ts (Axios + interceptores + ErrorApi) · servicios.ts (auth, catálogo, reservas, documentos, pagos, pasarela, panel) · tipos.ts (JSON del SPEC)
  componentes/
    ui/         componentes reutilizables (portal y panel) → ver «Componentes UI»
    portal/     MarcoPortal, Asistente (stepper, tira de fechas, chips de turno, resumen…), Canchas (tarjeta, reseñas), EstadoReserva
    panel/      PanelLayout (barra lateral + barra superior) y CabeceraPagina
  contexto/     AuthContext · ReservaContext (asistente de 3 pasos) · ToastContext
  esquemas/     comunes.ts (mensajes EXACTOS del SPEC §3) · reserva.ts · otros.ts (consulta, cancelación, reseña, login, tarjeta, Yape beta)
  hooks/        useCarga · useDatosPublicos (complejo, canchas, métodos) · useCuentaRegresiva · useErroresServidor · useTituloPagina
  mocks/        bd.ts (datos simulados) · manejadores.ts (MSW) · navegador.ts
  paginas/portal/  Inicio · Canchas · CanchaDetalle · reservar/(Reservar, Paso1, Paso2, Paso3) · ReservaListo · MiReserva · Pasarela · PagoResultado · Legales
  paginas/panel/   Login · Hoy · Reservas (+ReservaDetalle) · NuevaReserva · Agenda · Canchas (+CanchaFormulario) · Pagos · Clientes ·
                   Reportes · Notificaciones · Configuracion (tarjetas) · Feriados · cancha/ (ficha de 3 apartados) · Usuarios · Bitacora · EstadoSistema · Cuenta · esquemasPanel.ts
  rutas/        router.tsx · RutaProtegida.tsx · menuPanel.ts (menú, roles y bustos del panel)
  estilos/      tailwind.css · alcanfores.css + panel.css (portados del prototipo) · app.css (piezas nuevas) · utilidades.css
  utilidades/   fecha.ts (Lima) · formato.ts · documentos.ts (RUC módulo 11, Luhn, marcas, bytes mágicos) · recursos.ts · cx.ts
```

## Estilos: cómo se ve igual al prototipo

- `estilos/alcanfores.css` y `estilos/panel.css` son el CSS del prototipo **portado** (mismas clases: `btn`, `campo`, `control`, `chip-turno`,
  `resumen`, `ticket`, `kpi`, `tabla`, `ma-*`, `mt-*`…). Las container queries del prototipo se pasaron a media queries con los mismos cortes
  (768 / 1024 / 1280) y las capas (modal, toast, filtros) quedan `position: fixed`.
- Tailwind (`tailwind.config.ts`) trae los mismos tokens: colores `noche`, `verde`, `grass`, `profundo`, `suave`, `menta`, `amarillo`,
  `amarillo-tinte`, `amarillo-ocre`, `gris`, `rojo`, `borde`, `texto`, `superficie`; fuentes `font-titular` / `font-sans`; radios
  `rounded-8/10/12/14/16/24/pill`; sombras `shadow-1/2/flota/modal/barra`. Responsive: `md:` 768 · `lg:` 1024 · `xl:` 1280.
- Clases útiles del sistema: tipografía `t-display t-h1 t-h2 t-h3 t-h4 t-sec t-mini kicker kicker-raya tnum`; estructura `contenedor card card-pad
  card-cab kpis form-grid dos grupo-botones`; visibilidad `solo-movil` (< 1024) `solo-escritorio` `solo-telefono` (< 768) `solo-tablet-arriba`.
- Reglas: íconos + ilustraciones, texto mínimo (REGLAS_TEXTO del prototipo), sin emojis, un solo botón `primario` verde por vista,
  rojo solo para error/rechazo, amarillo nunca como texto sobre blanco. Portal en 12 h («8:00 p. m.»), panel en 24 h («20:00»).

## Componentes UI (`@/componentes/ui`)

Todos se importan desde `@/componentes/ui` (barrel `index.ts`).

| Componente | Uso |
|---|---|
| `Boton` | `<Boton variante="primario" tamano="lg" icono={Plus} cargando={enviando} textoCargando="Guardando…" bloque>Guardar</Boton>` · variantes `primario secundario oscuro fantasma peligro peligro-contorno whatsapp claro contorno-claro` · solo ícono: `<Boton icono={X} etiqueta="Cerrar" />` |
| `BotonEnlace` / `BotonExterno` / `Enlace` | Igual que `Boton` pero navega (`to`) o abre un `href` (PDF, WhatsApp). `Enlace` = texto verde subrayado. |
| `CampoTexto` | Input con etiqueta, ayuda, error, `prefijo`, `icono`, `sufijo`, `accion` (botón dentro: lupa, ojo), `valido`, `lectura`, `contador`. Compatible con `{...register('campo')}`. |
| `Selector` | `<Selector label="Cancha" opciones={[{valor:'1',texto:'Cancha 1'}]} vacio="Todas" {...register('idCancha')} />` |
| `AreaTexto` | Textarea con `contador={{ actual, max }}`. |
| `Casilla` | Checkbox/radio (`radio`) con la caja del sistema; el marcado se pinta solo (`:checked`). |
| `Interruptor` | Switch accesible (`role=switch`): `<Interruptor activo={x} alCambiar={setX}>Activa</Interruptor>` |
| `Segmentado` | Grupo de opciones en píldora (`role=radiogroup`). |
| `Modal` | `abierto alCerrar titulo subtitulo acciones ilustracion icono tono busto franja variante="hoja" tamano`. Foco atrapado, Esc, devuelve el foco. **Dentro del panel se vuelve oscuro solo** (panel.css). `franja={[['Total','S/ 80.00'],['Adelanto','S/ 40.00','verde']]}` |
| `Tabla<F>` | `columnas=[{clave,titulo,alinear,celda:(f)=>…}]`, `filas`, `claveFila`, `acciones={(f)=>…}`, `insignias`, `pie`, `vacio`. En < 1024 se vuelve tarjetas automáticamente. |
| `Paginacion` | Página 0-based como el back: `<Paginacion pagina={p.pagina} totalPaginas={p.totalPaginas} totalElementos={p.totalElementos} tamanio={p.tamanio} alCambiar={setPagina} etiqueta="reservas" />` |
| `Insignia` / `InsigniaEstado` | `<InsigniaEstado dominio="reserva" estado="CONFIRMADA" />`. Dominios: `reserva estadoPago pago turno asistencia origen cancha cliente resena` (tabla `ESTADOS`). En tablas del panel se ven como píldora con punto. |
| `Etiqueta` | Tag en mayúsculas: `tono="urgente|vencido|neutro|menta|rojo"`. |
| `Alerta` | `tipo="info|exito|advertencia|error|offline"`, `titulo`, `acciones`, `alCerrar`. |
| `TarjetaKpi` | `<TarjetaKpi etiqueta="Por validar" valor={5} icono={Hourglass} tono="gol" detalle="1 urgente" barra={40} alPulsar={…} activa />` · tonos `verde gol rojo noche gris`. Agrupar en `<div className="kpis">`. |
| `Vacio` | Estado vacío / error: `ilustracion="IL-24-sin-resultados" titulo texto acciones compacto`. |
| `Cargando` / `Spinner` / `Esqueleto` | Carga de página, spinner y bloques skeleton (`ancho alto radio`). |
| `VisorImagen` | Visor a pantalla completa con miniaturas, zoom, teclado y deslizar: `<VisorImagen imagenes={[{url,leyenda}]} indice={i} alCambiar={setI} alCerrar={…} />` (sirve para comprobantes del panel). |
| `Estrellas` / `EstrellasEntrada` | Solo lectura (admite medias) y selector 1–5 accesible. |
| `CodigoReserva` | Ticket con el código grande + Copiar (`claro`, `pequeno`, `acciones`). |
| `LineaTiempo` | `hitos=[{titulo, detalle, fecha, estado:'hecho|actual|pendiente|error|cancelado', icono}]`, `horizontal`. |
| `SubidaImagen` | Uploader con vista previa, cámara en móvil, arrastrar y validación (JPG/PNG por bytes mágicos, ≤ 5 MB, ≥ 200 px). |
| `Pestanas`, `Avatar`, `ListaDatos` | Pestañas con contador, avatar de iniciales, lista `dt/dd`. |
| `Logo`, `Ilustracion`, `Busto`, `PatronCancha`, `IconoDeporte` | Marca: `<Ilustracion id="IL-12-confirmada" />`, `<Busto id="BU-rosa-panel" />`, `<IconoDeporte deporte="VOLEY" />`. |

Panel (`@/componentes/panel/PanelLayout`): `PanelLayout` (armazón), `CabeceraPagina` (`titulo subtitulo acciones busto volver`) y
`usePanel()` → `{ porValidar, refrescarContador }` para el contador del menú y la campana.

Hooks: `useCarga(fn, clave)` (carga con `datos/error/cargando/recargar`), `useCuentaRegresiva(isoLima)`, `aplicarErroresServidor(error, setError, campos, mapa)`
(pinta `errores[].campo` del back en react-hook-form), `useToast()` (`exito/info/advertencia/error`), `useAuth()` (`usuario, tieneRol, salir`).

## Cómo agregar una pantalla del panel (siguiente agente)

1. El menú, los roles y los bustos ya están en `src/rutas/menuPanel.ts` (Hoy, Reservas, Agenda, Nueva reserva, Pagos y comprobantes,
   Clientes, Notificaciones, Canchas, Reportes, Usuarios, Bitácora, Configuración, Estado del sistema).
2. Crea la página en `src/paginas/panel/` y regístrala en `PAGINAS_PANEL` de `src/rutas/router.tsx` con su `ruta`
   (`'/admin/reservas': perezosa(() => import('@/paginas/panel/Reservas'))`). Las subrutas (`/admin/reservas/ALC-XXXXXX`) caen en la misma
   página (ruta `*`): léelas con `useParams()['*']` o agrega rutas hijas.
3. Usa `CabeceraPagina` + componentes UI; los clientes HTTP van en `src/api/servicios.ts` (`panelApi`). El token, el `X-Request-Id`,
   el toast de errores y el cierre de sesión ante 401 ya los hace el interceptor.
4. Para datos simulados agrega manejadores en `src/mocks/manejadores.ts` (hoy `/api/admin/*` responde 503 «sin datos simulados», salvo el tablero).

## Panel (hecho)

- Clientes HTTP del panel: `src/api/admin/` (`index.ts` por servicio, `tipos.ts` con las respuestas reales). Archivos con token (PDF, XML, Excel,
  imagen del comprobante): `componentes/panel/archivos.ts` (`abrirArchivo`, `descargarArchivo`, `useArchivo`).
- Piezas del panel en `componentes/panel/`: `Herramientas.tsx` (barra de búsqueda, filtros laterales, chips de filtros, menú ⋯, `ModalConfirmar`,
  `useFiltrosUrl` con filtros en la URL), `ModalesReserva.tsx` (validar, rechazar, cobrar en caja, cancelar), `VisorComprobante`,
  `BloqueComprobante` (boleta/factura con lupa SUNAT) y `panel-extra.css` (se carga desde `PanelLayout`).
- Esquemas Zod del panel: `paginas/panel/esquemasPanel.ts`, con los mensajes que devuelve el back (comprobados contra el back real).
- WhatsApp ya no es ítem del menú: es una tarjeta de Configuración (`/admin/whatsapp` redirige a `/admin/configuracion/whatsapp`).
- El gateway solo acepta el `Origin` de `FRONT_URL` (5186). Si corres el panel en otro puerto, el proxy debe enviar `Origin: http://localhost:5186`.
- Capturas reales: `../docs/capturas-reales/panel/INDICE.md`. Pendientes del back: `../docs/PENDIENTES_BACK.md` (sección «Panel»).

## Validación

- Esquemas Zod en `src/esquemas` con los **mismos mensajes** del SPEC §3 (`MSJ` en `comunes.ts`).
- Formularios con `mode: 'onTouched'`: se valida al salir del campo y al enviar; luego en cada cambio.
- Errores del back (`errores[].campo`, p. ej. `cliente.dni`) se pintan en el campo correcto; si el error es de un paso anterior del asistente,
  se vuelve a ese paso con el error marcado.
- El navegador también revisa el comprobante como el back: bytes mágicos JPG/PNG, ≤ 5 MB, lado mínimo 200 px.

## Modo simulado (MSW)

Datos coherentes con la semilla: 4 canchas, reseñas, turnos de hoy + 14 días (≈ 22 % ocupados «por otros»), bloqueos y reservas.
Se guardan en `localStorage` (`alc-mock-bd`) y se regeneran cada día. Consola: `alcanforesMock.reiniciar()`.

| Para probar | Dato |
|---|---|
| Panel | `rhuaman` / `Alcanfores2026` (ADMIN) · `mtorres` / `Recepcion2026` (RECEPCION) · `docente` / `UTP2026` · 5 fallos → bloqueo 15 min (423) |
| Mi reserva confirmada (cancelar con devolución) | `ALC-7K3Q9P` + DNI `72345678` |
| Comprobante observado (reenviar) | `ALC-M4R8TB` + DNI `70112233` |
| En revisión | `ALC-C4X7MS` + DNI `76543210` |
| Partido jugado (calificar) | `ALC-H2P6WD` + DNI `45678912` → `/canchas/1?resena=ALC-H2P6WD` |
| Cancelada | `ALC-W6R2NC` + DNI `41236587` |
| RENIEC | cualquier DNI → nombres simulados · `00000000` → 404 (se escribe a mano) · `11111111` → 503 |
| SUNAT | cualquier RUC válido (módulo 11), p. ej. `20000000001` |
| 409 TURNO_OCUPADO | Cancha 2 · mañana · 9:00 p. m. (se ve libre; al confirmar «otra persona» se adelanta) |
| Pasarela beta | tarjetas del SPEC (4111…, 5555…, 3782…, 4000 0027 6000 3184 → 3-D Secure `123456`, …0002 / …9995 / …0069 rechazadas) · Yape `123456` aprueba, `000000` rechaza |
| Mercado Pago | apagado (como sin `MP_ACCESS_TOKEN`); `localStorage.setItem('alc-mock-mp','1')` lo activa y se aprueba en la 2.ª consulta de `/pago/resultado` |

## Decisiones (no estaban en el SPEC)

- `POST /api/reservas` se llama al **confirmar** el paso 3 (no antes). Si el pago falla después, la reserva queda en el contexto y el
  paso 3 muestra la cuenta regresiva de `venceEn` para reintentar solo el pago (no se crea otra reserva).
- Pasarela: `anioVencimiento` se envía con 4 dígitos (`2030`) y `mesVencimiento` con 2 (`12`). En modo beta cualquier tarjeta que no sea de
  prueba se rechaza («solo funcionan las tarjetas de prueba»).
- Mensajes que el SPEC deja como «etc.»: vencimiento «Revisa la fecha de vencimiento (MM/AA).», CVV «Revisa el CVV (3 dígitos; 4 en Amex).»,
  titular «Escribe el nombre como figura en la tarjeta.», puntuación «Elige de 1 a 5 estrellas.», código 3DS/Yape «El código de aprobación tiene 6 dígitos.»
- Reenvío de comprobante desde «Mi reserva»: el modal pide Boleta o Factura (RUC con consulta SUNAT). El back emite con los datos de ESE
  envío (si no van, boleta), así que el asistente guarda la facturación por código en `localStorage` (`alc-facturacion`) y la prellena;
  «Pagar con tarjeta» desde «Mi reserva» también la usa. Ver `docs/PENDIENTES_BACK.md` §Portal 1.
- Yape/Plin se muestran sin QR (no hay QR real del complejo); número grande + Copiar + monto, como el prototipo pero sin el «QR demo».
- `linea[].evento` reales del back: `REGISTRADA`, `COMPROBANTE_RECIBIDO`, `OBSERVADO`, `PAGO_VALIDADO`, `CONFIRMADA`, `ASISTENCIA` (el `detalle` y
  `asistencia` dicen si vino) y `CANCELADA`; se traducen con un diccionario (otro valor se muestra legible).
- `GET /api/pagos/intentos/{ref}/estado` → `{referencia, proveedor, estado, tipoPago, monto, codigoReserva, motivoRechazo, expiraEn, idPago, comprobante}`;
  `tarjetasPrueba[]` → `{numero, marca, resultado}`; `GET /api/pagos/reserva/{codigo}` trae `comprobante` y `notaCredito` con `urlPdf`/`urlXml`.
- Multipart de `/api/pagos/comprobante`: `comprobante` va como TEXTO JSON (el back lo lee con `@RequestParam String`; como Blob se perdía la factura).
- Los cambios de pago llegan a la reserva por RabbitMQ con unos segundos de retraso: «Listo» y «Mi reserva» vuelven a consultar (hasta ~12 s)
  tras subir un comprobante, pagar en la pasarela o cancelar (nota de crédito).
- Capturas con el back real: `docs/capturas-reales/portal/` (índice en `INDICE.md`).
- Ruta del panel para la reserva manual: `/admin/nueva-reserva`; cambio de contraseña: `/admin/cuenta`.

## Versión 2 (SPEC_V2, 19-nov-2026)

Regla de oro: equilibrar funcionalidad con simpleza (una acción principal por pantalla, lo avanzado plegado en «Más opciones»).

| Punto | Dónde |
|---|---|
| Barra lateral rediseñada (grupos, activo con raya verde, pie = tarjeta del usuario + «Plegar menú»; plegada = íconos con tooltip en portal) | `componentes/panel/PanelLayout.tsx` (`Navegacion`, `pie`) · estilos al final de `panel-extra.css` |
| Menú sin «Reseñas» (`/admin/resenas` → `/admin/canchas`) | `rutas/menuPanel.ts`, `rutas/router.tsx` |
| Campana: modal «Te falta revisar estos comprobantes» con «Validar» directo | `componentes/panel/ModalAvisos.tsx` (pagos PENDIENTE + reservas EN_REVISION para cancha y horario) |
| Configuración en tarjetas con ilustración → `/admin/configuracion/{complejo,contacto,reglas,feriados,comprobantes,whatsapp}` | `paginas/panel/Configuracion.tsx` (grupos en `esquemasPanel.ts` → `GRUPOS_CONFIG`) |
| Feriados y días no laborables (calendario anual, feriados de Perú, días con motivo y canchas) | `paginas/panel/Feriados.tsx` |
| Canchas en tarjetas + ficha de 3 apartados `/admin/canchas/{id}[/horario|/configuracion]` | `paginas/panel/Canchas.tsx` y `paginas/panel/cancha/*` |
| Editor VISUAL: el detalle público real (`FichaCancha`, `data-edit`) escalado + panel derecho por parte; reseñas de la cancha | `cancha/EditorVisual.tsx`, `cancha/PanelesEditor.tsx`, `componentes/portal/FichaCancha.tsx` |
| Horario por día, duración, precios, «copiar a todos», vista previa (POST vista-previa; si falla, cálculo local) y feriados | `cancha/HorarioCancha.tsx`, `utilidades/horario.ts` |
| Agenda: sin «Generar turnos»; «Bloquear horas» (varias canchas, desde–hasta, varios días, también desbloquear); feriados y reservas de varios turnos («1 de 2») | `paginas/panel/Agenda.tsx` |
| Varios turnos (portal y panel): `idsTurno`, total = suma, `horarioTexto` | `contexto/ReservaContext.tsx` (`turnos[]` → `turno` compuesto), `Paso1.tsx`, `NuevaReserva.tsx`, `utilidades/fecha.ts → textoHorario` |
| Paso 1: tarjeta de la cancha elegida + modal de tarjetas; tira de días con flechas + calendario de mes con feriados y cerrados | `componentes/portal/SelectorCancha.tsx` (también en «Nueva reserva»), `componentes/portal/Fechas.tsx` |
| Cuentas de clientes: `/ingresar`, `/crear-cuenta`, `/mi-cuenta[/resenas|/datos]`; paso 2 lleno con la cuenta; reseña con sesión | `contexto/CuentaContext.tsx`, `paginas/portal/cuenta/*`, `paginas/portal/CanchaDetalle.tsx` |
| Reintentos: un GET con 502/503/504 o sin red se reintenta 3 veces (0,8 · 1,6 · 3,2 s) y se ve «Actualizando el sistema, un momento…» | `api/cliente.ts` (`reintentable`), `componentes/ui/AvisoReintento.tsx` |

Decisiones v2:
- Dos sesiones que no se mezclan: la del panel (`alc-sesion`) y la del cliente (`alc-cuenta`). El token del cliente solo viaja a
  `/auth/clientes/**`, `/cliente/**`, `POST /reservas` y `POST /catalogo/canchas/{id}/resenas` (`rutaDeCliente` en `api/cliente.ts`).
- «Mis reservas» abre el detalle de siempre (`/mi-reserva`) pasando el DNI de la cuenta en el estado de la navegación (no en la URL).
- El editor visual pinta el detalle a 980 px de ancho y lo escala con `zoom` (sin iframe); en < 1280 px el panel es una hoja inferior.
- Un solo «Publicar cambios» a la vista: con un panel de texto abierto vive en su pie; fotos y reseñas se guardan al instante.
- Capturas reales de la v2: `../docs/capturas-reales/v2/INDICE.md`. Pendientes del back: `../docs/PENDIENTES_BACK.md` (sección «v2»).
