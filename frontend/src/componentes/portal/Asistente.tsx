import { useState, type ReactNode } from 'react';
import {
  Banknote,
  CalendarClock,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Globe,
  History,
  Lock,
  Mail,
  Moon,
  Smartphone,
  Store,
  Sun,
  UserRound,
  Wallet,
  Wrench,
  HandCoins,
  ShieldCheck,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import type { Turno } from '@/api/tipos';
import type { SeleccionTurnos } from '@/contexto/ReservaContext';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { cx } from '@/utilidades/cx';
import { fmtFecha, fmtHora12 } from '@/utilidades/fecha';
import { fmtCelular, fmtSoles, nombreConDeporte, plural, desgloseIgv } from '@/utilidades/formato';

/** Adelanto = total × pct / 100 con HALF_UP a 2 decimales (igual que el back). */
export function calcularAdelanto(total: number, pct: number): number {
  return Math.round(total * pct) / 100;
}

// ------------------------------ Stepper ------------------------------
const PASOS: Array<{ texto: string; icono: LucideIcon }> = [
  { texto: 'Horario', icono: Clock },
  { texto: 'Datos', icono: UserRound },
  { texto: 'Pago', icono: Wallet },
];

/** Barra de progreso de 3 pasos: número + ícono + 1 palabra. Los pasos hechos se pueden tocar para volver. */
export function Stepper({ actual, completados = [], alIr }: { actual: 1 | 2 | 3; completados?: number[]; alIr?: (p: 1 | 2 | 3) => void }) {
  return (
    <nav aria-label={`Paso ${actual} de 3: ${PASOS[actual - 1]?.texto ?? ''}`}>
      <ol className="stepper" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {PASOS.map((p, i) => {
          const n = (i + 1) as 1 | 2 | 3;
          const hecho = n < actual || completados.includes(n);
          const esActual = n === actual;
          const Icono = p.icono;
          const contenido = (
            <>
              <span className="stepper-num">{hecho && !esActual ? <Check className="ico" aria-hidden /> : n}</span>
              <Icono className={cx('ico ico-16', esActual ? 'text-noche' : hecho ? 'text-profundo' : 'text-gris')} aria-hidden />
              <span className="stepper-texto">{p.texto}</span>
            </>
          );
          return (
            <li key={p.texto} className="contents">
              {i > 0 && <span className={cx('stepper-linea', (n <= actual || completados.includes(n)) && 'hecha')} aria-hidden />}
              <span className={cx('stepper-paso', esActual ? 'actual' : hecho ? 'hecho' : 'pendiente')} aria-current={esActual ? 'step' : undefined}>
                {hecho && !esActual && alIr ? (
                  <button type="button" className="flex items-center gap-2 bg-transparent border-0 p-0 cursor-pointer" onClick={() => alIr(n)} aria-label={`Volver al paso ${n}: ${p.texto}`}>
                    {contenido}
                  </button>
                ) : (
                  contenido
                )}
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

// ------------------------------ Turnos ------------------------------
function Rayas() {
  return (
    <svg className="rayas" aria-hidden preserveAspectRatio="none">
      <defs>
        <pattern id="ray" width="10" height="10" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="5" height="10" fill="currentColor" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#ray)" />
    </svg>
  );
}

const ETQ: Record<Turno['estado'], string> = { DISPONIBLE: 'disponible', OCUPADO: 'ocupado, no disponible', BLOQUEADO: 'en mantenimiento, no disponible', PASADO: 'ya pasó, no disponible' };

/** Chip de turno del portal (12 h, precio). Ocupado / mantenimiento / pasado no se pueden elegir. v2: se marcan varios. */
export function ChipTurno({ turno, seleccionado, alElegir }: { turno: Turno; seleccionado: boolean; alElegir: (t: Turno) => void }) {
  const clase = turno.estado === 'OCUPADO' ? 'ct-ocupado' : turno.estado === 'BLOQUEADO' ? 'ct-mantenimiento' : turno.estado === 'PASADO' ? 'ct-pasado' : seleccionado ? 'ct-seleccionado' : 'ct-disponible';
  const noSel = turno.estado !== 'DISPONIBLE';
  const sub = turno.estado === 'OCUPADO' ? 'Ocupado' : turno.estado === 'BLOQUEADO' ? 'No disponible' : turno.estado === 'PASADO' ? 'Pasado' : `${fmtSoles(turno.precio, { decimales: 0 })}${turno.feriado ? ' · feriado' : ''}`;
  const Marca = seleccionado && !noSel ? Check : turno.estado === 'OCUPADO' ? Lock : turno.estado === 'BLOQUEADO' ? Wrench : null;
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={seleccionado}
      aria-disabled={noSel || undefined}
      aria-label={`${fmtHora12(turno.horaInicio)}, ${fmtSoles(turno.precio)}, ${seleccionado ? 'seleccionado' : ETQ[turno.estado]}`}
      className={cx('chip-turno', clase)}
      onClick={() => !noSel && alElegir(turno)}
    >
      {turno.estado === 'BLOQUEADO' && <Rayas />}
      <span className="ct-hora">{fmtHora12(turno.horaInicio)}</span>
      <span className="ct-sub">{sub}</span>
      {Marca && (
        <span className="ct-marca" aria-hidden>
          <Marca className="ico" />
        </span>
      )}
    </button>
  );
}

/** Turnos en dos franjas (Día · Noche). v2: se pueden marcar varios (`seleccionados`). */
export function GrillaTurnos({ turnos, seleccionados, alElegir }: { turnos: Turno[]; seleccionados: number[]; alElegir: (t: Turno) => void }) {
  const franjas: Array<{ clave: Turno['franja']; texto: string; icono: LucideIcon }> = [
    { clave: 'DIA', texto: 'Día', icono: Sun },
    { clave: 'NOCHE', texto: 'Noche', icono: Moon },
  ];
  return (
    <div className="grid gap-5" role="group" aria-label="Horarios (puedes marcar varios)">
      {franjas.map(({ clave, texto, icono: Icono }) => {
        const lista = turnos.filter((t) => t.franja === clave);
        if (!lista.length) return null;
        return (
          <section key={clave} className="grid gap-2.5" aria-label={`Turnos de ${texto.toLowerCase()}`}>
            <h3 className="inline-flex items-center gap-2 text-[14px] font-semibold">
              <Icono className={cx('ico ico-16', clave === 'DIA' ? 'text-amarillo-ocre' : 'text-noche')} aria-hidden />
              {texto}
            </h3>
            <div className="grilla-turnos">
              {lista.map((t) => (
                <ChipTurno key={t.idTurno} turno={t} seleccionado={seleccionados.includes(t.idTurno)} alElegir={alElegir} />
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

/** Leyenda de estados de los turnos. */
export function LeyendaTurnos({ extra }: { extra?: ReactNode }) {
  const mue = (cls: string, Icono?: LucideIcon, icCls?: string) => (
    <span className={cx('leyenda-mue !w-[22px] !h-[18px] !rounded-[5px] grid place-items-center', cls)} aria-hidden>
      {Icono && <Icono className={cx('!w-[11px] !h-[11px] !stroke-[2.75]', icCls)} />}
    </span>
  );
  return (
    <div className="leyenda items-center">
      <span className="leyenda-item">{mue('')}Disponible</span>
      <span className="leyenda-item">{mue('sel', Check, 'text-white')}Seleccionado</span>
      <span className="leyenda-item">{mue('ocu', Lock, 'text-gris')}Ocupado</span>
      <span className="leyenda-item">{mue('man', Wrench, 'text-profundo')}No disponible</span>
      <span className="leyenda-item">{mue('pas', History, 'text-gris')}Pasado</span>
      {extra}
    </div>
  );
}

// ------------------------------ Resumen ------------------------------
export interface DatosResumen {
  turno: SeleccionTurnos | null;
  adelantoPct: number;
  montoAdelanto?: number;
  cliente?: { nombres: string; apellidos: string; telefono: string; correo: string } | null;
  codigo?: string | null;
}

function Fila({ icono, dato, etiqueta }: { icono: ReactNode; dato: ReactNode; etiqueta: string }) {
  return (
    <div className="resumen-item !items-center">
      <span className="resumen-ico">{icono}</span>
      <b className="tnum">
        <span className="sr-only">{etiqueta}: </span>
        {dato}
      </b>
    </div>
  );
}
function Monto({ icono: Icono, etiqueta, valor, destacado }: { icono: LucideIcon; etiqueta: string; valor: string; destacado?: boolean }) {
  return (
    <div className={cx('fila-monto', destacado && 'destacada')}>
      <span className="inline-flex items-center gap-2">
        <Icono className="ico ico-16" aria-hidden />
        {etiqueta}
      </span>
      <b>{valor}</b>
    </div>
  );
}

/** Resumen lateral «ícono + dato» con montos (Total · Saldo al llegar · Adelanto). */
export function ResumenReserva({
  datos,
  nota,
  cta,
  accionCab,
  titulo = 'Tu reserva',
  className,
}: {
  datos: DatosResumen;
  nota?: ReactNode;
  cta?: ReactNode;
  accionCab?: ReactNode;
  titulo?: string;
  className?: string;
}) {
  const t = datos.turno;
  const cab = (
    <div className="resumen-cab">
      <span className="kicker">{titulo}</span>
      {datos.codigo ? <span className="tnum font-bold text-[13px]">{datos.codigo}</span> : accionCab}
    </div>
  );
  if (!t) {
    return (
      <aside className={cx('resumen', className)} aria-label="Resumen de la reserva">
        {cab}
        <div className="resumen-vacio">
          <Clock className="ico" aria-hidden />
          <span>Elige un horario</span>
        </div>
        {cta && <div className="resumen-pie">{cta}</div>}
      </aside>
    );
  }
  const total = t.precio;
  const adelanto = datos.montoAdelanto ?? calcularAdelanto(total, datos.adelantoPct);
  return (
    <aside className={cx('resumen', className)} aria-label="Resumen de la reserva">
      {cab}
      <div className="resumen-cuerpo">
        <Fila icono={<IconoDeporte deporte={t.deporte} />} dato={nombreConDeporte(t.nombreCancha, t.deporte)} etiqueta="Cancha" />
        <Fila icono={<CalendarDays className="ico" aria-hidden />} dato={fmtFecha(t.fecha)} etiqueta="Fecha" />
        <Fila icono={<Clock className="ico" aria-hidden />} dato={t.partes.length > 1 ? `${t.horarioTexto} · ${plural(t.partes.length, 'turno', 'turnos')}` : t.horarioTexto} etiqueta="Hora" />
      </div>
      {datos.cliente && (
        <div className="px-[18px] py-3 border-t border-borde grid gap-1 text-[13.5px] font-semibold">
          <span className="flex flex-wrap gap-x-4 gap-y-1">
            <span className="inline-flex items-center gap-1.5">
              <UserRound className="ico ico-16" aria-hidden />
              {`${datos.cliente.nombres.split(' ')[0]} ${datos.cliente.apellidos.split(' ')[0]}`}
            </span>
            <span className="inline-flex items-center gap-1.5 tnum">
              <Smartphone className="ico ico-16" aria-hidden />
              {fmtCelular(datos.cliente.telefono)}
            </span>
          </span>
          <span className="inline-flex items-center gap-1.5 truncar">
            <Mail className="ico ico-16" aria-hidden />
            <span className="truncar">{datos.cliente.correo}</span>
          </span>
        </div>
      )}
      <div className="resumen-montos">
        <Monto icono={Banknote} etiqueta="Total" valor={fmtSoles(total)} />
        {total > 0 && (
          <div className="fila-monto fila-igv">
            <span>Incluye IGV (18 %)</span>
            <span>{fmtSoles(desgloseIgv(total).igv)}</span>
          </div>
        )}
        <Monto icono={Store} etiqueta="Saldo al llegar" valor={fmtSoles(total - adelanto)} />
        <Monto icono={Smartphone} etiqueta={`Adelanto ${datos.adelantoPct} %`} valor={fmtSoles(adelanto)} destacado />
      </div>
      {nota && (
        <div className="resumen-nota">
          <CalendarClock className="ico" aria-hidden />
          <span>{nota}</span>
        </div>
      )}
      {cta && <div className="resumen-pie">{cta}</div>}
    </aside>
  );
}

/** Barra inferior fija del móvil: resumen corto + CTA, con el resumen completo plegable. */
export function BarraResumen({ datos, cta, ayuda, completo }: { datos: DatosResumen; cta: ReactNode; ayuda?: ReactNode; completo?: ReactNode }) {
  const [abierto, setAbierto] = useState(false);
  const t = datos.turno;
  const adelanto = t ? datos.montoAdelanto ?? calcularAdelanto(t.precio, datos.adelantoPct) : 0;
  return (
    <div className="barra-fija solo-movil">
      {abierto && completo && <div className="resumen-plegable">{completo}</div>}
      <div className="barra-resumen">
        <div className="barra-resumen-txt">
          {t ? (
            <>
              <b>{`${t.nombreCancha} · ${fmtFecha(t.fecha)} · ${t.horarioTexto}`}</b>
              <span>
                Adelanto <b>{fmtSoles(adelanto)}</b> · Total {fmtSoles(t.precio)}
                {t.partes.length > 1 ? ` · ${t.partes.length} turnos` : ''}
                {completo && (
                  <button type="button" className="barra-toggle ml-2" aria-expanded={abierto} onClick={() => setAbierto((a) => !a)}>
                    {abierto ? 'Ocultar' : 'Ver detalle'}
                    {abierto ? <ChevronDown className="ico ico-14" aria-hidden /> : <ChevronUp className="ico ico-14" aria-hidden />}
                  </button>
                )}
              </span>
            </>
          ) : (
            <b className="!flex items-center gap-2">
              <Clock className="ico ico-18 text-gris" aria-hidden />
              Elige un horario
            </b>
          )}
        </div>
        {cta}
      </div>
      {ayuda && <div className="barra-ayuda">{ayuda}</div>}
    </div>
  );
}

// ------------------------------ Condiciones ------------------------------
/** Condiciones en 3 íconos (adelanto · devolución · sin devolución). */
export function CondicionesIconos({ adelantoPct = 50, devolucionHoras = 24, verMas = true }: { adelantoPct?: number; devolucionHoras?: number; verMas?: boolean }) {
  return (
    <section className="condiciones">
      <div className="condiciones-cab">
        <ShieldCheck className="ico" aria-hidden />
        <span>Condiciones</span>
      </div>
      <ul className="condiciones-lista">
        <li>
          <Wallet className="ico" aria-hidden />
          <span>{`Adelanto ${adelantoPct} % por Yape, Plin o tarjeta`}</span>
        </li>
        <li>
          <HandCoins className="ico" aria-hidden />
          <span>{`Con ${devolucionHoras} h o más: te devolvemos lo pagado`}</span>
        </li>
        <li>
          <TriangleAlert className="ico ico-alerta" aria-hidden />
          <span>
            {`Menos de ${devolucionHoras} h o no vienes: sin devolución`}
            {verMas && (
              <>
                {' '}
                <a className="enlace" href="/terminos" target="_blank" rel="noopener">
                  Ver términos
                </a>
              </>
            )}
          </span>
        </li>
      </ul>
    </section>
  );
}

/** «Hora de Lima» con globo (cabeceras de pasos). */
export function HoraLima() {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Globe className="ico ico-14" aria-hidden />
      Hora de Lima
    </span>
  );
}
