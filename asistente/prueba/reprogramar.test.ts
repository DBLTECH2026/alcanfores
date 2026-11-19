// Cambio de horario: la llamada va DIRECTO a reservas-service (actor «Canchita (IA)») y la vista previa aplica las mismas
// reglas del back (puedeReprogramar, monto menor, adelanto insuficiente, mismo horario) con sus mensajes tal cual.
import { afterEach, describe, expect, it, vi } from "vitest";
import { Ajustes } from "../src/ajustes.js";
import { ApiHttp, ErrorApi, type ApiAlcanfores } from "../src/api.js";
import { RepositorioMemoria } from "../src/bd/repositorioMemoria.js";
import { crearHerramientas } from "../src/herramientas.js";
import { instruccionesSistema } from "../src/prompt.js";
import { adelantoEnCentimos, cambioYaAplicado, centimos, MENSAJES, motivoSinCambio, plazoTrasCambio,
  revisarMontos } from "../src/reglasReprogramar.js";
import { fechaHoraLima, fechaLima, sumarDias, textoFecha } from "../src/tiempo.js";
import { ApiFalsa, COMPLEJO, configuracionPrueba } from "./apoyo.js";

const manana = sumarDias(fechaLima(), 1);
const DNI = "45678912";
const CODIGO = "ALC-ABCDEF";

type Resultado = Record<string, unknown>;

/** La herramienta reprogramar_reserva sobre una API dada, en una conversación web nueva. */
async function herramientaSobre(api: ApiAlcanfores) {
  const cfg = configuracionPrueba();
  const repo = new RepositorioMemoria();
  const conversacion = await repo.obtenerOCrearConversacion("WEB", "sesion-reprogramar-01");
  const herramientas = crearHerramientas({ api: api.paraConversacion?.(conversacion.id) ?? api, repo, ajustes: new Ajustes(api), cfg },
    { conversacion });
  const h = herramientas.find((x) => x.nombre === "reprogramar_reserva")!;
  return { cfg, conversacion, ejecutar: (args: Resultado) => h.ejecutar(args) as Promise<Resultado> };
}

