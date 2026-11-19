// Orquestador de Canchita: guarda lo que llega, respeta la pausa, lee las fotos, corre el turno del cerebro con las
// herramientas y guarda/devuelve las respuestas. Un turno a la vez por conversación (cola por chat).
import type { Ajustes } from "./ajustes.js";
import type { ApiAlcanfores } from "./api.js";
import type { Cerebro } from "./cerebro/tipos.js";
import { ColaPorClave } from "./cola.js";
import type { Configuracion } from "./config.js";
import { crearHerramientas, TEXTO_HERRAMIENTA, type EstadoTurno } from "./herramientas.js";
import type { Conversacion, Imagen, Repositorio } from "./modelo.js";
import { celularDeWhatsapp, instruccionesSistema, promptDelTurno } from "./prompt.js";
import { extrasDe, interpretarRespuesta, type MensajeSalida } from "./respuesta.js";
import { ahora, fechaLima } from "./tiempo.js";

export interface Dependencias {
  cfg: Configuracion;
  repo: Repositorio;
  api: ApiAlcanfores;
  ajustes: Ajustes;
  cerebro: Cerebro;
  registro?: (nivel: "info" | "warn" | "error", mensaje: string) => void;
}

export interface EventosTurno {
  /** Canchita empezó a pensar (para «escribiendo…»). */
  escribiendo?: () => void;
  /** Canchita usa una herramienta. */
  herramienta?: (nombre: string, texto: string) => void;
}

export interface ResultadoTurno {
  mensajes: MensajeSalida[];
  /** Ids de los mensajes guardados (en el mismo orden), para anotar el id de WhatsApp de cada envío. */
  idsGuardados: number[];
  latenciaMs: number;
  /** El turno no corrió: conversación pausada o nada pendiente. */
  omitido?: "PAUSADA" | "SIN_PENDIENTES";
}

export interface ImagenEntrante {
  datos: Buffer;
  mime: string;
}

/** ¿La conversación está pausada ahora? Una pausa vencida la reactiva. */
export async function pausaVigente(repo: Repositorio, c: Conversacion): Promise<{ pausada: boolean; conversacion: Conversacion }> {
  if (c.estado !== "PAUSADA") return { pausada: false, conversacion: c };
  if (c.pausadaHasta && c.pausadaHasta.getTime() <= ahora().getTime()) {
    const reactivada = await repo.actualizarConversacion(c.id, { estado: "ACTIVA", pausadaHasta: null });
    return { pausada: false, conversacion: reactivada };
  }
  return { pausada: true, conversacion: c };
}

export class Canchita {
  readonly cola = new ColaPorClave();

  constructor(private readonly d: Dependencias) {}

  private log(nivel: "info" | "warn" | "error", mensaje: string): void {
    this.d.registro?.(nivel, mensaje);
  }

  /** Texto fijo cuando no hay credencial de IA: enlace al portal y WhatsApp humano. */
  textoSinIa(): string {
    return `Hola, soy Canchita del Complejo Deportivo Los Alcanfores. En este momento no puedo atenderte por aquí, pero puedes `
      + `reservar en línea en ${this.d.cfg.frontUrl}/reservar o escribir a nuestro equipo por WhatsApp al ${this.d.cfg.whatsappHumano}.`;
  }

  textoPausada(): string {
    return `Ahora te atiende una persona de nuestro equipo. Si lo prefieres, escríbenos por WhatsApp al ${this.d.cfg.whatsappHumano}.`;
  }

  /** Guarda un mensaje del cliente (con su foto, si trae) en la conversación. */
  async guardarEntrada(c: Conversacion, texto: string, imagen?: ImagenEntrante, idExterno?: string | null): Promise<void> {
    let adjunto: string | null = null;
    if (imagen) {
      const img = await this.d.repo.guardarImagen(c.id, imagen.mime, imagen.datos);
      adjunto = `imagen:${img.id}`;
    }
    await this.d.repo.agregarMensaje({
      idConversacion: c.id, rol: "CLIENTE", texto: texto || (imagen ? "(envió una foto)" : ""), adjunto, idExterno: idExterno ?? null,
    });
  }

  /** Encola el turno de la conversación (un turno a la vez por chat). */
  responder(idConversacion: number, eventos: EventosTurno = {}): Promise<ResultadoTurno> {
    return this.cola.encolar(`c${idConversacion}`, () => this.turno(idConversacion, eventos));
  }

