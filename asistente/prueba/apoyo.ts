// Dobles de prueba: API de Alcanfores en memoria, cerebro simulado (decide herramientas con un guion) y WhatsApp falso.
import { ErrorApi, type ApiAlcanfores, type Cancha, type ClienteReserva, type Complejo, type Disponibilidad,
  type IntentoCreado, type PagoRegistrado, type PersonaDni, type ReservaCreada, type ReservaDelCliente, type ReservaDetalle,
  type ResultadoValidacionIa } from "../src/api.js";
import { crearAplicacion } from "../src/app.js";
import { RepositorioMemoria } from "../src/bd/repositorioMemoria.js";
import type { Cerebro, DefinicionHerramienta, EntradaTurno } from "../src/cerebro/tipos.js";
import { leerConfiguracion, type Configuracion } from "../src/config.js";
import type { DatosComprobante, LecturaComprobante } from "../src/modelo.js";
import { firmarJwt } from "../src/rutas/comun.js";
import { fechaLima, instanteDesdeLima, sumarDias } from "../src/tiempo.js";
import type { ClienteWhatsapp, EnvioWhatsapp } from "../src/whatsapp/evolution.js";

export const COMPLEJO: Complejo = {
  nombre: "Complejo Deportivo Los Alcanfores", lema: "Reserva tu cancha.", direccion: "Carretera Central km 11.5, Santa Clara, Ate",
  referencia: "Paradero Caseta", whatsapp: "914995187", correo: "informes@example.com", mapaUrl: "https://maps.example.com",
  horario: { primerTurno: "08:00", ultimoTurno: "23:00", duracionMin: 60, inicioNoche: "18:00", diasVisibles: 14 },
  reglas: { adelantoPct: 50, devolucionHoras: 24, minutosParaPagar: 20, recordatorioHoras: 2 },
  pagos: { yapeNumero: "900111222", yapeTitular: "Complejo Deportivo Alcanfores", plinNumero: "900111222" },
};

interface TurnoFalso { idTurno: number; idCancha: number; fecha: string; horaInicio: string; horaFin: string; precio: number; libre: boolean }

export class ApiFalsa implements ApiAlcanfores {
  configuracion: Record<string, string> = { adelanto_pct: "50" };
  turnos: TurnoFalso[] = [];
  reservas = new Map<string, ReservaDetalle & { dni: string; telefono: string }>();
  /** Simula que reservas-service aún no tiene por-cliente ni reprogramar (404). */
  sinEndpointsNuevos = false;
  reprogramaciones: { codigo: string; dni: string; idsTurno: number[] }[] = [];
  /** Error con el que respondería reservas-service al confirmar el cambio (409/422 de negocio). */
  errorReprogramar: ErrorApi | null = null;
  /** Reservas guarda el cambio pero la respuesta no llega: el siguiente cambio se aplica y lanza el 503 de ApiHttp.pedir. */
  perderRespuestaReprogramar = false;
  /** Para variar el % de adelanto u otros datos del complejo en una prueba (antes de la primera consulta: hay caché). */
  datosComplejo: Complejo = COMPLEJO;
  pagos: { idPago: number; codigo: string; metodo: string; codigoOperacion: string; comprobante?: DatosComprobante }[] = [];
  validaciones: { idPago: number; lectura: LecturaComprobante }[] = [];
  fallarValidacion = false;
  private secuencia = 0;

  constructor() {
    const manana = sumarDias(fechaLima(), 1);
    let id = 100;
    for (const idCancha of [1, 2]) {
      for (let h = 8; h < 23; h++) {
        this.turnos.push({
          idTurno: id++, idCancha, fecha: manana, horaInicio: `${String(h).padStart(2, "0")}:00`,
          horaFin: `${String(h + 1).padStart(2, "0")}:00`, precio: h >= 18 ? 80 : 60, libre: true,
        });
      }
    }
  }

  async complejo(): Promise<Complejo> { return this.datosComplejo; }

