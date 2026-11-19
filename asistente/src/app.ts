// Arma la aplicación HTTP (Fastify) con sus dependencias. Las pruebas la construyen con un cerebro y una API simulados.
import Fastify, { type FastifyInstance } from "fastify";
import { Ajustes } from "./ajustes.js";
import type { ApiAlcanfores } from "./api.js";
import { Canchita } from "./canchita.js";
import type { Cerebro } from "./cerebro/tipos.js";
import type { Configuracion } from "./config.js";
import type { Repositorio } from "./modelo.js";
import { rutasAdmin } from "./rutas/admin.js";
import { responderError } from "./rutas/comun.js";
import { rutasWeb } from "./rutas/web.js";
import type { ClienteWhatsapp } from "./whatsapp/evolution.js";
import { SincronizadorWebhook } from "./whatsapp/sincronizador.js";
import { ProcesadorWhatsapp } from "./whatsapp/webhook.js";

export interface Aplicacion {
  app: FastifyInstance;
  canchita: Canchita;
  procesador: ProcesadorWhatsapp;
  sincronizador: SincronizadorWebhook;
  ajustes: Ajustes;
  /** Promesas de los webhooks en curso (las pruebas las esperan). */
  webhooksEnCurso: Set<Promise<void>>;
}

export function crearAplicacion(d: {
  cfg: Configuracion; repo: Repositorio; api: ApiAlcanfores; cerebro: Cerebro; wa: ClienteWhatsapp; registrarSolicitudes?: boolean;
}): Aplicacion {
  const app = Fastify({
    logger: d.registrarSolicitudes ? { level: "info", redact: ["req.headers.authorization", "req.headers.apikey"] } : false,
    bodyLimit: 8 * 1024 * 1024,
  });
  const registro = (nivel: "info" | "warn" | "error", mensaje: string) => app.log[nivel](mensaje);
  const ajustes = new Ajustes(d.api);
  const canchita = new Canchita({ cfg: d.cfg, repo: d.repo, api: d.api, ajustes, cerebro: d.cerebro, registro });
  const procesador = new ProcesadorWhatsapp(d.cfg, d.repo, ajustes, canchita, d.wa, registro);
  const sincronizador = new SincronizadorWebhook(d.cfg, ajustes, d.wa);
  const webhooksEnCurso = new Set<Promise<void>>();

  app.setErrorHandler((error: { statusCode?: number; message?: string }, req, rep) => {
    if (error.statusCode === 413) return responderError(req, rep, 413, "VALIDACION", "La imagen es muy pesada: sube una de hasta 5 MB.");
    if (error.statusCode && error.statusCode < 500) {
      return responderError(req, rep, error.statusCode, "SOLICITUD_INVALIDA", "La solicitud no es válida.");
    }
    app.log.error(error);
    return responderError(req, rep, 500, "ERROR_INTERNO", "Ocurrió un problema inesperado. Intenta en unos segundos.");
  });
  app.setNotFoundHandler((req, rep) => responderError(req, rep, 404, "NO_ENCONTRADO", "La ruta no existe."));

  app.get("/actuator/health", async () => ({ status: "UP" }));

  // Webhook de Evolution: solo por la red interna de Docker (el gateway no lo enruta) y con el token en la ruta.
  app.post("/webhook/evolution/:token", async (req, rep) => {
    if ((req.params as { token: string }).token !== d.cfg.webhookToken) {
      return responderError(req, rep, 401, "NO_AUTENTICADO", "Token del webhook inválido.");
    }
    const tarea = procesador.procesar(req.body).finally(() => webhooksEnCurso.delete(tarea));
    webhooksEnCurso.add(tarea);
    return { recibido: true };
  });

  rutasWeb(app, { repo: d.repo, ajustes, canchita });
  rutasAdmin(app, { cfg: d.cfg, repo: d.repo, ajustes, sincronizador });

  return { app, canchita, procesador, sincronizador, ajustes, webhooksEnCurso };
}
