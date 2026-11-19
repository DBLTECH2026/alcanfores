// Herramientas de Canchita. Llaman a la API pública (como el portal) y SOLO a ella; la validación automática del pago
// la deciden las reglas en código (reglasPago.ts) con la lectura guardada de la foto, nunca datos que escriba el modelo.
import { z } from "zod";
import type { Ajustes } from "./ajustes.js";
import { ErrorApi, type ApiAlcanfores, type ReservaDetalle, type Turno } from "./api.js";
import type { DefinicionHerramienta } from "./cerebro/tipos.js";
import type { Configuracion } from "./config.js";
import type { Conversacion, DatosComprobante, Imagen, Repositorio } from "./modelo.js";
import { evaluarComprobante } from "./reglasPago.js";
import { adelantoEnCentimos, cambioYaAplicado, centimos, MENSAJES, MISMO_HORARIO, mismoHorario, motivoSinCambio,
  plazoTrasCambio, revisarMontos, type Rechazo } from "./reglasReprogramar.js";
import { celularDeWhatsapp } from "./prompt.js";
import { ahora, fechaLima, hora12Texto, sumarDias, textoFecha, instanteDesdeLima, hora12 } from "./tiempo.js";

export interface DependenciasHerramientas {
  api: ApiAlcanfores;
  repo: Repositorio;
  ajustes: Ajustes;
  cfg: Configuracion;
}

/** Estado compartido por las herramientas durante un turno: la conversación se actualiza al reservar o consultar. */
export interface EstadoTurno {
  conversacion: Conversacion;
}

const dni = z.string().regex(/^\d{8}$/, "El DNI debe tener 8 dígitos.");
const codigo = z.string().regex(/^\s*ALC-[A-HJ-NP-Z2-9]{6}\s*$/i, "El código tiene el formato ALC-XXXXXX.");

/** «+51 987 654 321» → «987654321». */
export function normalizarCelular(texto: string): string {
  const d = texto.replace(/\D/g, "");
  return d.length === 11 && d.startsWith("51") ? d.slice(2) : d;
}

function rango(t: { horaInicio: string; horaFin: string }): string {
  const fin = hora12Texto(t.horaFin);
  const inicio = hora12Texto(t.horaInicio);
  const sufijoInicio = inicio.slice(-5);
  return sufijoInicio === fin.slice(-5) ? `${inicio.slice(0, -6)}–${fin}` : `${inicio}–${fin}`;
}

function soles(n: number): string {
  return `S/ ${Number(n).toFixed(2)}`;
}

/** Plazo en hora de Lima: «9:00 p. m.» si es hoy; si no, «10:00 a. m. del viernes 20 de noviembre». */
function horaConDia(d: Date): string {
  const l = new Date(d.getTime() - 5 * 3_600_000);
  const hora = hora12(l.getUTCHours(), l.getUTCMinutes());
  const dia = fechaLima(d);
  return dia === fechaLima() ? hora : `${hora} del ${textoFecha(dia)}`;
}

function horaDe(fechaHora: string | null): string | null {
  const d = instanteDesdeLima(fechaHora);
  return d ? horaConDia(d) : null;
}

const NO_DISPONIBLE = "Esta función aún no está disponible. Pide el código de la reserva (ALC-XXXXXX) y el DNI, u ofrece el WhatsApp del equipo.";
const CAMBIO_NO_DISPONIBLE = "Cambiar el horario por aquí aún no está disponible. Ofrece el WhatsApp del equipo.";

/** Regla de reservas que impide el cambio: su mensaje tal cual (el modelo lo explica) y qué hacer. */
function sinCambio(r: Rechazo, siguientePaso: string) {
  return { error: r.mensaje, codigo: r.codigo, siguientePaso };
}

function error(e: unknown): { error: string } {
  if (e instanceof ErrorApi) return { error: e.paraModelo() };
  return { error: "No se pudo completar la operación por un problema interno. Ofrece reintentar o el WhatsApp del equipo." };
}

