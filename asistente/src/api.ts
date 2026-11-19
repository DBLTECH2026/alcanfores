// Cliente de la API de Alcanfores. Las herramientas de Canchita usan la API PÚBLICA por el gateway, igual que el
// portal; la configuración (catálogo), la validación del pago (pagos) y las reservas de un cliente (reservas) van por
// los endpoints /interno, y el cambio de horario va DIRECTO a reservas-service con el token interno (ver
// reprogramarReserva).
import type { Configuracion } from "./config.js";
import type { DatosComprobante, LecturaComprobante } from "./modelo.js";

export interface ErrorCampo {
  campo: string;
  mensaje: string;
}

/** Error de la API con el cuerpo estándar del sistema ({status, codigo, mensaje, errores}). */
export class ErrorApi extends Error {
  constructor(
    readonly status: number,
    readonly codigo: string,
    mensaje: string,
    readonly errores: ErrorCampo[] = [],
  ) {
    super(mensaje);
  }

  /** Texto para el modelo: el mensaje del back y los errores por campo. */
  paraModelo(): string {
    const campos = this.errores.map((e) => `${e.campo}: ${e.mensaje}`).join("; ");
    return `${this.codigo} (${this.status}): ${this.message}${campos ? ` [${campos}]` : ""}`;
  }
}

export interface Complejo {
  nombre: string;
  lema: string;
  direccion: string;
  referencia: string;
  whatsapp: string;
  correo: string;
  mapaUrl: string;
  horario: { primerTurno: string; ultimoTurno: string; duracionMin: number; inicioNoche: string; diasVisibles: number };
  reglas: { adelantoPct: number; devolucionHoras: number; minutosParaPagar: number; recordatorioHoras: number };
  pagos: { yapeNumero: string; yapeTitular: string; plinNumero: string };
  /** Desde catálogo V6: si Canchita aparece en la web. */
  asistente?: { webActiva: boolean };
}

export interface Cancha {
  id: number;
  slug: string;
  nombre: string;
  deporte: string;
  deporteTexto: string;
  superficie: string;
  capacidad: number;
  techada: boolean;
  iluminacionLed: boolean;
  descripcion: string;
  precioDia: number;
  precioNoche: number;
}

export interface Turno {
  idTurno: number;
  horaInicio: string;
  horaFin: string;
  precio: number;
  franja: "DIA" | "NOCHE";
  estado: "DISPONIBLE" | "OCUPADO" | "BLOQUEADO" | "PASADO";
  feriado?: boolean;
  recargo?: number;
}

export interface Disponibilidad {
  fecha: string;
  canchas: { idCancha: number; nombre: string; deporte: string; turnos: Turno[] }[];
}

export interface PersonaDni {
  dni: string;
  nombres: string;
  apellidos: string;
}

export interface ClienteReserva {
  dni: string;
  nombres: string;
  apellidos: string;
  telefono: string;
  correo: string;
}

export interface TurnoReserva {
  idTurno: number;
  horaInicio: string;
  horaFin: string;
  precio: number;
}

export interface ReservaCreada {
  codigoReserva: string;
  estado: string;
  estadoPago: string;
  venceEn: string | null;
  cancha: { id: number; nombre: string; deporte: string };
  fecha: string;
  horaInicio: string;
  horaFin: string;
  montoTotal: number;
  montoAdelanto: number;
  horarioTexto: string;
  cantidadTurnos: number;
  turnos: TurnoReserva[];
}

export interface ReservaDetalle extends ReservaCreada {
  montoPagado: number;
  saldo: number;
  asistencia: string;
  metodoPagoPrevisto: string | null;
  puedeCancelar: boolean;
  puedeReenviarComprobante: boolean;
  cancelacion: { canceladoPor: string; motivo: string; devolucion: boolean; montoDevolucion: number } | null;
  // Desde el cambio de horario (SPEC_V2_CAMBIOS § K). Opcionales: una versión anterior de reservas no los envía.
  /** Cuántos cambios de horario tuvo (del cliente, de Canchita y del panel). */
  reprogramaciones?: number;
  /** Reglas del cliente y Canchita: vigente, sin asistencia, a más de 2 h del inicio y sin haber usado su único cambio. */
  puedeReprogramar?: boolean;
  /** Monto del comprobante que se está validando (0 si no hay). */
  montoEnRevision?: number;
}

export interface PagoRegistrado {
  idPago: number;
  estado: string;
  mensaje: string;
}

export interface IntentoCreado {
  referencia: string;
  urlCheckout: string;
  expiraEn: string;
}

export interface ResultadoValidacionIa {
  idPago: number;
  estado: string;
  mensaje?: string;
}

