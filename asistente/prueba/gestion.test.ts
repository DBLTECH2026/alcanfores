// Gestión de reservas: mis_reservas (solo WhatsApp, celular de la conversación), reprogramar con vista previa y
// cancelar con confirmación explícita.
import { afterEach, describe, expect, it } from "vitest";
import { fechaLima, sumarDias } from "../src/tiempo.js";
import { crearEntorno, type Guion } from "./apoyo.js";

const CLIENTE = "51987650001";
const manana = sumarDias(fechaLima(), 1);

function eventoWhatsapp(id: string, texto: string, numero = CLIENTE) {
  return {
    event: "messages.upsert",
    data: { key: { remoteJid: `${numero}@s.whatsapp.net`, fromMe: false, id }, pushName: "Cliente Ficticio", message: { conversation: texto } },
  };
}

describe("mis_reservas", () => {
  let entorno: ReturnType<typeof crearEntorno>;
  afterEach(async () => entorno?.app.close());

  async function porWhatsapp(guion: Guion, numero = CLIENTE) {
    entorno.cerebro.guion = guion;
    await entorno.app.inject({ method: "POST", url: "/webhook/evolution/token-prueba", payload: eventoWhatsapp(`M${Date.now()}`, "quiero cancelar mi reserva, mi DNI es 45678912", numero) });
    await Promise.all([...entorno.webhooksEnCurso]);
    return entorno.wa.enviados.at(-1)?.texto ?? "";
  }

  async function reservaDe(telefono: string) {
    const turno = entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "19:00" && t.idCancha === 1)!;
    return entorno.api.crearReserva([turno.idTurno], { dni: "45678912", nombres: "Luis", apellidos: "Ficticio", telefono, correo: "l@example.com" }, "YAPE");
  }

  it("encuentra las reservas del DNI hechas con el mismo celular, sin pedir el código", async () => {
    entorno = crearEntorno();
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    const r = await reservaDe("987650001");
    const texto = await porWhatsapp(async ({ llamar }) => JSON.stringify(await llamar("mis_reservas", { dni: "45678912" })));
    const respuesta = JSON.parse(texto);
    expect(respuesta.reservas).toHaveLength(1);
    expect(respuesta.reservas[0]).toMatchObject({ codigo: r.codigoReserva, total: "S/ 80.00", horario: "7:00–8:00 p. m." });
    // Con una sola, queda como la reserva de la conversación.
    const conv = await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE);
    expect(conv?.codigoReserva).toBe(r.codigoReserva);
  });

  it("desde otro número no devuelve nada (el celular no lo da el modelo)", async () => {
    entorno = crearEntorno();
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await reservaDe("987650001");
    const texto = await porWhatsapp(async ({ llamar }) => JSON.stringify(await llamar("mis_reservas", { dni: "45678912", telefono: "987650001" })), "51999888777");
    const respuesta = JSON.parse(texto);
    expect(respuesta.reservas).toEqual([]);
    expect(respuesta.nota).toContain("pide el código");
  });

  it("no existe en el chat web, donde se sigue pidiendo código y DNI", async () => {
    entorno = crearEntorno(async ({ entrada }) => entrada.herramientas.map((h) => h.nombre).join(","));
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: "sesion-web-gestion-01", texto: "mis reservas" } });
    expect(r.json().mensajes[0].texto).not.toContain("mis_reservas");
    expect(entorno.cerebro.turnos[0].sistema).toContain("Pide el código (ALC-XXXXXX) y el DNI");
  });

  it("si reservas aún no tiene el endpoint (404) avisa que la función no está disponible", async () => {
    entorno = crearEntorno();
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    entorno.api.sinEndpointsNuevos = true;
    const texto = await porWhatsapp(async ({ llamar }) => String((await llamar("mis_reservas", { dni: "45678912" })).error));
    expect(texto).toContain("aún no está disponible");
  });

  it("el prompt de WhatsApp explica mis_reservas y la política de devolución", async () => {
    entorno = crearEntorno(async () => "ok");
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await porWhatsapp(async () => "ok");
    const sistema = entorno.cerebro.turnos[0].sistema;
    expect(sistema).toContain("usa mis_reservas");
    expect(sistema).toContain("con 24 h o más de");
    expect(sistema).toContain("«¿Confirmo la cancelación?»");
    expect(sistema).toContain("constancia");
  });
});

