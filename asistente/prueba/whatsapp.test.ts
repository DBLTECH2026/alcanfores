// WhatsApp: respuesta por Evolution, pausa cuando escribe el equipo, ecos, interruptor y webhook.
import { afterEach, describe, expect, it } from "vitest";
import { interpretarEvento } from "../src/whatsapp/webhook.js";
import { crearEntorno, lecturaQueCalza, PNG_BASE64 } from "./apoyo.js";

const CLIENTE = "51987650001";

function evento(datos: { id: string; texto?: string; fromMe?: boolean; source?: string; jid?: string; imagen?: boolean }) {
  return {
    event: "messages.upsert",
    instance: "alcanfores",
    data: {
      key: { remoteJid: datos.jid ?? `${CLIENTE}@s.whatsapp.net`, fromMe: datos.fromMe ?? false, id: datos.id },
      pushName: "Cliente Ficticio",
      message: datos.imagen ? { imageMessage: { caption: datos.texto ?? "", mimetype: "image/png" } } : { conversation: datos.texto ?? "" },
      source: datos.source ?? "android",
    },
  };
}

describe("WhatsApp", () => {
  let entorno: ReturnType<typeof crearEntorno>;
  afterEach(async () => entorno?.app.close());

  async function webhook(cuerpo: unknown, token = "token-prueba") {
    const r = await entorno.app.inject({ method: "POST", url: `/webhook/evolution/${token}`, payload: cuerpo as object });
    await Promise.all([...entorno.webhooksEnCurso]);
    return r;
  }

  it("responde al cliente por sendText, marca «escribiendo» y usa su número como celular", async () => {
    entorno = crearEntorno(async () => "Hola, soy *Canchita*. ¿Qué día quieres jugar?\nOPCIONES: Hoy | Mañana");
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    const r = await webhook(evento({ id: "MSG1", texto: "Hola" }));
    expect(r.statusCode).toBe(200);
    expect(entorno.wa.enviados).toHaveLength(1);
    expect(entorno.wa.enviados[0].numero).toBe(CLIENTE);
    expect(entorno.wa.enviados[0].texto).not.toContain("OPCIONES");
    expect(entorno.wa.presencias).toBe(1);
    expect(entorno.cerebro.turnos[0].sistema).toContain("987650001");
    const conv = await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE);
    expect(conv?.nombre).toBe("Cliente Ficticio");
    // Mismo id otra vez (Evolution reintenta): no se responde dos veces.
    await webhook(evento({ id: "MSG1", texto: "Hola" }));
    expect(entorno.wa.enviados).toHaveLength(1);
  });

  it("ignora el eco de lo que envió Canchita y los avisos enviados por la API (SEND_MESSAGE)", async () => {
    entorno = crearEntorno(async () => "Hola");
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await webhook(evento({ id: "MSG1", texto: "Hola" }));
    const idEnviado = entorno.wa.enviados[0].id;
    await webhook(evento({ id: idEnviado, texto: "Hola", fromMe: true, source: "web" }));
    // notificaciones-service manda la constancia por la API: Evolution avisa SEND_MESSAGE con su id.
    await webhook({ event: "send.message", instance: "alcanfores", data: { key: { remoteJid: `${CLIENTE}@s.whatsapp.net`, fromMe: true, id: "3EB0AVISO" } } });
    await webhook(evento({ id: "3EB0AVISO", texto: "Tu reserva fue confirmada", fromMe: true, source: "web" }));
    const conv = await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE);
    expect(conv?.estado).toBe("ACTIVA");
    expect((await entorno.repo.historial(conv!.id, 10)).map((m) => m.rol)).toEqual(["CLIENTE", "CANCHITA"]);
  });

  it("un mensaje del equipo desde WhatsApp Web (source web, id no enviado por la API) también pausa", async () => {
    entorno = crearEntorno(async () => "Hola");
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await webhook(evento({ id: "3EB0EQUIPOWEB", texto: "Hola, soy Marco del complejo", fromMe: true, source: "web" }));
    const conv = await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE);
    expect(conv?.estado).toBe("PAUSADA");
  });

  it("si el equipo escribe desde el celular, la conversación se pausa 2 h y Canchita no responde", async () => {
    entorno = crearEntorno(async () => "Hola");
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await webhook(evento({ id: "EQ1", texto: "Hola, te escribe Rosa del complejo", fromMe: true, source: "android" }));
    const conv = await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE);
    expect(conv?.estado).toBe("PAUSADA");
    const horas = (conv!.pausadaHasta!.getTime() - Date.now()) / 3_600_000;
    expect(horas).toBeGreaterThan(1.9);
    expect(horas).toBeLessThanOrEqual(2);

    await webhook(evento({ id: "MSG2", texto: "Ok, gracias" }));
    expect(entorno.wa.enviados).toHaveLength(0);
    expect(entorno.cerebro.turnos).toHaveLength(0);

    // Al reanudar desde el panel, Canchita vuelve a responder.
    await entorno.repo.actualizarConversacion(conv!.id, { estado: "ACTIVA", pausadaHasta: null });
    await webhook(evento({ id: "MSG3", texto: "¿Hay turnos mañana?" }));
    expect(entorno.wa.enviados).toHaveLength(1);
  });

  it("con el interruptor de WhatsApp apagado no guarda ni responde", async () => {
    entorno = crearEntorno(async () => "Hola");
    await webhook(evento({ id: "MSG1", texto: "Hola" }));
    expect(entorno.wa.enviados).toHaveLength(0);
    expect(await entorno.repo.buscarConversacion("WHATSAPP", CLIENTE)).toBeNull();
  });

  it("descarga la foto del comprobante por Evolution y la pasa a la lectura", async () => {
    entorno = crearEntorno(async ({ entrada }) => (entrada.prompt.includes("idImagen=1") ? "Veo tu comprobante" : "¿?"));
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    entorno.wa.medios.set("FOTO1", { base64: PNG_BASE64, mimetype: "image/png" });
    entorno.cerebro.lecturas.push(lecturaQueCalza(40));
    await webhook(evento({ id: "FOTO1", imagen: true }));
    expect(entorno.wa.enviados[0].texto).toBe("Veo tu comprobante");
  });

  it("usa la foto que ya trae el webhook (base64 de Evolution) sin descargarla", async () => {
    entorno = crearEntorno(async ({ entrada }) => (entrada.prompt.includes("idImagen=1") ? "Veo tu comprobante" : "¿?"));
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    entorno.cerebro.lecturas.push(lecturaQueCalza(40));
    const ev = evento({ id: "FOTO2", imagen: true });
    (ev.data.message as Record<string, unknown>).base64 = PNG_BASE64;
    await webhook(ev);
    expect(entorno.wa.enviados[0].texto).toBe("Veo tu comprobante");
  });

  it("sin base64 descarga con key + message (no solo el id) y sin el base64 dentro del mensaje", () => {
    const ev = evento({ id: "FOTO3", texto: "ya yapeé", imagen: true });
    const [m] = interpretarEvento(ev);
    expect(m).toMatchObject({ tipo: "CLIENTE", imagen: true, texto: "ya yapeé" });
    if (m.tipo !== "CLIENTE") throw new Error("esperaba CLIENTE");
    expect(m.base64).toBeUndefined();
    expect(m.crudo).toEqual({ key: ev.data.key, message: ev.data.message });
  });

  it("un sticker no se ignora: Canchita recibe el aviso para pedir la foto", () => {
    const ev = evento({ id: "STK1" });
    ev.data.message = { stickerMessage: { mimetype: "image/webp" } } as unknown as typeof ev.data.message;
    const [m] = interpretarEvento(ev);
    expect(m).toMatchObject({ tipo: "CLIENTE", imagen: false });
    if (m.tipo !== "CLIENTE") throw new Error("esperaba CLIENTE");
    expect(m.texto).toContain("sticker");
  });

  it("rechaza un token de webhook incorrecto e ignora grupos y estados", async () => {
    entorno = crearEntorno();
    expect((await webhook(evento({ id: "X" }), "otro")).statusCode).toBe(401);
    expect(interpretarEvento(evento({ id: "G", jid: "1203630@g.us" }))[0].tipo).toBe("IGNORAR");
    expect(interpretarEvento(evento({ id: "S", jid: "status@broadcast" }))[0].tipo).toBe("IGNORAR");
    expect(interpretarEvento({ event: "connection.update", data: {} })[0].tipo).toBe("IGNORAR");
  });

  it("el interruptor registra o quita el webhook de Evolution", async () => {
    entorno = crearEntorno();
    entorno.api.configuracion.ia_whatsapp_activa = "true";
    await entorno.sincronizador.sincronizar(true);
    expect(entorno.wa.webhooks.at(-1)).toEqual({ activo: true, url: expect.stringContaining("/webhook/evolution/token-prueba") });
    expect(interpretarEvento({ event: "SEND_MESSAGE", data: { key: { id: "X1" } } })[0]).toEqual({ tipo: "ENVIADO_POR_API", idMensaje: "X1" });
    entorno.api.configuracion.ia_whatsapp_activa = "false";
    await entorno.sincronizador.sincronizar(true);
    expect(entorno.wa.webhooks.at(-1)?.activo).toBe(false);
  });
});