function resumenReserva(r: ReservaDetalle, frontUrl: string) {
  return {
    codigoReserva: r.codigoReserva,
    estado: r.estado,
    estadoPago: r.estadoPago,
    cancha: r.cancha?.nombre,
    fecha: textoFecha(r.fecha),
    horario: r.horarioTexto,
    turnos: r.cantidadTurnos,
    montoTotal: soles(r.montoTotal),
    montoAdelanto: soles(r.montoAdelanto),
    montoPagado: soles(r.montoPagado ?? 0),
    saldo: soles(r.saldo ?? 0),
    pagarAntesDe: horaDe(r.venceEn),
    puedeCancelar: r.puedeCancelar,
    cancelacion: r.cancelacion ? { motivo: r.cancelacion.motivo, devolucion: r.cancelacion.devolucion } : null,
    enlaceMiReserva: `${frontUrl}/mi-reserva?codigo=${r.codigoReserva}`,
  };
}

export function crearHerramientas(deps: DependenciasHerramientas, estado: EstadoTurno): DefinicionHerramienta[] {
  const { api, repo, ajustes, cfg } = deps;

  async function recordarReserva(codigoReserva: string, dniCliente: string, extra: Partial<Conversacion["contexto"]> = {}) {
    const c = estado.conversacion;
    const mismaReserva = c.codigoReserva === codigoReserva;
    estado.conversacion = await repo.actualizarConversacion(c.id, {
      codigoReserva, dni: dniCliente,
      contexto: { ...(mismaReserva ? c.contexto : {}), ...extra },
    });
  }

  async function imagenDeLaConversacion(idImagen: number): Promise<Imagen | null> {
    const img = await repo.imagen(idImagen);
    return img && img.idConversacion === estado.conversacion.id ? img : null;
  }

  /** Aplica las reglas y, si todo calza y el interruptor está encendido, valida el pago por el endpoint interno. */
  async function intentarValidacion(img: Imagen, idPago: number, codigoReserva: string, dniCliente: string,
    metodo: "YAPE" | "PLIN", codigoOperacion: string) {
    const registro = (estadoPago: string, motivos: string[]) => ({ estadoPago, motivos, metodo, codigoOperacion, codigoReserva, dni: dniCliente });
    const interruptores = await ajustes.interruptores();
    if (!interruptores.validaPagos) {
      const r = registro("PENDIENTE", ["La validación automática está apagada."]);
      await repo.actualizarImagen(img.id, { validacion: JSON.stringify(r) });
      return r;
    }
    const [reserva, complejo] = await Promise.all([api.consultarReserva(codigoReserva, dniCliente), ajustes.complejo()]);
    const veredicto = evaluarComprobante(img.lectura, {
      metodo, codigoOperacion, montoAdelanto: Number(reserva.montoAdelanto),
      saldo: Number(reserva.montoTotal) - Number(reserva.montoPagado ?? 0),
      yapeNumero: complejo.pagos.yapeNumero, yapeTitular: complejo.pagos.yapeTitular, plinNumero: complejo.pagos.plinNumero,
    }, ahora());
    let r = registro(veredicto.calza ? "VALIDADO" : "PENDIENTE", veredicto.motivos);
    if (veredicto.calza) {
      try {
        await api.validarPagoIa(idPago, img.lectura!);
      } catch (e) {
        r = registro("PENDIENTE", [`No se pudo validar automáticamente: ${e instanceof ErrorApi ? e.message : "error interno"}`]);
      }
    }
    await repo.actualizarImagen(img.id, { validacion: JSON.stringify(r) });
    return r;
  }

  /** El cambio de horario quedó hecho: recuerda la reserva y devuelve su resumen (yaEstabaAplicado si es un reintento). */
  async function cambioHecho(r: ReservaDetalle, dniCliente: string, yaEstabaAplicado: boolean) {
    await recordarReserva(r.codigoReserva, dniCliente);
    if (!yaEstabaAplicado) return { reprogramada: true, ...resumenReserva(r, cfg.frontUrl) };
    return {
      reprogramada: true, yaEstabaAplicado: true, ...resumenReserva(r, cfg.frontUrl),
      siguientePaso: "El cambio ya estaba hecho: su reserva ya tiene ese horario. Confírmaselo con el horario y el saldo; no "
        + "le digas que no se pudo ni vuelvas a llamar a reprogramar_reserva.",
    };
  }

  /**
   * El cambio de horario falló sin respuesta (tiempo agotado, sin conexión o 5xx), pero reservas pudo haberlo guardado:
   * vuelve a consultar la reserva. Si ya tiene esos turnos, el cambio quedó hecho; si no (o no responde), no se pudo
   * confirmar y se ofrece reintentar, que es seguro porque un reintento ya aplicado devuelve «ya estaba hecho».
   */
  async function revisarCambioTrasFalla(codigoReserva: string, dniCliente: string, ids: number[], falla: ErrorApi) {
    const reintentar = "Ofrece reintentar: llama otra vez a reprogramar_reserva con los mismos datos y clienteConfirmo = true "
      + "(si el cambio ya hubiera quedado hecho, lo dirá), o el WhatsApp del equipo. No le digas que su horario cambió.";
    let r: ReservaDetalle;
    try {
      r = await api.consultarReserva(codigoReserva, dniCliente);
    } catch {
      return sinCambio({ codigo: falla.codigo, mensaje: "No pude confirmar si el cambio de horario quedó hecho: el sistema de "
        + "reservas no respondió a tiempo." }, reintentar);
    }
    if (mismoHorario((r.turnos ?? []).map((t) => t.idTurno), ids)) return cambioHecho(r, dniCliente, false);
    return sinCambio({ codigo: falla.codigo, mensaje: "No pude hacer el cambio: el sistema de reservas no respondió a tiempo y "
      + "por ahora tu reserva sigue con su horario de antes." }, reintentar);
  }

  const herramientas: DefinicionHerramienta[] = [
    {
      nombre: "info_complejo",
      descripcion: "Datos del complejo: dirección, referencia, mapa, WhatsApp del equipo, horario general, reglas "
        + "(adelanto, plazo para pagar, devolución) y datos de Yape/Plin.",
      esquema: {},
      async ejecutar() {
        const c = await ajustes.complejo();
        return {
          nombre: c.nombre, direccion: c.direccion, referencia: c.referencia, mapa: c.mapaUrl,
          whatsappEquipo: c.whatsapp, correo: c.correo,
          horarioGeneral: `${hora12Texto(c.horario.primerTurno)} a ${hora12Texto(c.horario.ultimoTurno)} (cada cancha tiene su horario)`,
          tarifaNocheDesde: hora12Texto(c.horario.inicioNoche),
          reservaHasta: `${c.horario.diasVisibles} días adelante`,
          adelanto: `${c.reglas.adelantoPct} % del total`,
          plazoParaPagar: `${c.reglas.minutosParaPagar} minutos después de reservar`,
          devolucion: `Si cancelas con ${c.reglas.devolucionHoras} h o más de anticipación y ya pagaste, se devuelve lo pagado.`,
          yape: { numero: c.pagos.yapeNumero, titular: c.pagos.yapeTitular },
          plin: { numero: c.pagos.plinNumero },
          terminos: `${cfg.frontUrl}/terminos`, privacidad: `${cfg.frontUrl}/privacidad`,
        };
      },
    },
    {
      nombre: "listar_canchas",
      descripcion: "Canchas activas con deporte, capacidad, superficie y precios de día y de noche.",
      esquema: {},
      async ejecutar() {
        const canchas = await api.canchas();
        return canchas.map((c) => ({
          idCancha: c.id, nombre: c.nombre, deporte: c.deporteTexto, jugadores: c.capacidad, superficie: c.superficie,
          techada: c.techada, precioDia: soles(c.precioDia), precioNoche: soles(c.precioNoche),
        }));
      },
    },
    {
      nombre: "ver_disponibilidad",
      descripcion: "Turnos LIBRES de un día (YYYY-MM-DD) con su precio (ya incluye recargo de feriado). Opcional: idCancha. "
        + "Úsala SIEMPRE antes de ofrecer horarios.",
      esquema: {
        fecha: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Fecha en formato YYYY-MM-DD."),
        idCancha: z.number().int().positive().optional(),
      },
      async ejecutar(args) {
        const { fecha, idCancha } = args as { fecha: string; idCancha?: number };
        const complejo = await ajustes.complejo();
        const hoy = fechaLima();
        const ultimo = sumarDias(hoy, complejo.horario.diasVisibles - 1);
        if (fecha < hoy || fecha > ultimo) {
          return { error: `Solo se reserva entre hoy (${hoy}) y el ${ultimo}.` };
        }
        try {
          const d = await api.disponibilidad(fecha, idCancha);
          return {
            fecha, dia: textoFecha(fecha),
            canchas: d.canchas.map((c) => {
              const libres = c.turnos.filter((t: Turno) => t.estado === "DISPONIBLE");
              return {
                idCancha: c.idCancha, cancha: c.nombre,
                libres: libres.map((t) => ({
                  idTurno: t.idTurno, horario: rango(t), precio: soles(t.precio), ...(t.feriado ? { feriado: true } : {}),
                })),
                ...(libres.length === 0 ? { nota: "Sin turnos libres ese día." } : {}),
              };
            }),
          };
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "consultar_dni",
      descripcion: "Nombres y apellidos de un DNI en RENIEC. Si no aparece o el servicio no responde, pide los datos al cliente.",
      esquema: { dni },
      async ejecutar(args) {
        try {
          const p = await api.consultarDni(String(args.dni));
          return { dni: p.dni, nombres: p.nombres, apellidos: p.apellidos };
        } catch (e) {
          if (e instanceof ErrorApi && e.status === 404) return { error: "DNI no encontrado en RENIEC: pide nombres y apellidos." };
          if (e instanceof ErrorApi && e.status >= 500) return { error: "RENIEC no responde ahora: pide nombres y apellidos." };
          return error(e);
        }
      },
    },
    {
      nombre: "crear_reserva",
      descripcion: "Registra la reserva (1 a 8 turnos de la MISMA cancha y el MISMO día). Llámala SOLO después de mostrar el "
        + "resumen y de que el cliente confirme. metodoPago: YAPE, PLIN o TARJETA. comprobante: boleta (por defecto) o factura.",
      esquema: {
        idsTurno: z.array(z.number().int().positive()).min(1, "Elige entre 1 y 8 turnos.").max(8, "Elige entre 1 y 8 turnos."),
        cliente: z.object({
          dni,
          nombres: z.string().min(2, "Usa solo letras (2 a 60).").max(60, "Usa solo letras (2 a 60)."),
          apellidos: z.string().min(2, "Usa solo letras (2 a 60).").max(60, "Usa solo letras (2 a 60)."),
          telefono: z.string().regex(/^(\+?51)?\s*9[\d\s]{8,11}$/, "Ingresa un celular peruano de 9 dígitos que empiece con 9."),
          correo: z.string().email("Ingresa un correo válido; ahí te enviamos la copia de tu reserva.").max(100),
        }),
        metodoPago: z.enum(["YAPE", "PLIN", "TARJETA"]),
        comprobante: z.object({
          tipo: z.enum(["BOLETA", "FACTURA"]),
          ruc: z.string().optional(),
          razonSocial: z.string().optional(),
          direccionFiscal: z.string().optional(),
        }).optional(),
        clienteConfirmo: z.boolean().describe("true solo si el cliente ya respondió que sí al resumen"),
      },
      async ejecutar(args) {
        const a = args as {
          idsTurno: number[]; metodoPago: string; clienteConfirmo: boolean; comprobante?: DatosComprobante;
          cliente: { dni: string; nombres: string; apellidos: string; telefono: string; correo: string };
        };
        if (!a.clienteConfirmo) return { error: "Primero muestra el resumen y espera a que el cliente confirme." };
        const cliente = { ...a.cliente, telefono: normalizarCelular(a.cliente.telefono), correo: a.cliente.correo.trim().toLowerCase() };
        try {
          const r = await api.crearReserva([...new Set(a.idsTurno)], cliente, a.metodoPago);
          const comprobante = a.comprobante ?? { tipo: "BOLETA" as const };
          await recordarReserva(r.codigoReserva, cliente.dni, {
            comprobante, metodoPago: a.metodoPago, telefono: cliente.telefono, correo: cliente.correo,
          });
          if (!estado.conversacion.nombre) {
            estado.conversacion = await repo.actualizarConversacion(estado.conversacion.id, {
              nombre: `${cliente.nombres} ${cliente.apellidos}`.slice(0, 120),
            });
          }
          const complejo = await ajustes.complejo();
          return {
            codigoReserva: r.codigoReserva, cancha: r.cancha.nombre, fecha: textoFecha(r.fecha), horario: r.horarioTexto,
            montoTotal: soles(r.montoTotal), montoAdelanto: soles(r.montoAdelanto), pagarAntesDe: horaDe(r.venceEn),
            metodoPago: a.metodoPago,
            pago: a.metodoPago === "TARJETA"
              ? "Genera el enlace con link_pago_tarjeta y compártelo."
              : `Que ${a.metodoPago === "YAPE" ? "yapee" : "pague por Plin"} ${soles(r.montoAdelanto)} al ${a.metodoPago === "YAPE"
                ? `${complejo.pagos.yapeNumero} (${complejo.pagos.yapeTitular})` : complejo.pagos.plinNumero} y te mande aquí la FOTO del comprobante.`,
            enlaceMiReserva: `${cfg.frontUrl}/mi-reserva?codigo=${r.codigoReserva}`,
          };
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "consultar_reserva",
      descripcion: "Estado de una reserva con su código ALC-XXXXXX y el DNI con que se hizo.",
      esquema: { codigo, dni },
      async ejecutar(args) {
        try {
          const r = await api.consultarReserva(String(args.codigo), String(args.dni));
          await recordarReserva(r.codigoReserva, String(args.dni));
          return resumenReserva(r, cfg.frontUrl);
        } catch (e) {
          if (e instanceof ErrorApi && e.status === 404) return { error: "No hay una reserva con ese código y ese DNI." };
          return error(e);
        }
      },
    },
    {
      nombre: "link_pago_tarjeta",
      descripcion: "Enlace de pago con tarjeta o Yape en línea (Alcanfores Pay) para una reserva PENDIENTE. tipoPago: ADELANTO "
        + "(por defecto) o TOTAL.",
      esquema: { codigo, dni, tipoPago: z.enum(["ADELANTO", "TOTAL"]).optional() },
      async ejecutar(args) {
        const c = estado.conversacion;
        const cod = String(args.codigo);
        const comprobante = c.codigoReserva === cod ? c.contexto.comprobante : undefined;
        try {
          const r = await api.crearIntentoTarjeta(cod, String(args.dni), (args.tipoPago as "ADELANTO" | "TOTAL") ?? "ADELANTO", comprobante);
          return { enlace: `${cfg.frontUrl}${r.urlCheckout}`, venceA: horaDe(r.expiraEn), nota: "El enlace vence en 15 minutos." };
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "registrar_comprobante",
      descripcion: "Registra la FOTO del comprobante de Yape o Plin que mandó el cliente (idImagen) para su reserva. "
        + "Devuelve estadoPago VALIDADO (reserva confirmada) o PENDIENTE (el equipo lo revisa).",
      esquema: {
        codigo, dni,
        metodo: z.enum(["YAPE", "PLIN"]),
        codigoOperacion: z.string().regex(/^\d{6,12}$/, "El número de operación tiene de 6 a 12 dígitos."),
        idImagen: z.number().int().positive(),
      },
      async ejecutar(args) {
        const a = args as { codigo: string; dni: string; metodo: "YAPE" | "PLIN"; codigoOperacion: string; idImagen: number };
        const img = await imagenDeLaConversacion(a.idImagen);
        if (!img) return { error: "No encuentro esa foto en esta conversación. Pide que la envíe de nuevo." };
        if (img.idPago) return { error: `Esa foto ya se registró (pago ${img.idPago}).` };
        const c = estado.conversacion;
        try {
          const pago = await api.subirComprobante({
            codigo: a.codigo, dni: a.dni, metodo: a.metodo, codigoOperacion: a.codigoOperacion, imagen: img.datos, mime: img.mime,
            comprobante: c.codigoReserva === a.codigo ? c.contexto.comprobante : undefined,
          });
          await repo.actualizarImagen(img.id, { idPago: pago.idPago });
          await recordarReserva(a.codigo, a.dni);
          const r = await intentarValidacion({ ...img, idPago: pago.idPago }, pago.idPago, a.codigo, a.dni, a.metodo, a.codigoOperacion);
          return {
            idPago: pago.idPago, estadoPago: r.estadoPago,
            ...(r.estadoPago === "VALIDADO"
              ? { mensaje: "Pago validado: la reserva quedó CONFIRMADA; le llegan la constancia y su boleta o factura por correo y WhatsApp." }
              : { mensaje: "Comprobante recibido: el equipo lo revisa en minutos. No le expliques las reglas internas." }),
          };
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "validar_pago_ia",
      descripcion: "Reintenta la validación automática de un pago ya registrado desde esta conversación (por ejemplo, si el "
        + "sistema de pagos no respondía). Usa la lectura guardada de la foto; no recibe datos tuyos.",
      esquema: { idPago: z.number().int().positive() },
      async ejecutar(args) {
        const img = await repo.imagenPorPago(Number(args.idPago));
        if (!img || img.idConversacion !== estado.conversacion.id) return { error: "Ese pago no se registró en esta conversación." };
        const previo = img.validacion ? JSON.parse(img.validacion) as { estadoPago: string; metodo: "YAPE" | "PLIN"; codigoOperacion: string; codigoReserva: string; dni: string } : null;
        if (!previo) return { error: "Ese pago aún no tiene datos para validar." };
        if (previo.estadoPago === "VALIDADO") return { idPago: img.idPago, estadoPago: "VALIDADO" };
        try {
          const r = await intentarValidacion(img, img.idPago!, previo.codigoReserva, previo.dni, previo.metodo, previo.codigoOperacion);
          return { idPago: img.idPago, estadoPago: r.estadoPago };
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "cancelar_reserva",
      descripcion: "Cancela una reserva (código + DNI + motivo de 10 a 255 caracteres). Antes explica la política de "
        + "devolución y pide confirmación explícita; clienteConfirmo = true solo si el cliente dijo que sí.",
      esquema: {
        codigo, dni, motivo: z.string().min(10, "Cuéntanos el motivo (mínimo 10 caracteres).").max(255),
        clienteConfirmo: z.boolean().describe("true solo si el cliente confirmó la cancelación"),
      },
      async ejecutar(args) {
        if (!args.clienteConfirmo) return { error: "Primero explica la política de devolución y pide que confirme la cancelación." };
        try {
          const r = await api.cancelarReserva(String(args.codigo), String(args.dni), String(args.motivo));
          return resumenReserva(r, cfg.frontUrl);
        } catch (e) {
          return error(e);
        }
      },
    },
    {
      nombre: "reprogramar_reserva",
      descripcion: "Cambia el día, la hora o la cancha de una reserva vigente a otros turnos (1 a 8, misma cancha y mismo día; "
        + "fecha = día de los turnos nuevos). Por su cuenta (aquí o en la web) el cliente lo hace UNA sola vez y hasta 2 horas "
        + "antes del partido. "
        + "Primero llámala con clienteConfirmo = false: revisa las reglas y calcula el cambio (antes y después, nuevo total, "
        + "lo ya pagado y el saldo) sin hacer nada. Si devuelve error, explícalo y NO ofrezcas confirmar. Si devuelve la "
        + "vista previa, muéstrala y pregunta «¿Confirmo?». Solo si dice que sí, llámala otra vez con clienteConfirmo = true.",
      esquema: {
        codigo, dni,
        fecha: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Fecha en formato YYYY-MM-DD."),
        idsTurno: z.array(z.number().int().positive()).min(1, "Elige entre 1 y 8 turnos.").max(8, "Elige entre 1 y 8 turnos."),
        clienteConfirmo: z.boolean(),
      },
      async ejecutar(args) {
        const a = args as { codigo: string; dni: string; fecha: string; idsTurno: number[]; clienteConfirmo: boolean };
        const ids = [...new Set(a.idsTurno)];
        let actual: ReservaDetalle;
        try {
          actual = await api.consultarReserva(a.codigo, a.dni);
        } catch (e) {
          if (e instanceof ErrorApi && e.status === 404) return { error: "No hay una reserva con ese código y ese DNI." };
          return error(e);
        }
        // Reintento de un cambio que ya quedó guardado (la respuesta no llegó): se dice que está hecho, antes de las reglas
        // que lo leerían como «ya cambiaste el horario una vez».
        if (cambioYaAplicado(actual, ids)) return cambioHecho(actual, a.dni, true);
        // Las mismas reglas de reservas-service, para no ofrecer un cambio que el back va a rechazar.
        const bloqueo = motivoSinCambio(actual, ahora());
        if (bloqueo) {
          return sinCambio(bloqueo, "No ofrezcas el cambio: explícale el motivo y ofrece el WhatsApp del equipo.");
        }
        if (mismoHorario((actual.turnos ?? []).map((t) => t.idTurno), ids)) {
          return sinCambio({ codigo: MISMO_HORARIO, mensaje: MENSAJES.mismoHorario }, "Pregunta qué otro día, hora o cancha prefiere.");
        }
        let disponibilidad;
        try {
          disponibilidad = await api.disponibilidad(a.fecha);
        } catch (e) {
          return error(e);
        }
        const propios = new Map((actual.turnos ?? []).map((t) => [t.idTurno, t]));
        const elegidos = ids.map((id) => {
          for (const c of disponibilidad.canchas) {
            const t = c.turnos.find((x) => x.idTurno === id);
            if (t && (t.estado === "DISPONIBLE" || propios.has(id))) return { cancha: c.nombre, idCancha: c.idCancha, turno: t };
          }
          return null;
        });
        if (elegidos.some((x) => !x)) return { error: "Algún turno elegido ya no está libre ese día. Vuelve a consultar la disponibilidad." };
        if (new Set(elegidos.map((x) => x!.idCancha)).size > 1) return { error: "Los turnos deben ser de la misma cancha y del mismo día." };

        // Dinero en céntimos con el redondeo del back. Comprometido = lo pagado + el comprobante en revisión.
        const complejo = await ajustes.complejo();
        const total = elegidos.reduce((suma, x) => suma + centimos(x!.turno.precio), 0);
        const pagado = centimos(actual.montoPagado);
        const enRevision = centimos(actual.montoEnRevision);
        const montos = revisarMontos({ total, pagado, enRevision, adelantoPct: complejo.reglas.adelantoPct });
        if (montos) {
          return sinCambio(montos, "Ofrece otro horario (consulta ver_disponibilidad) o el WhatsApp del equipo. No prometas "
            + "devoluciones ni que el equipo revise la diferencia.");
        }
        // Sin pagos sigue pendiente con el adelanto del nuevo horario. El plazo para pagar se conserva, salvo que el nuevo
        // horario empiece antes: entonces vence al empezar (ReprogramadorReservas, SPEC K).
        const sinPagos = pagado + enRevision === 0;
        const primeraHora = elegidos.map((x) => x!.turno.horaInicio).sort()[0];
        const plazo = sinPagos ? plazoTrasCambio(actual.venceEn, instanteDesdeLima(`${a.fecha}T${primeraHora}`)) : null;
        const pagarAntesDe = plazo ? horaConDia(plazo) : null;
        const cambio = {
          antes: { cancha: actual.cancha?.nombre, fecha: textoFecha(actual.fecha), horario: actual.horarioTexto, total: soles(actual.montoTotal) },
          despues: {
            cancha: elegidos[0]!.cancha, fecha: textoFecha(a.fecha),
            horario: elegidos.map((x) => rango(x!.turno)).join(" y "), total: soles(total / 100),
          },
          yaPagado: soles(pagado / 100),
          ...(enRevision > 0 ? { comprobanteEnRevision: soles(enRevision / 100) } : {}),
          saldo: soles(Math.max(total - pagado, 0) / 100),
          ...(sinPagos ? { adelantoNuevo: soles(adelantoEnCentimos(total, complejo.reglas.adelantoPct) / 100) } : {}),
          ...(pagarAntesDe ? { pagarAntesDe } : {}),
        };
        if (!a.clienteConfirmo) {
          return {
            vistaPrevia: true, ...cambio,
            aviso: "Es su único cambio de horario por su cuenta (aquí o en la web): después solo el equipo puede cambiarlo.",
            siguientePaso: "Muestra el cambio, recuérdale que es su único cambio y pregunta «¿Confirmo?».",
          };
        }
        try {
          const r = await api.reprogramarReserva(a.codigo, a.dni, ids);
          return await cambioHecho(r, a.dni, false);
        } catch (e) {
          if (e instanceof ErrorApi && e.status === 404) return { error: CAMBIO_NO_DISPONIBLE };
          // Reglas del back (409 turno tomado, 422 de negocio): su mensaje tal cual.
          if (e instanceof ErrorApi && e.status === 409) {
            return sinCambio({ codigo: e.codigo, mensaje: e.message }, "Consulta otra vez ver_disponibilidad y ofrece otro horario.");
          }
          if (e instanceof ErrorApi && e.status === 422) {
            return sinCambio({ codigo: e.codigo, mensaje: e.message }, "Explícale el motivo y ofrece otro horario o el WhatsApp del equipo.");
          }
          // Sin respuesta (503 por tiempo agotado o sin conexión, u otro 5xx): reservas pudo guardar el cambio igual.
          if (e instanceof ErrorApi && e.status >= 500) return revisarCambioTrasFalla(a.codigo, a.dni, ids, e);
          return error(e);
        }
      },
    },
  ];

  // Solo por WhatsApp: el celular sale de la conversación (51XXXXXXXXX), nunca del modelo.
  const celular = estado.conversacion.canal === "WHATSAPP" ? celularDeWhatsapp(estado.conversacion.clave) : null;
  if (celular) {
    herramientas.push({
      nombre: "mis_reservas",
      descripcion: "Reservas del cliente por su DNI y el número de WhatsApp desde el que escribe (próximas o de los últimos 30 "
        + "días), sin pedirle el código. Si hay varias, pregunta cuál.",
      esquema: { dni },
      async ejecutar(args) {
        try {
          const lista = await api.misReservas(String(args.dni), celular);
          if (lista.length === 1) await recordarReserva(lista[0].codigo, String(args.dni));
          return {
            reservas: lista.map((r) => ({
              codigo: r.codigo, cancha: r.cancha, fecha: textoFecha(r.fecha), horario: rango(r),
              turnos: Array.isArray(r.turnos) ? r.turnos.length : r.turnos, estado: r.estado, estadoPago: r.estadoPago,
              total: soles(r.montoTotal), pagado: soles(r.pagado), saldo: soles(r.saldo),
            })),
            ...(lista.length === 0
              ? { nota: "No hay reservas de ese DNI registradas con este número de WhatsApp: pide el código de la reserva." }
              : {}),
          };
        } catch (e) {
          if (e instanceof ErrorApi && e.status === 404) return { error: NO_DISPONIBLE };
          return error(e);
        }
      },
    });
  }

  // Cada herramienta valida sus argumentos (el SDK ya lo hace; el cerebro simulado de las pruebas no).
  return herramientas.map((h) => ({
    ...h,
    async ejecutar(args: Record<string, unknown>) {
      const validado = z.object(h.esquema).safeParse(args ?? {});
      if (!validado.success) {
        return { error: validado.error.issues.map((i) => `${i.path.join(".") || "datos"}: ${i.message}`).join("; ") };
      }
      const datos = validado.data as Record<string, unknown>;
      if (typeof datos.codigo === "string") datos.codigo = datos.codigo.trim().toUpperCase();
      try {
        return await h.ejecutar(datos);
      } catch (e) {
        return error(e);
      }
    },
  }));
}

/** Para el SSE de la web: qué está haciendo Canchita mientras usa cada herramienta. */
export const TEXTO_HERRAMIENTA: Record<string, string> = {
  info_complejo: "Revisando los datos del complejo",
  listar_canchas: "Revisando las canchas",
  ver_disponibilidad: "Buscando turnos libres",
  consultar_dni: "Consultando el DNI en RENIEC",
  crear_reserva: "Registrando tu reserva",
  consultar_reserva: "Buscando tu reserva",
  link_pago_tarjeta: "Generando el enlace de pago",
  registrar_comprobante: "Registrando tu comprobante",
  validar_pago_ia: "Validando tu pago",
  cancelar_reserva: "Cancelando tu reserva",
  reprogramar_reserva: "Cambiando tu reserva",
  mis_reservas: "Buscando tus reservas",
};
