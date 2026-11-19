// Flujo de reserva completo por el chat web, con el cerebro simulado decidiendo las herramientas.
import { afterEach, describe, expect, it } from "vitest";
import { fechaLima, sumarDias } from "../src/tiempo.js";
import { crearEntorno, type Guion } from "./apoyo.js";

const SESION = "sesion-prueba-0001";
const manana = sumarDias(fechaLima(), 1);

/** Guion de 3 pasos: disponibilidad → DNI y datos → confirmación y reserva. */
const guionReserva: Guion = async ({ entrada, llamar }) => {
  const nuevo = entrada.prompt.split("<<<")[1] ?? "";
  if (/reservar/i.test(nuevo)) {
    const d = await llamar("ver_disponibilidad", { fecha: manana, idCancha: 1 });
    const libres = (d.canchas as { libres: { idTurno: number; horario: string; precio: string }[] }[])[0].libres;
    const noche = libres.filter((t) => t.horario.includes("p. m.")).slice(-3);
    return `Para mañana en la Cancha 1 tengo libre:\n${noche.map((t) => `- ${t.horario} (${t.precio})`).join("\n")}\n`
      + `¿Cuál te separo?\nOPCIONES: ${noche.map((t) => t.horario).join(" | ")}`;
  }
  if (/DNI/i.test(nuevo)) {
    const p = await llamar("consultar_dni", { dni: "45678912" });
    return `Gracias, *${p.nombres} ${p.apellidos}*. ¿Boleta o factura?\nOPCIONES: Boleta | Factura`;
  }
  if (/confirmo/i.test(nuevo)) {
    const r = await llamar("crear_reserva", {
      idsTurno: [111, 112],
      cliente: { dni: "45678912", nombres: "Juan Carlos", apellidos: "Prueba Ficticio", telefono: "+51 987 654 321", correo: "juan.prueba@example.com" },
      metodoPago: "YAPE", comprobante: { tipo: "BOLETA" }, clienteConfirmo: true,
    });
    return `Listo, tu código es *${r.codigoReserva}*. Yapea *${r.montoAdelanto}* y mándame la foto.\n---\nTu reserva: ${r.enlaceMiReserva}`;
  }
  return "¿En qué te ayudo?";
};

describe("flujo de reserva por la web", () => {
  let entorno: ReturnType<typeof crearEntorno>;
  afterEach(async () => entorno?.app.close());

  it("reserva dos turnos en tres pasos y guarda el código en la conversación", async () => {
    entorno = crearEntorno(guionReserva);
    const enviar = (texto: string) => entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto } });

    const r1 = await enviar("Hola, quiero reservar la cancha 1 mañana en la noche");
    expect(r1.statusCode).toBe(200);
    const m1 = r1.json().mensajes;
    expect(m1[0].texto).toContain("Cancha 1");
    expect(m1[0].opciones).toHaveLength(3);

    const r2 = await enviar("Las de 7 y 8 pm. Mi DNI es 45678912");
    expect(r2.json().mensajes[0].texto).toContain("Juan Carlos Prueba Ficticio");

    const r3 = await enviar("Boleta, correo juan.prueba@example.com. Sí, confirmo");
    const m3 = r3.json().mensajes;
    expect(m3).toHaveLength(2);
    expect(m3[0].texto).toContain("ALC-");
    expect(m3[1].enlace).toEqual({ url: expect.stringContaining("/mi-reserva?codigo=ALC-"), texto: "Ver mi reserva" });

    const [reserva] = [...entorno.api.reservas.values()];
    expect(reserva.cantidadTurnos).toBe(2);
    expect(reserva.montoTotal).toBe(160);
    expect(reserva.montoAdelanto).toBe(80);
    const conv = await entorno.repo.buscarConversacion("WEB", SESION);
    expect(conv?.codigoReserva).toBe(reserva.codigoReserva);
    expect(conv?.dni).toBe("45678912");
    expect(conv?.contexto.telefono).toBe("987654321");
    expect(conv?.contexto.comprobante).toEqual({ tipo: "BOLETA" });

    // El historial devuelve la conversación con botones y enlaces.
    const h = await entorno.app.inject({ method: "GET", url: `/api/asistente/web/historial?sesion=${SESION}` });
    const mensajes = h.json().mensajes;
    expect(mensajes.map((m: { rol: string }) => m.rol)).toEqual(["CLIENTE", "CANCHITA", "CLIENTE", "CANCHITA", "CLIENTE", "CANCHITA", "CANCHITA"]);
    expect(mensajes[1].opciones).toHaveLength(3);
    expect(h.json().codigoReserva).toBe(reserva.codigoReserva);

    // Cada turno recibe el historial reciente y el estado: el tercero ya conoce lo hablado.
    expect(entorno.cerebro.turnos[2].prompt).toContain("Las de 7 y 8 pm");
    expect(entorno.cerebro.turnos[2].prompt).toContain("<<<");
  });

  it("no crea la reserva si el cliente no confirmó", async () => {
    entorno = crearEntorno(async ({ llamar }) => {
      const r = await llamar("crear_reserva", {
        idsTurno: [111], metodoPago: "YAPE", clienteConfirmo: false,
        cliente: { dni: "45678912", nombres: "Juan", apellidos: "Prueba", telefono: "987654321", correo: "j@example.com" },
      });
      return String(r.error);
    });
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "reserva" } });
    expect(r.json().mensajes[0].texto).toContain("confirme");
    expect(entorno.api.reservas.size).toBe(0);
  });

  it("transmite por SSE: escribiendo, herramienta, mensaje y fin", async () => {
    entorno = crearEntorno(guionReserva);
    const r = await entorno.app.inject({
      method: "POST", url: "/api/asistente/web/mensaje", headers: { accept: "text/event-stream" },
      payload: { sesion: SESION, texto: "quiero reservar mañana" },
    });
    expect(r.headers["content-type"]).toContain("text/event-stream");
    const eventos = [...r.body.matchAll(/^event: (\w+)$/gm)].map((m) => m[1]);
    expect(eventos).toEqual(["escribiendo", "herramienta", "mensaje", "fin"]);
    expect(r.body).toContain("Buscando turnos libres");
  });

  it("valida el cuerpo del mensaje con errores por campo", async () => {
    entorno = crearEntorno();
    const r = await entorno.app.inject({
      method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: "corta", imagen: Buffer.from("no es imagen").toString("base64") },
    });
    expect(r.statusCode).toBe(400);
    expect(r.json().codigo).toBe("VALIDACION");
    expect(r.json().errores.map((e: { campo: string }) => e.campo).sort()).toEqual(["imagen", "sesion"]);
  });

  it("sin credencial de IA responde un texto fijo con el portal y el WhatsApp del equipo", async () => {
    entorno = crearEntorno();
    entorno.cerebro.conCredencial = false;
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "hola" } });
    const texto = r.json().mensajes[0].texto;
    expect(texto).toContain("/reservar");
    expect(texto).toContain("914995187");
    expect(entorno.cerebro.turnos).toHaveLength(0);
  });

  it("si el modelo falla, responde una disculpa y no deja el mensaje colgado", async () => {
    entorno = crearEntorno(async () => {
      throw new Error("modelo caído");
    });
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "hola" } });
    expect(r.json().mensajes[0].texto).toContain("se me cruzaron los cables");
    const conv = await entorno.repo.buscarConversacion("WEB", SESION);
    expect(await entorno.repo.pendientes(conv!.id)).toHaveLength(0);
  });
});
