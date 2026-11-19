import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { DatosComprobante, Deporte, Franja, MetodoPagoWeb, ReservaCreada, TipoPago } from '@/api/tipos';
import type { DatosClienteEntrada } from '@/esquemas/reserva';
import { textoHorario } from '@/utilidades/fecha';

/**
 * Estado del asistente «Reservar» en 3 pasos (Horario · Datos · Pago).
 * Se guarda en sessionStorage (salvo la imagen del comprobante) para no perder nada al recargar.
 * Si el back responde 409 TURNO_OCUPADO se vuelve al paso 1 conservando datos y pago.
 */

export interface TurnoElegido {
  idTurno: number;
  idCancha: number;
  nombreCancha: string;
  deporte: Deporte;
  fecha: string;
  horaInicio: string;
  horaFin: string;
  precio: number;
  franja: Franja;
  feriado?: boolean;
}
/**
 * v2: selección de VARIOS turnos de la misma cancha y fecha (1 a 8). Se presenta como un turno «compuesto»:
 * hora del primero al último, precio = suma, `horarioTexto` une los seguidos («7:00–9:00 p. m.»).
 */
export interface SeleccionTurnos extends TurnoElegido {
  idsTurno: number[];
  partes: TurnoElegido[];
  horarioTexto: string;
}
export const MAX_TURNOS = 8;
function componer(turnos: TurnoElegido[]): SeleccionTurnos | null {
  const orden = [...turnos].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  const primero = orden[0];
  const ultimo = orden[orden.length - 1];
  if (!primero || !ultimo) return null;
  return {
    ...primero,
    horaFin: ultimo.horaFin,
    precio: Math.round(orden.reduce((a, t) => a + t.precio, 0) * 100) / 100,
    idsTurno: orden.map((t) => t.idTurno),
    partes: orden,
    horarioTexto: textoHorario(orden),
  };
}
export interface PagoElegido {
  metodoPago: MetodoPagoWeb | null;
  tipoPago: TipoPago;
  codigoOperacion: string;
}
export type Paso = 1 | 2 | 3;

interface Estado {
  paso: Paso;
  idCancha: number | null;
  fecha: string | null;
  /** v2: turnos marcados (misma cancha y fecha). */
  turnos: TurnoElegido[];
  cliente: DatosClienteEntrada | null;
  /** true si el DNI se verificó con RENIEC (los nombres vienen del servicio). */
  verificadoReniec: boolean;
  pago: PagoElegido;
  /** Reserva ya creada (PENDIENTE) mientras se completa el pago. */
  reserva: ReservaCreada | null;
  /** Errores por campo del back que deben pintarse en un paso. */
  erroresServidor: { paso: 2 | 3; errores: Record<string, string> } | null;
  /** Hora del turno que se perdió por 409 (para el aviso del paso 1). */
  turnoPerdido: SeleccionTurnos | null;
}

interface ApiReserva extends Estado {
  /** Selección compuesta (null si no hay turnos marcados). */
  turno: SeleccionTurnos | null;
  archivo: File | null;
  /** Marca o desmarca un turno (si es de otra cancha o fecha, empieza una selección nueva). */
  alternarTurno: (t: TurnoElegido) => void;
  irAPaso: (p: Paso) => void;
  elegirCancha: (id: number) => void;
  elegirFecha: (f: string) => void;
  /** null = quitar la selección. */
  elegirTurno: (t: TurnoElegido | null) => void;
  guardarCliente: (c: DatosClienteEntrada, verificado: boolean) => void;
  guardarPago: (p: Partial<PagoElegido>) => void;
  setArchivo: (f: File | null) => void;
  registrarReserva: (r: ReservaCreada) => void;
  marcarConflicto: () => void;
  ponerErroresServidor: (paso: 2 | 3, errores: Record<string, string>) => void;
  limpiarErroresServidor: () => void;
  reiniciar: () => void;
}

const CLAVE = 'alc-asistente';
const INICIAL: Estado = {
  paso: 1,
  idCancha: null,
  fecha: null,
  turnos: [],
  cliente: null,
  verificadoReniec: false,
  pago: { metodoPago: null, tipoPago: 'ADELANTO', codigoOperacion: '' },
  reserva: null,
  erroresServidor: null,
  turnoPerdido: null,
};

function leer(): Estado {
  try {
    const s = JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as (Partial<Estado> & { turno?: TurnoElegido | null }) | null;
    if (!s) return INICIAL;
    // Sesiones de la v1 guardaban un solo `turno`.
    const turnos = Array.isArray(s.turnos) ? s.turnos : s.turno ? [s.turno] : [];
    const { turno: _v1, ...resto } = s;
    return { ...INICIAL, ...resto, turnos, erroresServidor: null };
  } catch {
    return INICIAL;
  }
}

