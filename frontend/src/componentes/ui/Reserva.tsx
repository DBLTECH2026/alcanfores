import { useState, type ReactNode } from 'react';
import { Check, CircleX, Copy, CopyCheck, Hourglass, Circle, X, type LucideIcon } from 'lucide-react';
import { cx } from '@/utilidades/cx';
import { fmtFechaHora } from '@/utilidades/fecha';
import { PatronCancha } from './Marca';
import { useToast } from '@/contexto/ToastContext';

/** Ticket con el código de reserva grande + Copiar (prototipo `codigoReserva`). */
export function CodigoReserva({
  codigo,
  claro,
  pequeno,
  etiqueta = 'Tu código',
  ayuda = 'Guárdalo: lo usas con tu DNI.',
  acciones,
  fondo,
}: {
  codigo: string;
  claro?: boolean;
  pequeno?: boolean;
  etiqueta?: string;
  ayuda?: string;
  /** Botones extra junto a Copiar. */
  acciones?: ReactNode;
  /** Color del fondo donde va (para los «mordiscos» laterales). */
  fondo?: string;
}) {
  const [copiado, setCopiado] = useState(false);
  const toast = useToast();
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(codigo);
      setCopiado(true);
      toast.exito('Código copiado');
      window.setTimeout(() => setCopiado(false), 2500);
    } catch {
      toast.info(`Tu código: ${codigo}`);
    }
  };
  return (
    <div className={cx('ticket', claro && 'claro', pequeno && 'ticket-sm', !claro && 'sobre-oscuro')} style={fondo ? { ['--fondo-ticket' as string]: fondo } : undefined}>
      <PatronCancha />
      <span className="kicker">{etiqueta}</span>
      <div className="ticket-fila">
        <span className="ticket-codigo" aria-label={`Código ${codigo.split('').join(' ')}`}>
          {codigo}
        </span>
        <div className="grupo-botones">
          <button type="button" className={cx('btn btn-sm', claro ? 'btn-secundario' : 'btn-claro')} onClick={copiar}>
            {copiado ? <CopyCheck className="ico" aria-hidden /> : <Copy className="ico" aria-hidden />}
            <span>{copiado ? 'Copiado' : 'Copiar'}</span>
          </button>
          {acciones}
        </div>
      </div>
      {ayuda && <span className={claro ? 't-sec' : 'text-[13px] text-[#C9D6CF]'}>{ayuda}</span>}
    </div>
  );
}

export type EstadoHito = 'hecho' | 'actual' | 'pendiente' | 'error' | 'cancelado';
export interface Hito {
  titulo: string;
  detalle?: string | null;
  fecha?: string | null;
  estado: EstadoHito;
  icono?: LucideIcon;
}
const ICONO_HITO: Record<EstadoHito, LucideIcon> = { hecho: Check, actual: Hourglass, pendiente: Circle, error: X, cancelado: CircleX };

/** Línea de tiempo vertical u horizontal (historial de la reserva). */
export function LineaTiempo({ hitos, horizontal }: { hitos: Hito[]; horizontal?: boolean }) {
  return (
    <ol className={cx('timeline', horizontal && 'horizontal')}>
      {hitos.map((h, i) => {
        const Icono = h.icono ?? ICONO_HITO[h.estado];
        const fecha = h.fecha && /T\d\d:/.test(h.fecha) ? fmtFechaHora(h.fecha) : h.fecha;
        return (
          <li key={`${h.titulo}-${i}`} className={cx('tl-item', h.estado)} aria-current={h.estado === 'actual' ? 'step' : undefined}>
            <span className="tl-punto">
              <Icono className="ico" aria-hidden />
            </span>
            <div>
              <div className="tl-titulo">{h.titulo}</div>
              {h.detalle && <div className="tl-detalle">{h.detalle}</div>}
              {fecha && <div className="tl-fecha">{fecha}</div>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