  async canchas(): Promise<Cancha[]> {
    return [1, 2].map((id) => ({
      id, slug: `cancha-${id}`, nombre: `Cancha ${id} · Fútbol ${id + 4}`, deporte: `FUTBOL_${id + 4}`, deporteTexto: `Fútbol ${id + 4}`,
      superficie: "Grass sintético", capacidad: 10, techada: true, iluminacionLed: true, descripcion: "Cancha techada", precioDia: 60, precioNoche: 80,
    }));
  }

  async disponibilidad(fecha: string, idCancha?: number): Promise<Disponibilidad> {
    const canchas = [1, 2].filter((c) => !idCancha || c === idCancha);
    return {
      fecha,
      canchas: canchas.map((c) => ({
        idCancha: c, nombre: `Cancha ${c}`, deporte: "FUTBOL_5",
        turnos: this.turnos.filter((t) => t.idCancha === c && t.fecha === fecha).map((t) => ({
          idTurno: t.idTurno, horaInicio: t.horaInicio, horaFin: t.horaFin, precio: t.precio, franja: "DIA" as const,
          estado: t.libre ? "DISPONIBLE" as const : "OCUPADO" as const,
        })),
      })),
    };
  }

  async consultarDni(dni: string): Promise<PersonaDni> {
    if (dni === "00000000") throw new ErrorApi(404, "DNI_NO_ENCONTRADO", "No encontramos ese DNI.");
    return { dni, nombres: "Juan Carlos", apellidos: "Prueba Ficticio" };
  }

  async crearReserva(idsTurno: number[], cliente: ClienteReserva, metodoPago: string): Promise<ReservaCreada> {
    const turnos = idsTurno.map((id) => this.turnos.find((t) => t.idTurno === id));
    if (turnos.some((t) => !t)) throw new ErrorApi(400, "VALIDACION", "Ese turno ya no está disponible.");
    if (turnos.some((t) => !t!.libre)) throw new ErrorApi(409, "TURNO_OCUPADO", "Ese turno acaba de ser reservado.");
    turnos.forEach((t) => (t!.libre = false));
    const total = turnos.reduce((s, t) => s + t!.precio, 0);
    const codigo = `ALC-${"ABCDEFGHJK"[this.secuencia++ % 10]}2345X`;
    const r = {
      codigoReserva: codigo, estado: "PENDIENTE", estadoPago: "SIN_PAGO", venceEn: `${fechaLima()}T23:50:00`,
      cancha: { id: turnos[0]!.idCancha, nombre: `Cancha ${turnos[0]!.idCancha}`, deporte: "FUTBOL_5" }, fecha: turnos[0]!.fecha,
      horaInicio: turnos[0]!.horaInicio, horaFin: turnos.at(-1)!.horaFin, montoTotal: total, montoAdelanto: total / 2,
      horarioTexto: "7:00–8:00 p. m.", cantidadTurnos: turnos.length,
      turnos: turnos.map((t) => ({ idTurno: t!.idTurno, horaInicio: t!.horaInicio, horaFin: t!.horaFin, precio: t!.precio })),
      montoPagado: 0, saldo: total, asistencia: "PENDIENTE", metodoPagoPrevisto: metodoPago, puedeCancelar: true,
      puedeReenviarComprobante: false, cancelacion: null, dni: cliente.dni, telefono: cliente.telefono,
      reprogramaciones: 0, puedeReprogramar: true, montoEnRevision: 0,
    };
    this.reservas.set(codigo, r);
    return r;
  }

  async consultarReserva(codigo: string, dni: string): Promise<ReservaDetalle> {
    const r = this.reservas.get(codigo);
    if (!r || r.dni !== dni) throw new ErrorApi(404, "NO_ENCONTRADO", "No encontramos esa reserva.");
    return r;
  }

  async crearIntentoTarjeta(codigo: string, dni: string): Promise<IntentoCreado> {
    await this.consultarReserva(codigo, dni);
    return { referencia: "ALP-ABCDEFGHJK", urlCheckout: "/pasarela/ALP-ABCDEFGHJK", expiraEn: `${fechaLima()}T23:59:00` };
  }