/** Reserva de un cliente encontrada por DNI + celular (GET /interno/reservas/por-cliente). */
export interface ReservaDelCliente {
  codigo: string;
  cancha: string;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  turnos: number | TurnoReserva[];
  estado: string;
  estadoPago: string;
  montoTotal: number;
  pagado: number;
  saldo: number;
}

export interface ApiAlcanfores {
  complejo(): Promise<Complejo>;
  canchas(): Promise<Cancha[]>;
  disponibilidad(fecha: string, idCancha?: number): Promise<Disponibilidad>;
  consultarDni(dni: string): Promise<PersonaDni>;
  crearReserva(idsTurno: number[], cliente: ClienteReserva, metodoPago: string): Promise<ReservaCreada>;
  consultarReserva(codigo: string, dni: string): Promise<ReservaDetalle>;
  crearIntentoTarjeta(codigo: string, dni: string, tipoPago: "ADELANTO" | "TOTAL", comprobante?: DatosComprobante): Promise<IntentoCreado>;
  subirComprobante(datos: {
    codigo: string; dni: string; metodo: "YAPE" | "PLIN"; codigoOperacion: string; imagen: Buffer; mime: string;
    comprobante?: DatosComprobante;
  }): Promise<PagoRegistrado>;
  cancelarReserva(codigo: string, dni: string, motivo: string): Promise<ReservaDetalle>;
  /** Reservas de ese DNI cuyo celular registrado es `telefono` (9 dígitos), próximas o de los últimos 30 días. */
  misReservas(dni: string, telefono: string): Promise<ReservaDelCliente[]>;
  reprogramarReserva(codigo: string, dni: string, idsTurno: number[]): Promise<ReservaDetalle>;
  configuracionInterna(): Promise<Record<string, string>>;
  validarPagoIa(idPago: number, lectura: LecturaComprobante): Promise<ResultadoValidacionIa>;
  /** La misma API identificando la conversación ante el gateway (cupo del limitador propio por conversación). */
  paraConversacion?(idConversacion: number): ApiAlcanfores;
}

/** Implementación con fetch (Node 22). */
export class ApiHttp implements ApiAlcanfores {
  constructor(
    private readonly cfg: Configuracion,
    private readonly idConversacion: number | null = null,
  ) {}

  paraConversacion(idConversacion: number): ApiAlcanfores {
    return new ApiHttp(this.cfg, idConversacion);
  }

  private async pedir<T>(url: string, init: RequestInit & { tiempoMs?: number } = {}): Promise<T> {
    const control = AbortSignal.timeout(init.tiempoMs ?? 15_000);
    let res: Response;
    try {
      res = await fetch(url, { ...init, signal: control, headers: { Accept: "application/json", ...(init.headers ?? {}) } });
    } catch (e) {
      const tiempo = e instanceof Error && (e.name === "TimeoutError" || e.name === "AbortError");
      throw new ErrorApi(503, "SERVICIO_NO_DISPONIBLE",
        tiempo ? "El sistema de reservas tardó demasiado en responder." : "No se pudo conectar con el sistema de reservas.");
    }
    const texto = await res.text();
    let cuerpo: unknown = null;
    try {
      cuerpo = texto ? JSON.parse(texto) : null;
    } catch {
      cuerpo = null;
    }
    if (!res.ok) {
      const e = (cuerpo ?? {}) as { codigo?: string; mensaje?: string; errores?: ErrorCampo[] };
      throw new ErrorApi(res.status, e.codigo ?? `HTTP_${res.status}`, e.mensaje ?? "El sistema de reservas respondió con un error.",
        Array.isArray(e.errores) ? e.errores : []);
    }
    return cuerpo as T;
  }