  private async turno(idConversacion: number, eventos: EventosTurno): Promise<ResultadoTurno> {
    const inicio = Date.now();
    const { repo, cerebro, cfg } = this.d;
    const original = await repo.conversacion(idConversacion);
    if (!original) throw new Error(`No existe la conversación ${idConversacion}`);
    const { pausada, conversacion } = await pausaVigente(repo, original);
    if (pausada) return { mensajes: [], idsGuardados: [], latenciaMs: 0, omitido: "PAUSADA" };
    const pendientes = await repo.pendientes(idConversacion);
    if (pendientes.length === 0) return { mensajes: [], idsGuardados: [], latenciaMs: 0, omitido: "SIN_PENDIENTES" };
    eventos.escribiendo?.();

    let mensajes: MensajeSalida[];
    if (!cerebro.disponible()) {
      mensajes = [{ texto: this.textoSinIa() }];
    } else {
      try {
        mensajes = await this.pensar(conversacion, pendientes.map((m) => m.id), eventos);
      } catch (e) {
        this.log("error", `Turno fallido en la conversación ${idConversacion}: ${e instanceof Error ? e.message : String(e)}`);
        mensajes = [{
          texto: `Uy, se me cruzaron los cables un momento. ¿Me escribes de nuevo? Si es urgente, nuestro equipo te atiende `
            + `por WhatsApp al ${cfg.whatsappHumano}.`,
        }];
      }
    }

    const latenciaMs = Date.now() - inicio;
    const idsGuardados: number[] = [];
    for (const [i, m] of mensajes.entries()) {
      const guardado = await repo.agregarMensaje({
        idConversacion, rol: "CANCHITA", texto: m.texto, extras: extrasDe(m), latenciaMs: i === 0 ? latenciaMs : null,
      });
      idsGuardados.push(guardado.id);
    }
    await repo.marcarAtendidos(pendientes.map((m) => m.id));
    this.log("info", `Conversación ${idConversacion}: respuesta en ${latenciaMs} ms`);
    return { mensajes, idsGuardados, latenciaMs };
  }

  private async pensar(conversacion: Conversacion, idsPendientes: number[], eventos: EventosTurno): Promise<MensajeSalida[]> {
    const { repo, cerebro, cfg, ajustes } = this.d;
    const historial = await repo.historial(conversacion.id, cfg.ia.historial);
    const pendientes = (await repo.pendientes(conversacion.id)).filter((m) => idsPendientes.includes(m.id));

    // Fotos: primero se leen (visión, llamada aparte); el turno recibe la lectura como texto.
    const imagenes = new Map<number, Imagen>();
    for (const m of historial) {
      if (!m.adjunto?.startsWith("imagen:")) continue;
      const id = Number(m.adjunto.slice(7));
      let img = await repo.imagen(id);
      if (!img) continue;
      if (!img.lectura && idsPendientes.includes(m.id)) {
        eventos.herramienta?.("leer_comprobante", "Leyendo tu comprobante");
        try {
          const inicioLectura = Date.now();
          const lectura = await cerebro.leerComprobante(img.datos, img.mime, fechaLima());
          this.log("info", `Imagen ${id} leída en ${Date.now() - inicioLectura} ms`);
          await repo.actualizarImagen(id, { lectura });
          img = { ...img, lectura };
        } catch (e) {
          this.log("warn", `No se pudo leer la imagen ${id}: ${e instanceof Error ? e.message : String(e)}`);
        }
      }
      imagenes.set(id, img);
    }

    const complejo = await ajustes.complejo();
    const celular = conversacion.canal === "WHATSAPP" ? celularDeWhatsapp(conversacion.clave) : null;
    const estado: EstadoTurno = { conversacion };
    const texto = await cerebro.responder({
      sistema: instruccionesSistema(complejo, conversacion.canal, cfg.frontUrl, celular),
      prompt: promptDelTurno(conversacion, historial, pendientes, imagenes),
      herramientas: crearHerramientas({ api: this.d.api.paraConversacion?.(conversacion.id) ?? this.d.api, repo, ajustes, cfg }, estado),
      alUsarHerramienta: (nombre) => eventos.herramienta?.(nombre, TEXTO_HERRAMIENTA[nombre] ?? "Revisando"),
    });
    return interpretarRespuesta(texto, cfg.frontUrl);
  }
}
