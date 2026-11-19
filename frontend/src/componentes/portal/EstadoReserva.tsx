import type { ReactNode } from 'react';
import {
  BadgeCheck,
  Bell,
  CalendarClock,
  CircleCheck,
  CircleX,
  Flag,
  Receipt,
  ShieldCheck,
  Ticket,
  TriangleAlert,
  Upload,
  UserCheck,
  UserX,
  type LucideIcon,
} from 'lucide-react';
import type { EventoLinea, ReservaCreada, ReservaDetalle } from '@/api/tipos';
import type { SeleccionTurnos } from '@/contexto/ReservaContext';
import { Ilustracion, PatronCancha } from '@/componentes/ui/Marca';
import { InsigniaEstado } from '@/componentes/ui/Estados';
import type { Hito } from '@/componentes/ui/Reserva';
import { ahoraLima, deLima, fmtFecha, fmtRango12, fmtRango24, textoHorario } from '@/utilidades/fecha';
import { cx } from '@/utilidades/cx';
import { sinCortes } from '@/utilidades/formato';
import type { IdIlustracion } from '@/utilidades/recursos';

export type ClaveCliente =
  | 'FALTA_PAGO'
  | 'EN_REVISION'
  | 'OBSERVADO'
  | 'CONFIRMADA'
  | 'EN_JUEGO'
  | 'JUGADA'
  | 'NO_ASISTIO'
  | 'FINALIZADA'
  | 'CANCELADA'
  | 'NO_CONFIRMADA';

/** Estado combinado que ve el cliente (reserva + pago + asistencia + hora). */
export function estadoCliente(r: Pick<ReservaDetalle, 'estado' | 'estadoPago' | 'fecha' | 'horaInicio' | 'horaFin'> & Partial<Pick<ReservaDetalle, 'asistencia' | 'cancelacion'>>): ClaveCliente {
  if (r.estado === 'CANCELADA') return r.cancelacion?.canceladoPor === 'SISTEMA' ? 'NO_CONFIRMADA' : 'CANCELADA';
  if (r.estado === 'PENDIENTE') return r.estadoPago === 'EN_REVISION' ? 'EN_REVISION' : r.estadoPago === 'OBSERVADO' ? 'OBSERVADO' : 'FALTA_PAGO';
  if (r.asistencia === 'ASISTIO') return 'JUGADA';
  if (r.asistencia === 'NO_ASISTIO') return 'NO_ASISTIO';
  const ahora = ahoraLima();
  const ini = deLima(`${r.fecha}T${r.horaInicio}:00`);
  const fin = deLima(`${r.fecha}T${r.horaFin === '00:00' ? '23:59' : r.horaFin}:00`);
  if (ahora.isAfter(fin)) return 'FINALIZADA';
  if (ahora.isAfter(ini)) return 'EN_JUEGO';
  return 'CONFIRMADA';
}

/**
 * `etiqueta`: texto de la insignia cuando el de siempre repetiría el título (string) o `null` para no mostrarla.
 * Sin `etiqueta` se usa la insignia normal del dominio «cliente».
 */