  async subirComprobante(d: { codigo: string; dni: string; metodo: "YAPE" | "PLIN"; codigoOperacion: string; comprobante?: DatosComprobante }): Promise<PagoRegistrado> {
    await this.consultarReserva(d.codigo, d.dni);
    if (this.pagos.some((p) => p.codigoOperacion === d.codigoOperacion)) {
      throw new ErrorApi(409, "OPERACION_REPETIDA", "Ese número de operación ya se registró.");
    }
    const idPago = 500 + this.pagos.length;
    this.pagos.push({ idPago, codigo: d.codigo, metodo: d.metodo, codigoOperacion: d.codigoOperacion, comprobante: d.comprobante });
    return { idPago, estado: "PENDIENTE", mensaje: "Recibimos tu comprobante." };
  }

  async cancelarReserva(codigo: string, dni: string, motivo: string): Promise<ReservaDetalle> {
    const r = await this.consultarReserva(codigo, dni);
    r.estado = "CANCELADA";
    r.cancelacion = { canceladoPor: "CLIENTE", motivo, devolucion: false, montoDevolucion: 0 };
    return r;
  }

  async misReservas(dni: string, telefono: string): Promise<ReservaDelCliente[]> {
    if (this.sinEndpointsNuevos) throw new ErrorApi(404, "NO_ENCONTRADO", "La ruta no existe.");
    return [...this.reservas.values()].filter((r) => r.dni === dni && r.telefono === telefono).map((r) => ({
      codigo: r.codigoReserva, cancha: r.cancha.nombre, fecha: r.fecha, horaInicio: r.horaInicio, horaFin: r.horaFin,
      turnos: r.cantidadTurnos, estado: r.estado, estadoPago: r.estadoPago, montoTotal: r.montoTotal, pagado: r.montoPagado, saldo: r.saldo,
    }));
  }

  async reprogramarReserva(codigo: string, dni: string, idsTurno: number[]): Promise<ReservaDetalle> {
    if (this.sinEndpointsNuevos) throw new ErrorApi(404, "NO_ENCONTRADO", "La ruta no existe.");
    const r = await this.consultarReserva(codigo, dni);
    if (this.errorReprogramar) throw this.errorReprogramar;
    const turnos = idsTurno.map((id) => this.turnos.find((t) => t.idTurno === id)!);
    for (const t of r.turnos) this.turnos.find((x) => x.idTurno === t.idTurno)!.libre = true;
    turnos.forEach((t) => (t.libre = false));
    this.reprogramaciones.push({ codigo, dni, idsTurno });
    const total = turnos.reduce((x, t) => x + t.precio, 0);
    Object.assign(r, {
      fecha: turnos[0].fecha, horaInicio: turnos[0].horaInicio, horaFin: turnos.at(-1)!.horaFin, cantidadTurnos: turnos.length,
      horarioTexto: "8:00–9:00 p. m.",
      turnos: turnos.map((t) => ({ idTurno: t.idTurno, horaInicio: t.horaInicio, horaFin: t.horaFin, precio: t.precio })),
      montoTotal: total, montoAdelanto: total / 2, saldo: total - r.montoPagado,
      reprogramaciones: (r.reprogramaciones ?? 0) + 1, puedeReprogramar: false,
    });
    // Como ReprogramadorReservas: venceEn se conserva, pero nunca queda después del nuevo inicio.
    const inicio = `${r.fecha}T${r.horaInicio.slice(0, 5)}:00`;
    if (r.venceEn && instanteDesdeLima(r.venceEn)!.getTime() > instanteDesdeLima(inicio)!.getTime()) r.venceEn = inicio;
    if (this.perderRespuestaReprogramar) {
      this.perderRespuestaReprogramar = false;
      throw new ErrorApi(503, "SERVICIO_NO_DISPONIBLE", "El sistema de reservas tardó demasiado en responder.");
    }
    return r;
  }

  async configuracionInterna(): Promise<Record<string, string>> { return this.configuracion; }

  async validarPagoIa(idPago: number, lectura: LecturaComprobante): Promise<ResultadoValidacionIa> {
    if (this.fallarValidacion) throw new ErrorApi(503, "SERVICIO_NO_DISPONIBLE", "El servicio de pagos no responde.");
    this.validaciones.push({ idPago, lectura });
    return { idPago, estado: "VALIDADO" };
  }
}