describe("reprogramarReserva por HTTP: directo a reservas-service", () => {
  afterEach(() => vi.unstubAllGlobals());

  /** Detalle con el cuerpo real de GET /api/reservas/{codigo}?dni= (horas con segundos, como las serializa Jackson). */
  function detalle(extra: Resultado = {}) {
    return {
      codigoReserva: CODIGO, estado: "CONFIRMADA", estadoPago: "ADELANTO_PAGADO", venceEn: null,
      cancha: { id: 1, nombre: "Cancha 1", deporte: "FUTBOL_5" }, fecha: manana, horaInicio: "19:00:00", horaFin: "20:00:00",
      montoTotal: 80, montoAdelanto: 40, horarioTexto: "7:00–8:00 p. m.", cantidadTurnos: 1,
      turnos: [{ idTurno: 111, horaInicio: "19:00:00", horaFin: "20:00:00", precio: 80 }],
      montoPagado: 40, saldo: 40, asistencia: "PENDIENTE", metodoPagoPrevisto: "YAPE", puedeCancelar: true,
      puedeReenviarComprobante: false, cancelacion: null, reprogramaciones: 0, puedeReprogramar: true, montoEnRevision: 0, ...extra,
    };
  }

  const disponibilidad = {
    fecha: manana,
    canchas: [{
      idCancha: 1, nombre: "Cancha 1", deporte: "FUTBOL_5", turnos: [
        { idTurno: 111, horaInicio: "19:00:00", horaFin: "20:00:00", precio: 80, franja: "NOCHE", estado: "OCUPADO" },
        { idTurno: 112, horaInicio: "20:00:00", horaFin: "21:00:00", precio: 80, franja: "NOCHE", estado: "DISPONIBLE" },
      ],
    }],
  };

  /** fetch falso: el gateway sirve las lecturas y reservas-service responde el POST /reprogramar con lo indicado. */
  function servidor(reprogramar: { status: number; cuerpo: unknown }) {
    const llamadas: { url: string; metodo: string; cabeceras: Record<string, string>; cuerpo: unknown }[] = [];
    const json = (status: number, cuerpo: unknown) =>
      new Response(JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json" } });
    vi.stubGlobal("fetch", async (url: string | URL, init: RequestInit = {}) => {
      llamadas.push({
        url: String(url), metodo: init.method ?? "GET", cabeceras: (init.headers ?? {}) as Record<string, string>,
        cuerpo: typeof init.body === "string" ? JSON.parse(init.body) : null,
      });
      const ruta = new URL(String(url)).pathname;
      if (ruta.endsWith("/reprogramar")) return json(reprogramar.status, reprogramar.cuerpo);
      if (ruta.startsWith("/api/reservas/")) return json(200, detalle());
      if (ruta === "/api/disponibilidad") return json(200, disponibilidad);
      if (ruta === "/api/catalogo/complejo") return json(200, COMPLEJO);
      return json(404, { codigo: "NO_ENCONTRADO", mensaje: "No existe." });
    });
    return llamadas;
  }

  it("con conversación: POST a RESERVAS_URL con X-Interno-Token y X-Canchita-Conversacion (no por el gateway)", async () => {
    const cfg = configuracionPrueba();
    const llamadas = servidor({ status: 200, cuerpo: detalle({ reprogramaciones: 1, puedeReprogramar: false }) });
    const r = await new ApiHttp(cfg).paraConversacion(42).reprogramarReserva(CODIGO, DNI, [112]);
    expect(r.reprogramaciones).toBe(1);
    expect(llamadas).toHaveLength(1);
    const [l] = llamadas;
    expect(l.url).toBe(`${cfg.reservasUrl}/api/reservas/${CODIGO}/reprogramar`);
    expect(l.url.startsWith(cfg.apiUrl)).toBe(false);
    expect(l.metodo).toBe("POST");
    expect(l.cabeceras).toMatchObject({
      "Content-Type": "application/json", "X-Interno-Token": cfg.internoToken, "X-Canchita-Conversacion": "c42",
    });
    expect(l.cuerpo).toEqual({ dni: DNI, idsTurno: [112] });
  });

  it("sin conversación manda el token pero no la cabecera de conversación", async () => {
    const cfg = configuracionPrueba();
    const llamadas = servidor({ status: 200, cuerpo: detalle() });
    await new ApiHttp(cfg).reprogramarReserva(CODIGO, DNI, [112]);
    expect(llamadas[0].url).toBe(`${cfg.reservasUrl}/api/reservas/${CODIGO}/reprogramar`);
    expect(llamadas[0].cabeceras["X-Interno-Token"]).toBe(cfg.internoToken);
    expect(llamadas[0].cabeceras).not.toHaveProperty("X-Canchita-Conversacion");
  });

  it("un 422 del back llega como ErrorApi con su código y su mensaje", async () => {
    servidor({ status: 422, cuerpo: { status: 422, codigo: "REPROGRAMACION_NO_PERMITIDA", mensaje: MENSAJES.limite, errores: [] } });
    const e = await new ApiHttp(configuracionPrueba()).paraConversacion(7).reprogramarReserva(CODIGO, DNI, [112])
      .catch((x: unknown) => x);
    expect(e).toBeInstanceOf(ErrorApi);
    expect(e).toMatchObject({ status: 422, codigo: "REPROGRAMACION_NO_PERMITIDA", message: MENSAJES.limite });
  });

  it("la herramienta confirma por la llamada directa; 422 y 409 llegan al modelo tal cual y 404 → «aún no disponible»", async () => {
    const casos = [
      { status: 422, cuerpo: { codigo: "REPROGRAMAR_MONTO_MENOR", mensaje: MENSAJES.montoMenor },
        esperado: { error: MENSAJES.montoMenor, codigo: "REPROGRAMAR_MONTO_MENOR" } },
      { status: 409, cuerpo: { codigo: "TURNO_OCUPADO", mensaje: "Ese turno acaba de ser reservado por otra persona." },
        esperado: { error: "Ese turno acaba de ser reservado por otra persona.", codigo: "TURNO_OCUPADO" } },
      { status: 404, cuerpo: { codigo: "NO_ENCONTRADO", mensaje: "No existe." }, esperado: null },
      { status: 200, cuerpo: detalle({ reprogramaciones: 1, puedeReprogramar: false }), esperado: { reprogramada: true } },
    ];
    for (const caso of casos) {
      const llamadas = servidor(caso);
      const { cfg, conversacion, ejecutar } = await herramientaSobre(new ApiHttp(configuracionPrueba()));
      const r = await ejecutar({ codigo: CODIGO, dni: DNI, fecha: manana, idsTurno: [112], clienteConfirmo: true });
      if (caso.esperado) expect(r).toMatchObject(caso.esperado);
      else expect(String(r.error)).toContain("aún no está disponible");
      // Las lecturas van por el gateway; solo el cambio va directo a reservas-service, con la conversación.
      const cambio = llamadas.find((l) => l.metodo === "POST")!;
      expect(cambio.url).toBe(`${cfg.reservasUrl}/api/reservas/${CODIGO}/reprogramar`);
      expect(cambio.cabeceras["X-Canchita-Conversacion"]).toBe(`c${conversacion.id}`);
      expect(llamadas.filter((l) => l.metodo === "GET").every((l) => l.url.startsWith(cfg.apiUrl))).toBe(true);
      vi.unstubAllGlobals();
    }
  });

  it("si el POST se corta por tiempo pero reservas guardó el cambio, la herramienta lo revisa y dice que quedó hecho", async () => {
    let guardado = false;
    let posts = 0;
    const json = (cuerpo: unknown) => new Response(JSON.stringify(cuerpo), { status: 200, headers: { "Content-Type": "application/json" } });
    const yaCambiada = () => detalle({
      horaInicio: "20:00:00", horaFin: "21:00:00", horarioTexto: "8:00–9:00 p. m.", reprogramaciones: 1, puedeReprogramar: false,
      turnos: [{ idTurno: 112, horaInicio: "20:00:00", horaFin: "21:00:00", precio: 80 }],
    });
    vi.stubGlobal("fetch", async (url: string | URL) => {
      const ruta = new URL(String(url)).pathname;
      if (ruta.endsWith("/reprogramar")) {
        posts++;
        guardado = true; // reservas hizo commit, pero la respuesta no llega a tiempo (AbortSignal.timeout de pedir)
        throw new DOMException("The operation was aborted due to timeout", "TimeoutError");
      }
      if (ruta.startsWith("/api/reservas/")) return json(guardado ? yaCambiada() : detalle());
      if (ruta === "/api/disponibilidad") return json(disponibilidad);
      if (ruta === "/api/catalogo/complejo") return json(COMPLEJO);
      return new Response("{}", { status: 404 });
    });
    const { ejecutar } = await herramientaSobre(new ApiHttp(configuracionPrueba()));
    const args = { codigo: CODIGO, dni: DNI, fecha: manana, idsTurno: [112], clienteConfirmo: true };
    const r1 = await ejecutar(args);
    expect(r1).toMatchObject({ reprogramada: true, horario: "8:00–9:00 p. m." });
    expect(r1).not.toHaveProperty("error");
    // El reintento no vuelve a llamar al POST ni dice «Ya cambiaste el horario una vez».
    const r2 = await ejecutar(args);
    expect(r2).toMatchObject({ reprogramada: true, yaEstabaAplicado: true, horario: "8:00–9:00 p. m." });
    expect(JSON.stringify(r2)).not.toContain(MENSAJES.limite);
    expect(posts).toBe(1);
  });
});

describe("vista previa de reprogramar_reserva con las reglas del back", () => {
  /** Reserva de mañana 7:00–8:00 p. m. (u otra hora) en la Cancha 1 con los datos de pago indicados. */
  async function preparar(datos: { montoPagado?: number; montoEnRevision?: number } = {}, hora = "19:00") {
    const api = new ApiFalsa();
    const turno = (hora: string, cancha = 1) => api.turnos.find((t) => t.fecha === manana && t.horaInicio === hora && t.idCancha === cancha)!;
    const creada = await api.crearReserva([turno(hora).idTurno], {
      dni: DNI, nombres: "Luis", apellidos: "Ficticio", telefono: "987650001", correo: "l@example.com",
    }, "YAPE");
    const reserva = api.reservas.get(creada.codigoReserva)!;
    Object.assign(reserva, { montoPagado: datos.montoPagado ?? 0, montoEnRevision: datos.montoEnRevision ?? 0 });
    const { ejecutar } = await herramientaSobre(api);
    const pedir = (horas: string[], clienteConfirmo = false) => ejecutar({
      codigo: reserva.codigoReserva, dni: DNI, fecha: manana, idsTurno: horas.map((h) => turno(h).idTurno), clienteConfirmo,
    });
    return { api, reserva, turno, pedir };
  }

  /** Mueve el inicio de la reserva a «ahora + minutos» (hora de Lima). */
  function empiezaEn(reserva: { fecha: string; horaInicio: string }, minutos: number) {
    const [fecha, hora] = fechaHoraLima(new Date(Date.now() + minutos * 60_000)).split("T");
    Object.assign(reserva, { fecha, horaInicio: hora });
  }

  it("un cambio válido muestra antes, después, pagado y saldo, avisa que es el único cambio y no toca nada", async () => {
    const { api, pedir } = await preparar({ montoPagado: 40 });
    const r = await pedir(["20:00"]);
    expect(r).toMatchObject({
      vistaPrevia: true, antes: { total: "S/ 80.00" }, despues: { total: "S/ 80.00", horario: "8:00–9:00 p. m." },
      yaPagado: "S/ 40.00", saldo: "S/ 40.00",
    });
    expect(String(r.aviso)).toContain("único cambio");
    expect(r).not.toHaveProperty("nota");
    expect(JSON.stringify(r)).not.toContain("revisa la diferencia");
    expect(api.reprogramaciones).toHaveLength(0);
  });

  it("sin pagos sigue pendiente: muestra el adelanto del nuevo horario y el plazo", async () => {
    const { pedir } = await preparar();
    const r = await pedir(["20:00", "21:00"]);
    expect(r).toMatchObject({ vistaPrevia: true, despues: { total: "S/ 160.00" }, yaPagado: "S/ 0.00", saldo: "S/ 160.00",
      adelantoNuevo: "S/ 80.00", pagarAntesDe: "11:50 p. m." });
  });

  it("sin pagos, si el nuevo horario empieza antes del plazo, hay que pagar antes de que empiece (como el back)", async () => {
    // Comprobante observado de una reserva de mañana a las 10 p. m.: el plazo quedó en mañana a las 9 p. m. (inicio − 1 h).
    const { reserva, pedir } = await preparar({}, "22:00");
    Object.assign(reserva, { estado: "PENDIENTE", estadoPago: "OBSERVADO", venceEn: `${manana}T21:00:00` });
    const dia = textoFecha(manana);
    // ReprogramadorReservas: venceEn = nuevo inicio cuando el plazo es posterior. Si no es hoy, se dice el día.
    expect(await pedir(["10:00"])).toMatchObject({ vistaPrevia: true, adelantoNuevo: "S/ 30.00", pagarAntesDe: `10:00 a. m. del ${dia}` });
    expect(await pedir(["20:00"])).toMatchObject({ vistaPrevia: true, pagarAntesDe: `8:00 p. m. del ${dia}` });
    // Con varios turnos cuenta el que empieza primero, aunque lleguen desordenados.
    expect(await pedir(["18:00", "17:00"])).toMatchObject({ vistaPrevia: true, pagarAntesDe: `5:00 p. m. del ${dia}` });
    // Si empieza a la misma hora del plazo o después, el plazo se conserva.
    expect(await pedir(["21:00"])).toMatchObject({ vistaPrevia: true, pagarAntesDe: `9:00 p. m. del ${dia}` });
    // Al confirmar, el resumen de la reserva dice el mismo plazo que la vista previa.
    expect(await pedir(["10:00"], true)).toMatchObject({ reprogramada: true, pagarAntesDe: `10:00 a. m. del ${dia}` });
    expect(reserva.venceEn).toBe(`${manana}T10:00:00`);
  });

  it("con un comprobante en revisión lo muestra aparte (cuenta como comprometido)", async () => {
    const { pedir } = await preparar({ montoEnRevision: 40 });
    const r = await pedir(["20:00"]);
    expect(r).toMatchObject({ vistaPrevia: true, yaPagado: "S/ 0.00", comprobanteEnRevision: "S/ 40.00", saldo: "S/ 80.00" });
    expect(r).not.toHaveProperty("adelantoNuevo");
  });

  describe("si el back dice que no se puede (puedeReprogramar = false) explica por qué y no hay vista previa", () => {
    it("ya cambió el horario una vez", async () => {
      const { api, reserva, pedir } = await preparar({ montoPagado: 40 });
      Object.assign(reserva, { puedeReprogramar: false, reprogramaciones: 1 });
      for (const confirmo of [false, true]) {
        const r = await pedir(["20:00"], confirmo);
        expect(r).toEqual({ error: MENSAJES.limite, codigo: "REPROGRAMACION_NO_PERMITIDA", siguientePaso: expect.any(String) });
      }
      expect(MENSAJES.limite).toBe("Ya cambiaste el horario una vez; escríbenos para ayudarte.");
      expect(api.reprogramaciones).toHaveLength(0);
    });

    it("faltan menos de 2 horas para el partido", async () => {
      const { reserva, pedir } = await preparar();
      Object.assign(reserva, { puedeReprogramar: false });
      empiezaEn(reserva, 90);
      const r = await pedir(["20:00"]);
      expect(r).toMatchObject({ error: MENSAJES.plazo, codigo: "REPROGRAMACION_NO_PERMITIDA" });
      expect(r.error).toBe("Los cambios de horario se hacen hasta 2 horas antes del partido; escríbenos para ayudarte.");
      expect(r).not.toHaveProperty("vistaPrevia");
    });

    it("el partido ya empezó", async () => {
      const { reserva, pedir } = await preparar();
      Object.assign(reserva, { puedeReprogramar: false });
      empiezaEn(reserva, -10);
      expect(await pedir(["20:00"])).toMatchObject({ error: MENSAJES.empezo, codigo: "RESERVA_NO_REPROGRAMABLE" });
    });

    it("ya se registró la asistencia", async () => {
      const { reserva, pedir } = await preparar();
      Object.assign(reserva, { puedeReprogramar: false, asistencia: "ASISTIO" });
      expect(await pedir(["20:00"])).toMatchObject({ error: MENSAJES.asistencia, codigo: "RESERVA_NO_REPROGRAMABLE" });
    });

    it("está cancelada", async () => {
      const { reserva, pedir } = await preparar();
      Object.assign(reserva, { puedeReprogramar: false, estado: "CANCELADA" });
      expect(await pedir(["20:00"])).toMatchObject({ error: MENSAJES.cancelada, codigo: "RESERVA_NO_REPROGRAMABLE" });
    });

    it("sin un motivo visible usa el texto genérico", async () => {
      const { reserva, pedir } = await preparar();
      Object.assign(reserva, { puedeReprogramar: false, reprogramaciones: 0 });
      expect(await pedir(["20:00"])).toMatchObject({ error: MENSAJES.otro, codigo: "REPROGRAMACION_NO_PERMITIDA" });
    });
  });

  it("nuevo total menor que lo pagado → mensaje de REPROGRAMAR_MONTO_MENOR, sin «el equipo revisa la diferencia»", async () => {
    const { api, pedir } = await preparar({ montoPagado: 80 });
    const r = await pedir(["10:00"]); // S/ 60 < S/ 80 pagados
    expect(r).toMatchObject({ error: MENSAJES.montoMenor, codigo: "REPROGRAMAR_MONTO_MENOR" });
    expect(r.error).toBe("El nuevo horario cuesta menos de lo que ya pagaste. Elige otro horario o acércate al complejo.");
    expect(JSON.stringify(r)).not.toContain("revisa la diferencia");
    expect(api.reprogramaciones).toHaveLength(0);
  });

  it("el comprobante en revisión también cuenta: pagado + en revisión > nuevo total → monto menor", async () => {
    const { pedir } = await preparar({ montoPagado: 40, montoEnRevision: 40 });
    expect(await pedir(["10:00"])).toMatchObject({ codigo: "REPROGRAMAR_MONTO_MENOR" }); // S/ 60 < S/ 80 comprometidos
  });

  it("lo pagado no cubre el adelanto del nuevo horario → REPROGRAMAR_ADELANTO_INSUFICIENTE", async () => {
    const { pedir } = await preparar({ montoPagado: 40 });
    const r = await pedir(["20:00", "21:00"]); // S/ 160 → adelanto S/ 80 > S/ 40 pagados
    expect(r).toMatchObject({ error: MENSAJES.adelanto, codigo: "REPROGRAMAR_ADELANTO_INSUFICIENTE" });
    expect(r.error).toBe("El nuevo horario pide un adelanto mayor del que ya pagaste. Elige otro horario o escríbenos para ayudarte.");
  });

  it("solo con un comprobante en revisión que no cubre el adelanto también se rechaza", async () => {
    const { pedir } = await preparar({ montoEnRevision: 40 });
    expect(await pedir(["20:00", "21:00"])).toMatchObject({ codigo: "REPROGRAMAR_ADELANTO_INSUFICIENTE" });
  });

  it("el adelanto se redondea como el back (HALF_UP al céntimo): 33.35 × 30 % = 10.01", async () => {
    const { api, turno, pedir } = await preparar({ montoPagado: 10 });
    api.datosComplejo = { ...COMPLEJO, reglas: { ...COMPLEJO.reglas, adelantoPct: 30 } };
    turno("20:00").precio = 33.35;
    expect(await pedir(["20:00"])).toMatchObject({ codigo: "REPROGRAMAR_ADELANTO_INSUFICIENTE" });
    api.reservas.forEach((r) => (r.montoPagado = 10.01));
    expect(await pedir(["20:00"])).toMatchObject({ vistaPrevia: true, despues: { total: "S/ 33.35" }, saldo: "S/ 23.34" });
  });

  it("elegir los mismos turnos que ya tiene → REPROGRAMAR_MISMO_HORARIO", async () => {
    const { pedir } = await preparar({ montoPagado: 40 });
    expect(await pedir(["19:00"])).toMatchObject({ error: MENSAJES.mismoHorario, codigo: "REPROGRAMAR_MISMO_HORARIO" });
  });

  it("si reservas no envía la bandera (versión anterior) no se bloquea: decide el back al confirmar", async () => {
    const { reserva, pedir } = await preparar({ montoPagado: 40 });
    delete (reserva as { puedeReprogramar?: boolean }).puedeReprogramar;
    expect(await pedir(["20:00"])).toMatchObject({ vistaPrevia: true });
  });

  it("al confirmar, un 422 del back llega con su mensaje tal cual", async () => {
    const { api, pedir } = await preparar({ montoPagado: 40 });
    api.errorReprogramar = new ErrorApi(422, "REPROGRAMACION_NO_PERMITIDA", MENSAJES.plazo);
    expect(await pedir(["20:00"], true)).toEqual({
      error: MENSAJES.plazo, codigo: "REPROGRAMACION_NO_PERMITIDA", siguientePaso: expect.any(String),
    });
  });

  describe("si reservas guardó el cambio pero la respuesta no llegó (503)", () => {
    afterEach(() => vi.restoreAllMocks());

    it("lo revisa y dice que quedó hecho; un reintento dice «ya estaba hecho», no «ya cambiaste una vez»", async () => {
      const { api, reserva, turno, pedir } = await preparar({ montoPagado: 40 });
      api.perderRespuestaReprogramar = true;
      const r1 = await pedir(["20:00"], true);
      expect(r1).toMatchObject({ reprogramada: true, codigoReserva: reserva.codigoReserva, horario: "8:00–9:00 p. m." });
      expect(r1).not.toHaveProperty("error");
      expect(r1).not.toHaveProperty("yaEstabaAplicado");
      // El modelo reintenta igual (o vuelve a pedir la vista previa) con los mismos turnos.
      for (const confirmo of [true, false]) {
        const r = await pedir(["20:00"], confirmo);
        expect(r).toMatchObject({ reprogramada: true, yaEstabaAplicado: true, codigoReserva: reserva.codigoReserva });
        expect(r).not.toHaveProperty("error");
        expect(r).not.toHaveProperty("vistaPrevia");
        expect(String(r.siguientePaso)).toContain("ya estaba hecho");
      }
      expect(api.reprogramaciones).toHaveLength(1);
      expect(reserva.turnos.map((t) => t.idTurno)).toEqual([turno("20:00").idTurno]);
      // Pedir OTRO horario después del cambio sí es «ya cambiaste el horario una vez».
      expect(await pedir(["21:00"], true)).toMatchObject({ error: MENSAJES.limite, codigo: "REPROGRAMACION_NO_PERMITIDA" });
    });

    it("si no quedó guardado, dice que sigue con su horario de antes y ofrece reintentar (y el reintento funciona)", async () => {
      const { api, reserva, turno, pedir } = await preparar({ montoPagado: 40 });
      api.errorReprogramar = new ErrorApi(503, "SERVICIO_NO_DISPONIBLE", "No se pudo conectar con el sistema de reservas.");
      const r = await pedir(["20:00"], true);
      expect(r).toEqual({
        error: expect.stringContaining("sigue con su horario de antes"), codigo: "SERVICIO_NO_DISPONIBLE",
        siguientePaso: expect.stringContaining("reintentar"),
      });
      expect(reserva.turnos.map((t) => t.idTurno)).toEqual([turno("19:00").idTurno]);
      api.errorReprogramar = null;
      expect(await pedir(["20:00"], true)).toMatchObject({ reprogramada: true });
      expect(api.reprogramaciones).toHaveLength(1);
    });

    it("si tampoco responde la consulta, no afirma nada; al volver, el reintento dice que ya estaba hecho", async () => {
      const { api, pedir } = await preparar({ montoPagado: 40 });
      api.perderRespuestaReprogramar = true;
      let caido = false;
      const consultar = api.consultarReserva.bind(api);
      vi.spyOn(api, "consultarReserva").mockImplementation(async (c, d) => {
        if (caido) throw new ErrorApi(503, "SERVICIO_NO_DISPONIBLE", "No se pudo conectar con el sistema de reservas.");
        return consultar(c, d);
      });
      const reprogramar = api.reprogramarReserva.bind(api);
      vi.spyOn(api, "reprogramarReserva").mockImplementation(async (c, d, ids) => {
        try {
          return await reprogramar(c, d, ids);
        } finally {
          caido = true; // se cae justo después del cambio
        }
      });
      const r1 = await pedir(["20:00"], true);
      expect(r1).toMatchObject({ error: expect.stringContaining("No pude confirmar si el cambio"), codigo: "SERVICIO_NO_DISPONIBLE" });
      expect(r1).not.toHaveProperty("reprogramada");
      expect(String(r1.siguientePaso)).toContain("No le digas que su horario cambió");
      caido = false;
      expect(await pedir(["20:00"], true)).toMatchObject({ reprogramada: true, yaEstabaAplicado: true });
      expect(api.reprogramaciones).toHaveLength(1);
    });

    it("si el equipo la movió desde el panel (aún puede cambiar) o está cancelada, no se dice «ya estaba hecho»", async () => {
      const { reserva, pedir } = await preparar({ montoPagado: 40 });
      Object.assign(reserva, { reprogramaciones: 1, puedeReprogramar: true });
      expect(await pedir(["19:00"])).toMatchObject({ error: MENSAJES.mismoHorario, codigo: "REPROGRAMAR_MISMO_HORARIO" });
      Object.assign(reserva, { puedeReprogramar: false, estado: "CANCELADA" });
      expect(await pedir(["19:00"], true)).toMatchObject({ error: MENSAJES.cancelada, codigo: "RESERVA_NO_REPROGRAMABLE" });
    });
  });
});

describe("reglas puras del cambio de horario", () => {
  it("céntimos y adelanto sin errores de coma flotante", () => {
    expect(centimos(33.35)).toBe(3335);
    expect(centimos("80.00")).toBe(8000);
    expect(centimos(undefined)).toBe(0);
    expect(adelantoEnCentimos(3335, 30)).toBe(1001); // 10.005 → 10.01 (33.35 * 0.3 en JS da 10.004999…)
    expect(adelantoEnCentimos(8000, 50)).toBe(4000);
    expect(adelantoEnCentimos(3333, 50)).toBe(1667); // 16.665 → 16.67
  });

  it("los límites exactos pasan: total = comprometido y comprometido = adelanto", () => {
    expect(revisarMontos({ total: 8000, pagado: 8000, enRevision: 0, adelantoPct: 50 })).toBeNull();
    expect(revisarMontos({ total: 16000, pagado: 4000, enRevision: 4000, adelantoPct: 50 })).toBeNull();
    expect(revisarMontos({ total: 7999, pagado: 8000, enRevision: 0, adelantoPct: 50 })?.codigo).toBe("REPROGRAMAR_MONTO_MENOR");
    expect(revisarMontos({ total: 16000, pagado: 3999, enRevision: 4000, adelantoPct: 50 })?.codigo).toBe("REPROGRAMAR_ADELANTO_INSUFICIENTE");
    expect(revisarMontos({ total: 16000, pagado: 0, enRevision: 0, adelantoPct: 50 })).toBeNull();
  });

  it("puedeReprogramar = true no se discute y el plazo es exactamente 2 h antes del inicio", () => {
    const base = {
      codigoReserva: CODIGO, estado: "CONFIRMADA", estadoPago: "ADELANTO_PAGADO", venceEn: null,
      cancha: { id: 1, nombre: "Cancha 1", deporte: "FUTBOL_5" }, fecha: "2026-11-20", horaInicio: "19:00:00", horaFin: "20:00:00",
      montoTotal: 80, montoAdelanto: 40, horarioTexto: "7:00–8:00 p. m.", cantidadTurnos: 1, turnos: [], montoPagado: 40,
      saldo: 40, asistencia: "PENDIENTE", metodoPagoPrevisto: null, puedeCancelar: true, puedeReenviarComprobante: false,
      cancelacion: null, reprogramaciones: 0,
    };
    const lima = (hhmm: string) => new Date(`2026-11-20T${hhmm}:00-05:00`);
    expect(motivoSinCambio({ ...base, puedeReprogramar: true }, lima("18:30"))).toBeNull();
    expect(motivoSinCambio({ ...base, puedeReprogramar: false }, lima("17:00"))?.mensaje).toBe(MENSAJES.plazo);
    expect(motivoSinCambio({ ...base, puedeReprogramar: false }, lima("16:59"))?.mensaje).toBe(MENSAJES.otro);
    expect(motivoSinCambio({ ...base, puedeReprogramar: false }, lima("19:00"))?.mensaje).toBe(MENSAJES.empezo);
  });

  it("cambioYaAplicado: vigente, con justo esos turnos (en cualquier orden) y su cambio ya usado", () => {
    const base = {
      codigoReserva: CODIGO, estado: "CONFIRMADA", estadoPago: "ADELANTO_PAGADO", venceEn: null,
      cancha: { id: 1, nombre: "Cancha 1", deporte: "FUTBOL_5" }, fecha: "2026-11-20", horaInicio: "20:00:00", horaFin: "22:00:00",
      montoTotal: 160, montoAdelanto: 80, horarioTexto: "8:00–10:00 p. m.", cantidadTurnos: 2, montoPagado: 80, saldo: 80,
      turnos: [{ idTurno: 112, horaInicio: "20:00", horaFin: "21:00", precio: 80 }, { idTurno: 113, horaInicio: "21:00", horaFin: "22:00", precio: 80 }],
      asistencia: "PENDIENTE", metodoPagoPrevisto: null, puedeCancelar: true, puedeReenviarComprobante: false, cancelacion: null,
      reprogramaciones: 1, puedeReprogramar: false,
    };
    expect(cambioYaAplicado(base, [113, 112])).toBe(true);
    expect(cambioYaAplicado({ ...base, estado: "PENDIENTE" }, [112, 113])).toBe(true);
    expect(cambioYaAplicado(base, [112])).toBe(false); // otros turnos: es «ya cambiaste una vez»
    expect(cambioYaAplicado({ ...base, reprogramaciones: 0 }, [112, 113])).toBe(false); // nunca cambió: mismo horario
    expect(cambioYaAplicado({ ...base, puedeReprogramar: true }, [112, 113])).toBe(false); // el panel no gasta su cambio
    expect(cambioYaAplicado({ ...base, estado: "CANCELADA" }, [112, 113])).toBe(false);
  });

  it("plazoTrasCambio: el menor entre venceEn y el nuevo inicio; sin venceEn no hay plazo", () => {
    const lima = (fechaHora: string) => new Date(`${fechaHora}:00-05:00`);
    const inicio = lima("2026-11-20T10:00");
    expect(plazoTrasCambio("2026-11-20T21:00:00", inicio)).toEqual(inicio);
    expect(plazoTrasCambio("2026-11-20T09:30:00", inicio)).toEqual(lima("2026-11-20T09:30"));
    expect(plazoTrasCambio("2026-11-20T10:00:00", inicio)).toEqual(inicio);
    expect(plazoTrasCambio("2026-11-20T21:00", null)).toEqual(lima("2026-11-20T21:00"));
    expect(plazoTrasCambio(null, inicio)).toBeNull();
  });
});

describe("prompt: Canchita explica las reglas del cambio de horario", () => {
  it("una sola vez, hasta 2 h antes, lo pagado se conserva y nunca promete devolver diferencias", () => {
    for (const canal of ["WEB", "WHATSAPP"] as const) {
      const sistema = instruccionesSistema(COMPLEJO, canal, "http://localhost:5186", canal === "WHATSAPP" ? "987650001" : null);
      expect(sistema).toContain("Por su cuenta (aquí o en la web) el cliente cambia UNA sola vez por reserva y hasta 2 horas antes del partido");
      expect(sistema).toContain("el comprobante que se está revisando");
      expect(sistema).toContain("cubrir el adelanto (50 %) del nuevo horario");
      expect(sistema).toContain("Nunca prometas devolver ni\n    revisar diferencias");
      expect(sistema).toContain("NO ofrezcas confirmar");
      expect(sistema).toContain("recuérdale que es su único cambio");
      expect(sistema).not.toContain("el equipo revisa la diferencia");
    }
  });
});
