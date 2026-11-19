import type { ReactNode } from 'react';
import {
  AlarmClock,
  BadgeCheck,
  CalendarX,
  Circle,
  CircleCheck,
  CircleDashed,
  CirclePause,
  CircleX,
  ClockAlert,
  Clock,
  Eye,
  EyeOff,
  FileX,
  Flag,
  Globe,
  History,
  Hourglass,
  Lock,
  Phone,
  Store,
  Timer,
  TriangleAlert,
  Trophy,
  Upload,
  UserCheck,
  UserX,
  Wallet,
  Wrench,
  CircleAlert,
  Info,
  WifiOff,
  X,
  type LucideIcon,
} from 'lucide-react';
import { cx } from '@/utilidades/cx';

// ------------------------------ Insignia (badge) ------------------------------
export type TonoInsignia = 'amarillo' | 'verde' | 'gris' | 'menta' | 'rojo' | 'profundo' | 'rojo-borde' | 'punteado' | 'neutro' | 'noche' | 'grass';

/** Píldora de estado: SIEMPRE ícono + 1–3 palabras. */
export function Insignia({ tono = 'neutro', icono: Icono, children, tamano = 'md', className }: { tono?: TonoInsignia; icono?: LucideIcon; children: ReactNode; tamano?: 'sm' | 'md' | 'lg'; className?: string }) {
  return (
    <span className={cx('badge', `b-${tono}`, tamano !== 'md' && `badge-${tamano}`, className)}>
      {Icono && <Icono className="ico" aria-hidden />}
      <span>{children}</span>
    </span>
  );
}

type Def = [TonoInsignia, LucideIcon, string];
/** Estados por dominio (mismos tonos e íconos que el prototipo, ampliados con los del SPEC). */
export const ESTADOS: Record<string, Record<string, Def>> = {
  reserva: { PENDIENTE: ['amarillo', Clock, 'Pendiente'], CONFIRMADA: ['verde', CircleCheck, 'Confirmada'], CANCELADA: ['gris', CircleX, 'Cancelada'] },
  estadoPago: {
    SIN_PAGO: ['punteado', Wallet, 'Sin pago'],
    EN_REVISION: ['amarillo', Hourglass, 'En revisión'],
    OBSERVADO: ['rojo', TriangleAlert, 'Observado'],
    ADELANTO_PAGADO: ['menta', BadgeCheck, 'Adelanto pagado'],
    PAGADO_TOTAL: ['profundo', BadgeCheck, 'Pagado'],
    REEMBOLSADO: ['neutro', History, 'Devuelto'],
  },
  pago: {
    PENDIENTE: ['amarillo', Hourglass, 'En revisión'],
    VALIDADO: ['menta', BadgeCheck, 'Validado'],
    RECHAZADO: ['rojo', TriangleAlert, 'Rechazado'],
    ANULADO: ['gris', CircleX, 'Anulado'],
    REEMBOLSADO: ['neutro', History, 'Reembolsado'],
    SIN_COMPROBANTE: ['punteado', FileX, 'Sin comprobante'],
    SIN_REVISAR: ['gris', EyeOff, 'Sin revisar'],
  },
  turno: {
    DISPONIBLE: ['grass', Circle, 'Disponible'],
    OCUPADO: ['gris', Lock, 'Ocupado'],
    RESERVADO: ['gris', Lock, 'Ocupado'],
    BLOQUEADO: ['menta', Wrench, 'Mantenimiento'],
    PASADO: ['neutro', History, 'Pasado'],
  },
  asistencia: { PENDIENTE: ['punteado', CircleDashed, 'Por marcar'], ASISTIO: ['profundo', UserCheck, 'Asistió'], NO_ASISTIO: ['rojo-borde', UserX, 'No asistió'] },
  origen: { WEB: ['neutro', Globe, 'Web'], PRESENCIAL: ['neutro', Store, 'Presencial'], TELEFONICA: ['neutro', Phone, 'Teléfono'] },
  cancha: { ACTIVA: ['menta', CircleCheck, 'Activa'], INACTIVA: ['gris', CirclePause, 'Inactiva'] },
  /** Etiqueta combinada que ve el cliente en el portal (ver `estadoCliente`). */
  cliente: {
    FALTA_PAGO: ['amarillo', Upload, 'Falta tu pago'],
    EN_REVISION: ['amarillo', Hourglass, 'En revisión'],
    OBSERVADO: ['rojo', TriangleAlert, 'Comprobante observado'],
    CONFIRMADA: ['verde', CircleCheck, 'Confirmada'],
    EN_JUEGO: ['profundo', Timer, 'En juego'],
    JUGADA: ['menta', Trophy, 'Jugada'],
    NO_ASISTIO: ['rojo-borde', UserX, 'No asististe'],
    FINALIZADA: ['neutro', Flag, 'Finalizada'],
    CANCELADA: ['gris', CircleX, 'Cancelada'],
    NO_CONFIRMADA: ['gris', CalendarX, 'No se confirmó'],
  },
  resena: { PUBLICADA: ['menta', Eye, 'Publicada'], OCULTA: ['gris', EyeOff, 'Oculta'] },
};