/** Guion del cerebro simulado: recibe el turno y puede llamar herramientas por su nombre. */
export type Guion = (turno: {
  entrada: EntradaTurno;
  llamar: (nombre: string, args?: Record<string, unknown>) => Promise<Record<string, unknown>>;
}) => Promise<string>;

export class CerebroFalso implements Cerebro {
  turnos: EntradaTurno[] = [];
  resultados: { nombre: string; args: Record<string, unknown>; resultado: Record<string, unknown> }[] = [];
  lecturas: LecturaComprobante[] = [];
  lecturasPedidas = 0;
  conCredencial = true;

  constructor(public guion: Guion = async () => "Hola, soy Canchita.") {}

  disponible(): boolean { return this.conCredencial; }

  async responder(entrada: EntradaTurno): Promise<string> {
    this.turnos.push(entrada);
    const porNombre = new Map<string, DefinicionHerramienta>(entrada.herramientas.map((h) => [h.nombre, h]));
    return this.guion({
      entrada,
      llamar: async (nombre, args = {}) => {
        const h = porNombre.get(nombre);
        if (!h) throw new Error(`Herramienta desconocida: ${nombre}`);
        entrada.alUsarHerramienta?.(nombre);
        const resultado = (await h.ejecutar(args)) as Record<string, unknown>;
        this.resultados.push({ nombre, args, resultado });
        return resultado;
      },
    });
  }

  async leerComprobante(): Promise<LecturaComprobante> {
    this.lecturasPedidas++;
    const l = this.lecturas.shift();
    if (!l) throw new Error("Sin lectura preparada");
    return l;
  }
}

export class WhatsappFalso implements ClienteWhatsapp {
  enviados: { numero: string; texto: string; id: string }[] = [];
  webhooks: { activo: boolean; url: string }[] = [];
  medios = new Map<string, { base64: string; mimetype: string }>();
  presencias = 0;
  private n = 0;

  configurado(): boolean { return true; }
  async enviarTexto(numero: string, texto: string): Promise<EnvioWhatsapp> {
    const id = `3EB0CANCHITA${++this.n}`;
    this.enviados.push({ numero, texto, id });
    return { key: { id, fromMe: true } };
  }
  async escribiendo(): Promise<void> { this.presencias++; }
  async descargarMedia(id: string) { return this.medios.get(id) ?? null; }
  async configurarWebhook(activo: boolean, url: string): Promise<void> { this.webhooks.push({ activo, url }); }
}

/** PNG de 1×1 válido (bytes mágicos correctos). */
export const PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

export function configuracionPrueba(): Configuracion {
  const cfg = leerConfiguracion();
  return { ...cfg, frontUrl: "http://localhost:5186", webhookToken: "token-prueba", whatsappHumano: "914995187" };
}

export function crearEntorno(guion?: Guion) {
  const cfg = configuracionPrueba();
  const repo = new RepositorioMemoria();
  const api = new ApiFalsa();
  const cerebro = new CerebroFalso(guion);
  const wa = new WhatsappFalso();
  const aplicacion = crearAplicacion({ cfg, repo, api, cerebro, wa });
  return { cfg, repo, api, cerebro, wa, ...aplicacion };
}

export function tokenPanel(cfg: Configuracion, rol = "ADMIN"): string {
  return firmarJwt({ sub: "1", usuario: "rhuaman", nombre: "Rosa Huamán", rol, exp: Math.floor(Date.now() / 1000) + 3600 }, cfg.jwtSecreto);
}

export function lecturaQueCalza(monto: number, operacion = "12345678", fechaHora = `${fechaLima()}T00:00`): LecturaComprobante {
  return {
    esComprobante: true, app: "YAPE", monto, codigoOperacion: operacion, fechaHora, destinatario: "Complejo Deport* Alcanf*",
    numeroDestino: "222", marcaDeAgua: false, pareceEditada: false, descripcion: "Constancia de Yape",
  };
}

/** Saca el idImagen que el prompt le muestra al modelo («[Foto idImagen=N …]»). */
export function idImagenDelPrompt(prompt: string): number | null {
  const m = /idImagen=(\d+)/.exec(prompt);
  return m ? Number(m[1]) : null;
}
