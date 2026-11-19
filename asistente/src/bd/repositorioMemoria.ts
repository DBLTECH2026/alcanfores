// Repositorio en memoria: el mismo contrato que el de MySQL, para las pruebas (no necesitan base de datos).
import type {
  Canal, Conversacion, Imagen, Mensaje, NuevoMensaje, Repositorio, ResumenConversacion,
} from "../modelo.js";
import { ahora, fechaLima } from "../tiempo.js";

export class RepositorioMemoria implements Repositorio {
  private conversaciones: Conversacion[] = [];
  private mensajes: Mensaje[] = [];
  private imagenes: Imagen[] = [];

  async obtenerOCrearConversacion(canal: Canal, clave: string, nombre?: string | null): Promise<Conversacion> {
    const existente = this.conversaciones.find((c) => c.canal === canal && c.clave === clave);
    if (existente) {
      if (nombre && !existente.nombre) existente.nombre = nombre;
      return { ...existente };
    }
    const c: Conversacion = {
      id: this.conversaciones.length + 1, canal, clave, nombre: nombre ?? null, estado: "ACTIVA", pausadaHasta: null,
      codigoReserva: null, dni: null, contexto: {}, creadaEn: ahora(), ultimaActividad: ahora(),
    };
    this.conversaciones.push(c);
    return { ...c };
  }

  async conversacion(id: number): Promise<Conversacion | null> {
    const c = this.conversaciones.find((x) => x.id === id);
    return c ? { ...c, contexto: { ...c.contexto } } : null;
  }

  async buscarConversacion(canal: Canal, clave: string): Promise<Conversacion | null> {
    const c = this.conversaciones.find((x) => x.canal === canal && x.clave === clave);
    return c ? { ...c } : null;
  }

  async actualizarConversacion(id: number, cambios: Partial<Conversacion>): Promise<Conversacion> {
    const c = this.conversaciones.find((x) => x.id === id);
    if (!c) throw new Error(`No existe la conversación ${id}`);
    Object.assign(c, cambios);
    return { ...c };
  }

  async agregarMensaje(m: NuevoMensaje): Promise<Mensaje> {
    const nuevo: Mensaje = {
      id: this.mensajes.length + 1, idConversacion: m.idConversacion, rol: m.rol, texto: m.texto,
      adjunto: m.adjunto ?? null, idExterno: m.idExterno ?? null, latenciaMs: m.latenciaMs ?? null, extras: m.extras ?? null,
      atendido: m.atendido ?? m.rol !== "CLIENTE", creadoEn: ahora(),
    };
    this.mensajes.push(nuevo);
    const c = this.conversaciones.find((x) => x.id === m.idConversacion);
    if (c) c.ultimaActividad = ahora();
    return { ...nuevo };
  }

  async existeMensajeExterno(idExterno: string): Promise<boolean> {
    return this.mensajes.some((m) => m.idExterno === idExterno);
  }

  async anotarIdExterno(idMensaje: number, idExterno: string): Promise<void> {
    const m = this.mensajes.find((x) => x.id === idMensaje);
    if (m) m.idExterno = idExterno;
  }

  async historial(idConversacion: number, limite: number): Promise<Mensaje[]> {
    return this.mensajes.filter((m) => m.idConversacion === idConversacion).slice(-limite).map((m) => ({ ...m }));
  }

  async pendientes(idConversacion: number): Promise<Mensaje[]> {
    return this.mensajes.filter((m) => m.idConversacion === idConversacion && m.rol === "CLIENTE" && !m.atendido)
      .map((m) => ({ ...m }));
  }

  async marcarAtendidos(ids: number[]): Promise<void> {
    for (const m of this.mensajes) if (ids.includes(m.id)) m.atendido = true;
  }

  async guardarImagen(idConversacion: number, mime: string, datos: Buffer): Promise<Imagen> {
    const i: Imagen = {
      id: this.imagenes.length + 1, idConversacion, mime, datos, lectura: null, idPago: null, validacion: null, creadoEn: ahora(),
    };
    this.imagenes.push(i);
    return { ...i };
  }

  async imagen(id: number): Promise<Imagen | null> {
    const i = this.imagenes.find((x) => x.id === id);
    return i ? { ...i } : null;
  }

  async imagenPorPago(idPago: number): Promise<Imagen | null> {
    const i = this.imagenes.find((x) => x.idPago === idPago);
    return i ? { ...i } : null;
  }

  async actualizarImagen(id: number, cambios: Partial<Imagen>): Promise<void> {
    const i = this.imagenes.find((x) => x.id === id);
    if (i) Object.assign(i, cambios);
  }

  async ultimasConversaciones(limite: number): Promise<ResumenConversacion[]> {
    return [...this.conversaciones]
      .sort((a, b) => b.ultimaActividad.getTime() - a.ultimaActividad.getTime() || b.id - a.id)
      .slice(0, limite)
      .map((c) => ({
        conversacion: { ...c },
        ultimoMensaje: [...this.mensajes].reverse().find((m) => m.idConversacion === c.id) ?? null,
      }));
  }

  async contarConversaciones(): Promise<{ activas: number; pausadas: number; hoy: number }> {
    const hoy = fechaLima();
    return {
      activas: this.conversaciones.filter((c) => c.estado === "ACTIVA").length,
      pausadas: this.conversaciones.filter((c) => c.estado === "PAUSADA").length,
      hoy: this.conversaciones.filter((c) => fechaLima(c.ultimaActividad) === hoy).length,
    };
  }

  async latenciaPromedioMs(ultimos: number): Promise<number | null> {
    const l = this.mensajes.filter((m) => m.latenciaMs != null).slice(-ultimos).map((m) => m.latenciaMs!);
    return l.length ? Math.round(l.reduce((a, b) => a + b, 0) / l.length) : null;
  }

  async cerrar(): Promise<void> {}
}
