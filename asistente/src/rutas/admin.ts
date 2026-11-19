// Panel: estado de Canchita y conversaciones (ADMIN o RECEPCION con el JWT de auth-service).
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import type { Ajustes } from "../ajustes.js";
import { tipoCredencial, type Configuracion } from "../config.js";
import type { Conversacion, Repositorio, ResumenConversacion } from "../modelo.js";
import { ahora } from "../tiempo.js";
import type { SincronizadorWebhook } from "../whatsapp/sincronizador.js";
import { isoLima, responderError, verificarJwt } from "./comun.js";

const ROLES_PANEL = new Set(["ADMIN", "RECEPCION"]);

function clienteDe(c: Conversacion): string {
  if (c.nombre) return c.nombre;
  if (c.canal === "WHATSAPP") {
    const d = c.clave.replace(/\D/g, "");
    return d.length === 11 && d.startsWith("51") ? `+51 ${d.slice(2, 5)} ${d.slice(5, 8)} ${d.slice(8)}` : "Cliente de WhatsApp";
  }
  return "Visitante de la web";
}

function item(r: ResumenConversacion) {
  const c = r.conversacion;
  return {
    id: c.id, canal: c.canal, cliente: clienteDe(c), clave: c.clave, estado: c.estado, pausadaHasta: isoLima(c.pausadaHasta),
    codigoReserva: c.codigoReserva, ultimaActividad: isoLima(c.ultimaActividad),
    ultimoMensaje: r.ultimoMensaje
      ? { rol: r.ultimoMensaje.rol, texto: r.ultimoMensaje.texto.slice(0, 160), creadoEn: isoLima(r.ultimoMensaje.creadoEn) }
      : null,
  };
}

export function rutasAdmin(app: FastifyInstance, d: {
  cfg: Configuracion; repo: Repositorio; ajustes: Ajustes; sincronizador: SincronizadorWebhook;
}): void {
  const exigirPanel = async (req: FastifyRequest, rep: FastifyReply) => {
    const cabecera = String(req.headers.authorization ?? "");
    const token = cabecera.startsWith("Bearer ") ? cabecera.slice(7).trim() : "";
    const usuario = token ? verificarJwt(token, d.cfg.jwtSecreto) : null;
    if (!usuario) return responderError(req, rep, 401, "NO_AUTENTICADO", "Inicia sesión para continuar.");
    if (!ROLES_PANEL.has(usuario.rol)) return responderError(req, rep, 403, "SIN_PERMISO", "No tienes permiso para esta acción.");
  };

  app.register(async (panel) => {
    panel.addHook("preHandler", exigirPanel);

    panel.get("/api/asistente/admin/estado", async () => {
      d.ajustes.olvidar();
      const [interruptores, whatsapp, conversaciones, latencia] = await Promise.all([
        d.ajustes.interruptores(true), d.sincronizador.sincronizar(), d.repo.contarConversaciones(), d.repo.latenciaPromedioMs(50),
      ]);
      const tipo = tipoCredencial(d.cfg);
      return {
        credencial: { estado: tipo === "FALTA" ? "FALTA" : "CONECTADA", tipo },
        modelo: d.cfg.ia.modelo,
        esfuerzo: d.cfg.ia.esfuerzo,
        interruptores,
        whatsapp,
        conversaciones,
        latenciaPromedioMs: latencia,
      };
    });

    panel.get("/api/asistente/admin/conversaciones", async (req: FastifyRequest<{ Querystring: { limite?: string } }>) => {
      const limite = Math.min(Math.max(Number(req.query.limite) || 20, 1), 100);
      const lista = await d.repo.ultimasConversaciones(limite);
      return { contenido: lista.map(item) };
    });

    const cambiarEstado = (pausar: boolean) =>
      async (req: FastifyRequest<{ Params: { id: string }; Body: { horas?: unknown } | undefined }>, rep: FastifyReply) => {
        const id = Number(req.params.id);
        const c = Number.isInteger(id) && id > 0 ? await d.repo.conversacion(id) : null;
        if (!c) return responderError(req, rep, 404, "NO_ENCONTRADO", "No encontramos esa conversación.");
        const horas = Number((req.body as { horas?: unknown } | undefined)?.horas);
        if (pausar && req.body && (req.body as { horas?: unknown }).horas !== undefined && !(horas >= 1 && horas <= 72)) {
          return responderError(req, rep, 400, "VALIDACION", "Revisa los datos.", [{ campo: "horas", mensaje: "Elige entre 1 y 72 horas." }]);
        }
        const actualizada = await d.repo.actualizarConversacion(id, pausar
          ? { estado: "PAUSADA", pausadaHasta: horas >= 1 ? new Date(ahora().getTime() + horas * 3_600_000) : null }
          : { estado: "ACTIVA", pausadaHasta: null });
        const [ultimo] = await d.repo.historial(id, 1);
        return item({ conversacion: actualizada, ultimoMensaje: ultimo ?? null });
      };

    panel.post("/api/asistente/admin/conversaciones/:id/pausar", cambiarEstado(true));
    panel.post("/api/asistente/admin/conversaciones/:id/reanudar", cambiarEstado(false));
  });
}