const VISUAL: Record<ClaveCliente, { il: IdIlustracion; titulo: string; sub: string; fondo: string; etiqueta?: string | null }> = {
  FALTA_PAGO: { il: 'IL-04-paso3-comprobante', titulo: 'Falta tu pago', sub: 'Completa el pago para confirmar tu horario.', fondo: 'bg-amarillo-tinte border-[#F1DC8E]', etiqueta: 'Sin pago' },
  EN_REVISION: { il: 'IL-11-en-revision', titulo: 'Tu horario está separado', sub: 'Se confirma cuando el complejo valida tu adelanto.', fondo: 'bg-amarillo-tinte border-[#F1DC8E]' },
  OBSERVADO: { il: 'IL-13-rechazado', titulo: 'Revisa tu comprobante', sub: 'Envíalo de nuevo para no perder tu horario.', fondo: 'bg-rojo-tinte border-[#F0B7B7]' },
  CONFIRMADA: { il: 'IL-12-confirmada', titulo: '¡Reserva confirmada!', sub: 'Te esperamos. Llega 10 min antes.', fondo: 'bg-menta border-[#CFE5D7]' },
  EN_JUEGO: { il: 'IL-15-partido-jugado', titulo: 'En juego', sub: '¡Buen partido!', fondo: 'bg-menta border-[#CFE5D7]', etiqueta: null },
  JUGADA: { il: 'IL-15-partido-jugado', titulo: 'Partido jugado', sub: 'Gracias por venir. Cuéntanos cómo te fue.', fondo: 'bg-menta border-[#CFE5D7]' },
  NO_ASISTIO: { il: 'IL-17-no-encontrada', titulo: 'No asististe', sub: 'El turno quedó registrado como no asistido.', fondo: 'bg-superficie-suave border-borde', etiqueta: null },
  FINALIZADA: { il: 'IL-15-partido-jugado', titulo: 'Reserva finalizada', sub: 'El horario ya pasó.', fondo: 'bg-superficie-suave border-borde' },
  CANCELADA: { il: 'IL-14-cancelada', titulo: 'Reserva cancelada', sub: 'El horario quedó libre.', fondo: 'bg-superficie-suave border-borde' },
  NO_CONFIRMADA: { il: 'IL-14-cancelada', titulo: 'No se confirmó', sub: 'Venció el plazo para pagar y el horario se liberó.', fondo: 'bg-superficie-suave border-borde', etiqueta: null },
};

/** Tarjeta grande de estado (ilustración + insignia + título + 1 línea), como en el prototipo cli-13 / cli-21. */
export function HeroEstado({ clave, titulo, sub, extra }: { clave: ClaveCliente; titulo?: string; sub?: ReactNode; extra?: ReactNode }) {
  const v = VISUAL[clave];
  return (
    <section className={cx('relative overflow-hidden rounded-24 border p-5 md:p-8 grid gap-4 md:grid-cols-[200px_1fr] md:items-center md:gap-8', v.fondo)} aria-live="polite">
      <PatronCancha className="absolute -right-10 -top-6 w-[360px] text-noche opacity-[.06] pointer-events-none" />
      <Ilustracion id={v.il} carga="eager" className="relative w-[150px] h-[150px] md:w-[200px] md:h-[200px] mx-auto md:mx-0" />
      <div className="relative grid gap-3 justify-items-center text-center md:justify-items-start md:text-left">
        {v.etiqueta !== null && <InsigniaEstado dominio="cliente" estado={clave} tamano="lg" texto={v.etiqueta} />}
        <h1 className="t-h1">{titulo ?? v.titulo}</h1>
        <p className="t-sec inline-flex items-center gap-1.5 !text-[14.5px]">
          <ShieldCheck className="ico ico-16" aria-hidden />
          {sub ?? v.sub}
        </p>
        {extra}
      </div>
    </section>
  );
}

/** Convierte una reserva del back al formato del resumen lateral. */
/**
 * v2: texto del horario de una reserva (varios turnos: «7:00–9:00 p. m.»). En 12 h cada tramo va sin cortes
 * (espacio duro y unión invisible junto al guion): solo se puede partir en « y » entre tramos.
 */
export function horarioDeReserva(r: { horaInicio: string; horaFin: string; horarioTexto?: string; turnos?: Array<{ horaInicio: string; horaFin: string }> }, h24 = false): string {
  if (h24) return r.turnos && r.turnos.length > 1 ? textoHorario(r.turnos, true) : fmtRango24(r.horaInicio, r.horaFin);
  const texto = r.horarioTexto || (r.turnos && r.turnos.length > 1 ? textoHorario(r.turnos, false) : fmtRango12(r.horaInicio, r.horaFin));
  return texto
    .split(' y ')
    .map((tramo) => tramo.replace(/ /g, '\u00a0').replace(/–/g, '\u2060–\u2060'))
    .join(' y ');
}