const Contexto = createContext<ApiReserva | null>(null);

export function ReservaProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>(leer);
  const [archivo, setArchivoEstado] = useState<File | null>(null);
  const primera = useRef(true);

  useEffect(() => {
    if (primera.current) {
      primera.current = false;
      return;
    }
    const { erroresServidor: _e, ...persistir } = estado;
    sessionStorage.setItem(CLAVE, JSON.stringify(persistir));
  }, [estado]);

  const cambiar = useCallback((p: Partial<Estado>) => setEstado((e) => ({ ...e, ...p })), []);

  const api = useMemo<ApiReserva>(
    () => ({
      ...estado,
      turno: componer(estado.turnos),
      archivo,
      irAPaso: (paso) => cambiar({ paso }),
      elegirCancha: (idCancha) =>
        setEstado((e) => (e.idCancha === idCancha ? e : { ...e, idCancha, turnos: e.turnos.filter((t) => t.idCancha === idCancha) })),
      elegirFecha: (fecha) => setEstado((e) => ({ ...e, fecha, turnos: e.turnos.filter((t) => t.fecha === fecha) })),
      elegirTurno: (turno) => cambiar({ turnos: turno ? [turno] : [], turnoPerdido: null }),
      alternarTurno: (t) =>
        setEstado((e) => {
          const misma = e.turnos.filter((x) => x.idCancha === t.idCancha && x.fecha === t.fecha);
          const ya = misma.some((x) => x.idTurno === t.idTurno);
          const turnos = ya ? misma.filter((x) => x.idTurno !== t.idTurno) : misma.length >= MAX_TURNOS ? misma : [...misma, t];
          return { ...e, turnos, turnoPerdido: null };
        }),
      guardarCliente: (cliente, verificadoReniec) => cambiar({ cliente, verificadoReniec }),
      guardarPago: (p) => setEstado((e) => ({ ...e, pago: { ...e.pago, ...p } })),
      setArchivo: setArchivoEstado,
      registrarReserva: (reserva) => cambiar({ reserva }),
      marcarConflicto: () => setEstado((e) => ({ ...e, paso: 1, turnoPerdido: componer(e.turnos), turnos: [], reserva: null })),
      ponerErroresServidor: (paso, errores) => cambiar({ erroresServidor: { paso, errores }, paso }),
      limpiarErroresServidor: () => cambiar({ erroresServidor: null }),
      reiniciar: () => {
        sessionStorage.removeItem(CLAVE);
        setArchivoEstado(null);
        setEstado(INICIAL);
      },
    }),
    [estado, archivo, cambiar],
  );

  return <Contexto.Provider value={api}>{children}</Contexto.Provider>;
}

export function useReserva(): ApiReserva {
  const c = useContext(Contexto);
  if (!c) throw new Error('useReserva fuera de ReservaProvider');
  return c;
}

// ------------------------------ última reserva (pantalla «Listo») ------------------------------
export interface UltimaReserva {
  reserva: ReservaCreada;
  dni: string;
  metodo: MetodoPagoWeb;
}
const CLAVE_ULTIMA = 'alc-ultima-reserva';
export function guardarUltimaReserva(u: UltimaReserva) {
  sessionStorage.setItem(CLAVE_ULTIMA, JSON.stringify(u));
}
export function leerUltimaReserva(codigo?: string): UltimaReserva | null {
  try {
    const u = JSON.parse(sessionStorage.getItem(CLAVE_ULTIMA) ?? 'null') as UltimaReserva | null;
    if (!u) return null;
    return !codigo || u.reserva.codigoReserva === codigo ? u : null;
  } catch {
    return null;
  }
}

// ------------------------------ facturación pedida al reservar ------------------------------
// El back no expone la facturación en «Mi reserva» y, al reenviar o pagar de nuevo, emite con lo que se mande en ESE
// pedido. Se recuerda en este navegador para volver a enviarla (en otro dispositivo, el cliente la elige otra vez).
const CLAVE_FACTURACION = 'alc-facturacion';
export function guardarFacturacion(codigo: string, comprobante: DatosComprobante) {
  try {
    const todo = JSON.parse(localStorage.getItem(CLAVE_FACTURACION) ?? '{}') as Record<string, DatosComprobante>;
    todo[codigo] = comprobante;
    localStorage.setItem(CLAVE_FACTURACION, JSON.stringify(todo));
  } catch {
    /* sin almacenamiento: se elige de nuevo */
  }
}
export function leerFacturacion(codigo: string): DatosComprobante | null {
  try {
    return (JSON.parse(localStorage.getItem(CLAVE_FACTURACION) ?? '{}') as Record<string, DatosComprobante>)[codigo] ?? null;
  } catch {
    return null;
  }
}
