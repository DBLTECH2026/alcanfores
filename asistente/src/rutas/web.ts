// Chat del portal (botón flotante): POST /api/asistente/web/mensaje (SSE o JSON) y GET /api/asistente/web/historial.
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { Ajustes } from "../ajustes.js";
import type { Canchita, ImagenEntrante } from "../canchita.js";
import { leerImagenBase64 } from "../imagenes.js";
import type { Repositorio } from "../modelo.js";
import type { MensajeSalida } from "../respuesta.js";
import { isoLima, responderError, type ErrorCampo } from "./comun.js";

const PATRON_SESION = /^[A-Za-z0-9_-]{16,64}$/;
const MAX_TEXTO = 1000;

interface CuerpoMensaje {
  sesion?: unknown;
  texto?: unknown;
  imagen?: unknown;
}

export function rutasWeb(app: FastifyInstance, d: { repo: Repositorio; ajustes: Ajustes; canchita: Canchita }): void {
  app.get("/api/asistente/web/estado", async () => {
    const i = await d.ajustes.interruptores();
    return { activa: i.web, nombre: "Canchita", avatar: "/canchita/canchita-avatar.webp" };
  });

  app.post("/api/asistente/web/mensaje", async (req: FastifyRequest<{ Body: CuerpoMensaje }>, rep: FastifyReply) => {
    const cuerpo = (req.body ?? {}) as CuerpoMensaje;
    const errores: ErrorCampo[] = [];
    const sesion = typeof cuerpo.sesion === "string" ? cuerpo.sesion.trim() : "";
    if (!PATRON_SESION.test(sesion)) errores.push({ campo: "sesion", mensaje: "La sesión del chat no es válida." });
    const texto = typeof cuerpo.texto === "string" ? cuerpo.texto.trim() : "";
    if (cuerpo.texto !== undefined && cuerpo.texto !== null && typeof cuerpo.texto !== "string") {
      errores.push({ campo: "texto", mensaje: "Escribe tu mensaje." });
    }
    if (texto.length > MAX_TEXTO) errores.push({ campo: "texto", mensaje: `Escribe hasta ${MAX_TEXTO} caracteres.` });
    let imagen: ImagenEntrante | undefined;
    if (cuerpo.imagen !== undefined && cuerpo.imagen !== null && cuerpo.imagen !== "") {
      const leida = typeof cuerpo.imagen === "string" ? leerImagenBase64(cuerpo.imagen) : { error: "Sube una imagen JPG o PNG de hasta 5 MB." };
      if ("error" in leida) errores.push({ campo: "imagen", mensaje: leida.error });
      else imagen = leida;
    }
    if (!texto && !imagen && !errores.some((e) => e.campo === "imagen")) {
      errores.push({ campo: "texto", mensaje: "Escribe tu mensaje o sube la foto de tu comprobante." });
    }
    if (errores.length) return responderError(req, rep, 400, "VALIDACION", "Revisa los datos del mensaje.", errores);

    if (!(await d.ajustes.interruptores()).web) {
      return responderError(req, rep, 503, "CANCHITA_APAGADA", "Canchita no está disponible en la web por ahora.");
    }

    const conversacion = await d.repo.obtenerOCrearConversacion("WEB", sesion);
    await d.canchita.guardarEntrada(conversacion, texto, imagen);
    const quiereSse = String(req.headers.accept ?? "").includes("text/event-stream");

    if (!quiereSse) {
      const r = await d.canchita.responder(conversacion.id);
      const mensajes: MensajeSalida[] = r.omitido === "PAUSADA" ? [{ texto: d.canchita.textoPausada() }] : r.mensajes;
      return { mensajes, latenciaMs: r.latenciaMs };
    }

    rep.hijack();
    const raw = rep.raw;
    raw.writeHead(200, {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    });
    const enviar = (evento: string, datos: unknown) => {
      if (!raw.writableEnded) raw.write(`event: ${evento}\ndata: ${JSON.stringify(datos)}\n\n`);
    };
    const latido = setInterval(() => !raw.writableEnded && raw.write(": sigo aquí\n\n"), 10_000);
    try {
      const r = await d.canchita.responder(conversacion.id, {
        escribiendo: () => enviar("escribiendo", {}),
        herramienta: (nombre, textoHerramienta) => enviar("herramienta", { nombre, texto: textoHerramienta }),
      });
      const mensajes: MensajeSalida[] = r.omitido === "PAUSADA" ? [{ texto: d.canchita.textoPausada() }] : r.mensajes;
      for (const m of mensajes) enviar("mensaje", m);
      enviar("fin", { latenciaMs: r.latenciaMs });
    } catch {
      enviar("error", { mensaje: "No pude responder ahora. Intenta en unos segundos." });
    } finally {
      clearInterval(latido);
      raw.end();
    }
    return rep;
  });

  app.get("/api/asistente/web/historial", async (req: FastifyRequest<{ Querystring: { sesion?: string } }>, rep) => {
    const sesion = String(req.query.sesion ?? "").trim();
    if (!PATRON_SESION.test(sesion)) {
      return responderError(req, rep, 400, "VALIDACION", "Revisa los datos.", [{ campo: "sesion", mensaje: "La sesión del chat no es válida." }]);
    }
    const c = await d.repo.buscarConversacion("WEB", sesion);
    if (!c) return { sesion, mensajes: [] };
    const mensajes = await d.repo.historial(c.id, 50);
    return {
      sesion,
      codigoReserva: c.codigoReserva,
      mensajes: mensajes.map((m) => ({
        id: m.id, rol: m.rol, texto: m.texto, imagen: Boolean(m.adjunto?.startsWith("imagen:")),
        ...(m.extras?.opciones ? { opciones: m.extras.opciones } : {}),
        ...(m.extras?.enlace ? { enlace: m.extras.enlace } : {}),
        creadoEn: isoLima(m.creadoEn),
      })),
    };
  });
}