/** Insignia de estado por dominio: <InsigniaEstado dominio="reserva" estado="CONFIRMADA" /> */
export function InsigniaEstado({ dominio, estado, tamano = 'md', texto, className }: { dominio: keyof typeof ESTADOS | string; estado: string; tamano?: 'sm' | 'md' | 'lg'; texto?: string; className?: string }) {
  const d = ESTADOS[dominio]?.[estado];
  if (!d) return <Insignia tamano={tamano} className={className}>{texto ?? estado}</Insignia>;
  const [tono, icono, et] = d;
  return (
    <Insignia tono={tono} icono={icono} tamano={tamano} className={className}>
      {texto ?? et}
    </Insignia>
  );
}

// ------------------------------ Etiqueta (tag) ------------------------------
/** Tag pequeño en MAYÚSCULAS: urgente · vencido · neutro · menta · rojo. */
export function Etiqueta({ tono = 'neutro', icono, children }: { tono?: 'urgente' | 'vencido' | 'neutro' | 'menta' | 'rojo'; icono?: LucideIcon | false; children: ReactNode }) {
  const Icono = icono === false ? null : icono ?? (tono === 'urgente' ? AlarmClock : tono === 'vencido' ? ClockAlert : null);
  return (
    <span className={cx('tag', `tag-${tono}`)}>
      {Icono && <Icono className="ico" aria-hidden />}
      {children}
    </span>
  );
}

// ------------------------------ Alerta ------------------------------
const ICONO_ALERTA = { info: Info, exito: CircleCheck, advertencia: TriangleAlert, error: CircleAlert, offline: WifiOff } as const;

/** Aviso en línea (info · exito · advertencia · error · offline). Los errores llevan role=alert. */
export function Alerta({
  tipo = 'info',
  titulo,
  children,
  acciones,
  alCerrar,
  icono,
  className,
}: {
  tipo?: keyof typeof ICONO_ALERTA;
  titulo?: ReactNode;
  children?: ReactNode;
  acciones?: ReactNode;
  alCerrar?: () => void;
  icono?: LucideIcon;
  className?: string;
}) {
  const Icono = icono ?? ICONO_ALERTA[tipo];
  return (
    <div className={cx('alerta', `alerta-${tipo}`, className)} role={tipo === 'error' ? 'alert' : 'status'}>
      <Icono className="ico" aria-hidden />
      <div className="alerta-cuerpo">
        {titulo && <span className="alerta-titulo">{titulo}</span>}
        {children && <span>{children}</span>}
        {acciones && <div className="alerta-acciones">{acciones}</div>}
      </div>
      {alCerrar && (
        <button type="button" className="alerta-cerrar" aria-label="Cerrar aviso" onClick={alCerrar}>
          <X className="ico ico-18" aria-hidden />
        </button>
      )}
    </div>
  );
}