describe("reprogramar y cancelar", () => {
  let entorno: ReturnType<typeof crearEntorno>;
  afterEach(async () => entorno?.app.close());

  async function preparar() {
    entorno = crearEntorno();
    const turno = entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "19:00" && t.idCancha === 1)!;
    const r = await entorno.api.crearReserva([turno.idTurno], { dni: "45678912", nombres: "Luis", apellidos: "Ficticio", telefono: "987650001", correo: "l@example.com" }, "YAPE");
    entorno.api.reservas.get(r.codigoReserva)!.montoPagado = 40;
    return { codigo: r.codigoReserva, nuevo: entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "20:00" && t.idCancha === 1)! };
  }

  async function turno(guion: Guion) {
    entorno.cerebro.guion = guion;
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: "sesion-web-gestion-02", texto: "cambio" } });
    return r.json().mensajes[0].texto as string;
  }

  it("primero calcula el cambio sin tocar nada y solo reprograma con la confirmación", async () => {
    const { codigo, nuevo } = await preparar();
    // Pagó el total (S/ 80): cubre el adelanto del nuevo horario de S/ 160 (S/ 80). Con S/ 40 el back lo rechaza.
    entorno.api.reservas.get(codigo)!.montoPagado = 80;
    const previa = JSON.parse(await turno(async ({ llamar }) => JSON.stringify(await llamar("reprogramar_reserva", {
      codigo, dni: "45678912", fecha: manana, idsTurno: [nuevo.idTurno, nuevo.idTurno + 1], clienteConfirmo: false,
    }))));
    expect(previa).toMatchObject({
      vistaPrevia: true, antes: { total: "S/ 80.00" }, despues: { total: "S/ 160.00", horario: "8:00–9:00 p. m. y 9:00–10:00 p. m." },
      yaPagado: "S/ 80.00", saldo: "S/ 80.00",
    });
    expect(entorno.api.reprogramaciones).toHaveLength(0);

    const hecha = JSON.parse(await turno(async ({ llamar }) => JSON.stringify(await llamar("reprogramar_reserva", {
      codigo, dni: "45678912", fecha: manana, idsTurno: [nuevo.idTurno], clienteConfirmo: true,
    }))));
    expect(hecha.reprogramada).toBe(true);
    expect(entorno.api.reprogramaciones).toEqual([{ codigo, dni: "45678912", idsTurno: [nuevo.idTurno] }]);
  });

  it("rechaza turnos ocupados, de otra cancha o una reserva que no es de ese DNI", async () => {
    const { codigo, nuevo } = await preparar();
    nuevo.libre = false;
    const otraCancha = entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "21:00" && t.idCancha === 2)!;
    const libre = entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "21:00" && t.idCancha === 1)!;
    const texto = await turno(async ({ llamar }) => [
      (await llamar("reprogramar_reserva", { codigo, dni: "45678912", fecha: manana, idsTurno: [nuevo.idTurno], clienteConfirmo: true })).error,
      (await llamar("reprogramar_reserva", { codigo, dni: "45678912", fecha: manana, idsTurno: [libre.idTurno, otraCancha.idTurno], clienteConfirmo: true })).error,
      (await llamar("reprogramar_reserva", { codigo, dni: "11111111", fecha: manana, idsTurno: [libre.idTurno], clienteConfirmo: true })).error,
    ].join(" | "));
    expect(texto).toContain("ya no está libre");
    expect(texto).toContain("misma cancha");
    expect(texto).toContain("No hay una reserva con ese código y ese DNI.");
    expect(entorno.api.reprogramaciones).toHaveLength(0);
  });

  it("si reservas aún no tiene /reprogramar (404) avisa que la función no está disponible", async () => {
    const { codigo, nuevo } = await preparar();
    entorno.api.sinEndpointsNuevos = true;
    const texto = await turno(async ({ llamar }) => String((await llamar("reprogramar_reserva", {
      codigo, dni: "45678912", fecha: manana, idsTurno: [nuevo.idTurno], clienteConfirmo: true,
    })).error));
    expect(texto).toContain("aún no está disponible");
  });

  it("cancelar exige la confirmación explícita del cliente", async () => {
    const { codigo } = await preparar();
    const texto = await turno(async ({ llamar }) => {
      const sin = await llamar("cancelar_reserva", { codigo, dni: "45678912", motivo: "No podremos ir ese día", clienteConfirmo: false });
      const con = await llamar("cancelar_reserva", { codigo, dni: "45678912", motivo: "No podremos ir ese día", clienteConfirmo: true });
      return `${sin.error} | ${con.estado}`;
    });
    expect(texto).toBe("Primero explica la política de devolución y pide que confirme la cancelación. | CANCELADA");
  });
});