  /**
   * API pública por el gateway. Canchita llama por la red interna con el token interno y su conversación: así el
   * limitador del gateway le da un cupo por conversación y no uno solo para todos sus clientes. El gateway borra el
   * token antes de reenviar la solicitud a los servicios.
   */
  private publica<T>(ruta: string, init: RequestInit & { tiempoMs?: number } = {}): Promise<T> {
    const cabeceras: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) };
    if (this.idConversacion !== null) {
      cabeceras["X-Interno-Token"] = this.cfg.internoToken;
      cabeceras["X-Canchita-Conversacion"] = `c${this.idConversacion}`;
    }
    return this.pedir<T>(`${this.cfg.apiUrl}${ruta}`, { ...init, headers: cabeceras });
  }

  private json(cuerpo: unknown): RequestInit & { headers: Record<string, string> } {
    return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(cuerpo) };
  }

  complejo(): Promise<Complejo> {
    return this.publica("/api/catalogo/complejo");
  }

  canchas(): Promise<Cancha[]> {
    return this.publica("/api/catalogo/canchas");
  }

  disponibilidad(fecha: string, idCancha?: number): Promise<Disponibilidad> {
    const q = new URLSearchParams({ fecha });
    if (idCancha) q.set("idCancha", String(idCancha));
    return this.publica(`/api/disponibilidad?${q}`);
  }

  async consultarDni(dni: string): Promise<PersonaDni> {
    const r = await this.publica<PersonaDni>(`/api/documentos/dni/${encodeURIComponent(dni)}`, { tiempoMs: 20_000 });
    return { dni: r.dni, nombres: r.nombres, apellidos: r.apellidos };
  }

  crearReserva(idsTurno: number[], cliente: ClienteReserva, metodoPago: string): Promise<ReservaCreada> {
    return this.publica("/api/reservas", this.json({ idsTurno, cliente, metodoPago, aceptaTerminos: true, aceptaPrivacidad: true }));
  }

  consultarReserva(codigo: string, dni: string): Promise<ReservaDetalle> {
    return this.publica(`/api/reservas/${encodeURIComponent(codigo)}?dni=${encodeURIComponent(dni)}`);
  }

  crearIntentoTarjeta(codigo: string, dni: string, tipoPago: "ADELANTO" | "TOTAL", comprobante?: DatosComprobante): Promise<IntentoCreado> {
    return this.publica("/api/pagos/pasarela/intentos", this.json({ codigoReserva: codigo, dni, tipoPago, comprobante }));
  }

  subirComprobante(d: {
    codigo: string; dni: string; metodo: "YAPE" | "PLIN"; codigoOperacion: string; imagen: Buffer; mime: string;
    comprobante?: DatosComprobante;
  }): Promise<PagoRegistrado> {
    const form = new FormData();
    form.set("codigoReserva", d.codigo);
    form.set("dni", d.dni);
    form.set("metodo", d.metodo);
    form.set("codigoOperacion", d.codigoOperacion);
    form.set("archivo", new Blob([new Uint8Array(d.imagen)], { type: d.mime }), d.mime === "image/png" ? "comprobante.png" : "comprobante.jpg");
    if (d.comprobante) form.set("comprobante", JSON.stringify(d.comprobante));
    return this.publica("/api/pagos/comprobante", { method: "POST", body: form, tiempoMs: 20_000 });
  }

  cancelarReserva(codigo: string, dni: string, motivo: string): Promise<ReservaDetalle> {
    return this.publica(`/api/reservas/${encodeURIComponent(codigo)}/cancelar`, this.json({ dni, motivo }));
  }

  misReservas(dni: string, telefono: string): Promise<ReservaDelCliente[]> {
    const q = new URLSearchParams({ dni, telefono });
    return this.pedir(`${this.cfg.reservasUrl}/interno/reservas/por-cliente?${q}`, {
      headers: { "X-Interno-Token": this.cfg.internoToken }, tiempoMs: 10_000,
    });
  }

  /**
   * DIRECTO a reservas-service por la red interna, no por el gateway: el gateway borra `X-Interno-Token` en todas las
   * rutas y el cambio quedaría con actor «Cliente». Con el token y la conversación, reservas lo registra como
   * «Canchita (IA) · conversación cN» (bitácora, historial y línea de tiempo). Mismo cuerpo y mismos errores que la ruta
   * pública (404, 409, 422 con su `mensaje`).
   */
  reprogramarReserva(codigo: string, dni: string, idsTurno: number[]): Promise<ReservaDetalle> {
    const cabeceras: Record<string, string> = { "Content-Type": "application/json", "X-Interno-Token": this.cfg.internoToken };
    if (this.idConversacion !== null) cabeceras["X-Canchita-Conversacion"] = `c${this.idConversacion}`;
    return this.pedir(`${this.cfg.reservasUrl}/api/reservas/${encodeURIComponent(codigo)}/reprogramar`, {
      method: "POST", headers: cabeceras, body: JSON.stringify({ dni, idsTurno }), tiempoMs: 15_000,
    });
  }

  configuracionInterna(): Promise<Record<string, string>> {
    return this.pedir(`${this.cfg.catalogoUrl}/interno/configuracion`, {
      headers: { "X-Interno-Token": this.cfg.internoToken }, tiempoMs: 5_000,
    });
  }

  validarPagoIa(idPago: number, lectura: LecturaComprobante): Promise<ResultadoValidacionIa> {
    return this.pedir(`${this.cfg.pagosUrl}/interno/pagos/${idPago}/validar-ia`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Interno-Token": this.cfg.internoToken },
      body: JSON.stringify({ lectura, validadoPor: "Canchita (IA)" }),
      tiempoMs: 15_000,
    });
  }
}