export function turnoDeReserva(r: ReservaCreada): SeleccionTurnos {
  const partes = (r.turnos ?? []).map((t) => ({ idTurno: t.idTurno, idCancha: r.cancha.id, nombreCancha: r.cancha.nombre, deporte: r.cancha.deporte, fecha: r.fecha, horaInicio: t.horaInicio, horaFin: t.horaFin, precio: t.precio, franja: 'DIA' as const }));
  return {
    idsTurno: partes.map((t) => t.idTurno),
    partes,
    horarioTexto: horarioDeReserva(r),
    idTurno: 0,
    idCancha: r.cancha.id,
    nombreCancha: r.cancha.nombre,
    deporte: r.cancha.deporte,
    fecha: r.fecha,
    horaInicio: r.horaInicio,
    horaFin: r.horaFin,
    precio: r.montoTotal,
    franja: 'DIA',
  };
}

const EVENTOS: Record<string, { titulo: string; icono: LucideIcon; estado?: Hito['estado'] }> = {
  REGISTRADA: { titulo: 'Registrada', icono: Ticket },
  RESERVA_REGISTRADA: { titulo: 'Registrada', icono: Ticket },
  COMPROBANTE_RECIBIDO: { titulo: 'Comprobante enviado', icono: Upload },
  PAGO_REGISTRADO: { titulo: 'Comprobante enviado', icono: Upload },
  COMPROBANTE_ENVIADO: { titulo: 'Comprobante enviado', icono: Upload },
  OBSERVADO: { titulo: 'Comprobante observado', icono: TriangleAlert, estado: 'error' },
  PAGO_VALIDADO: { titulo: 'Pago validado', icono: BadgeCheck },
  PAGO_RECHAZADO: { titulo: 'Comprobante observado', icono: TriangleAlert, estado: 'error' },
  CONFIRMADA: { titulo: 'Confirmada', icono: CircleCheck },
  RESERVA_CONFIRMADA: { titulo: 'Confirmada', icono: CircleCheck },
  CANCELADA: { titulo: 'Cancelada', icono: CircleX, estado: 'cancelado' },
  RESERVA_CANCELADA: { titulo: 'Cancelada', icono: CircleX, estado: 'cancelado' },
  ASISTIO: { titulo: 'Asistió', icono: UserCheck },
  NO_ASISTIO: { titulo: 'No asistió', icono: UserX, estado: 'error' },
  RECORDATORIO: { titulo: 'Recordatorio enviado', icono: Bell },
  COMPROBANTE_EMITIDO: { titulo: 'Comprobante emitido', icono: Receipt },
  REPROGRAMADA: { titulo: 'Horario cambiado', icono: CalendarClock },
};

function titulo(evento: string): string {
  const t = evento.replace(/_/g, ' ').toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Línea de tiempo de la reserva: eventos reales del back + hitos pendientes (confirmación y partido). */
export function hitosDeReserva(r: ReservaDetalle, eventos: EventoLinea[] = r.linea): Hito[] {
  const hitos: Hito[] = eventos.map((e) => {
    const clave = e.evento.toUpperCase();
    // El back guarda un solo evento ASISTENCIA; el detalle y `asistencia` dicen si vino o no.
    const def =
      clave === 'ASISTENCIA'
        ? r.asistencia === 'NO_ASISTIO' || /no asisti/i.test(e.detalle ?? '')
          ? EVENTOS.NO_ASISTIO
          : EVENTOS.ASISTIO
        : EVENTOS[clave];
    return { titulo: def?.titulo ?? titulo(e.evento), icono: def?.icono, fecha: e.fecha, detalle: e.detalle ? sinCortes(e.detalle) : e.detalle, estado: def?.estado ?? 'hecho' };
  });
  if (r.estado === 'PENDIENTE') {
    const ultimo = hitos[hitos.length - 1];
    if (ultimo && ultimo.estado === 'hecho' && r.estadoPago === 'EN_REVISION') ultimo.estado = 'actual';
    hitos.push({ titulo: 'Confirmada', estado: 'pendiente', icono: CircleCheck });
  }
  if (r.estado !== 'CANCELADA' && r.asistencia === 'PENDIENTE') {
    // Varios turnos = tramos separados (nunca un rango falso de inicio a fin); horarioDeReserva ya los deja sin cortes.
    hitos.push({ titulo: 'Partido', estado: 'pendiente', icono: Flag, detalle: `${fmtFecha(r.fecha)} · ${horarioDeReserva(r)}` });
  }
  return hitos;
}

