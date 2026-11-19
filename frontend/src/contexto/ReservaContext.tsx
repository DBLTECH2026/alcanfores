import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { DatosComprobante, Deporte, Franja, MetodoPagoWeb, ReservaCreada, TipoPago } from '@/api/tipos';
import type { DatosClienteEntrada } from '@/esquemas/reserva';

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
  turno: TurnoElegido | null;
  cliente: DatosClienteEntrada | null;
  /** true si el DNI se verificó con RENIEC (los nombres vienen del servicio). */
  verificadoReniec: boolean;
  pago: PagoElegido;
  /** Reserva ya creada (PENDIENTE) mientras se completa el pago. */
  reserva: ReservaCreada | null;
  /** Errores por campo del back que deben pintarse en un paso. */
  erroresServidor: { paso: 2 | 3; errores: Record<string, string> } | null;
  /** Hora del turno que se perdió por 409 (para el aviso del paso 1). */
  turnoPerdido: TurnoElegido | null;
}

interface ApiReserva extends Estado {
  archivo: File | null;
  irAPaso: (p: Paso) => void;
  elegirCancha: (id: number) => void;
  elegirFecha: (f: string) => void;
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
  turno: null,
  cliente: null,
  verificadoReniec: false,
  pago: { metodoPago: null, tipoPago: 'ADELANTO', codigoOperacion: '' },
  reserva: null,
  erroresServidor: null,
  turnoPerdido: null,
};

function leer(): Estado {
  try {
    const s = JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null') as Partial<Estado> | null;
    return s ? { ...INICIAL, ...s, erroresServidor: null } : INICIAL;
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
      archivo,
      irAPaso: (paso) => cambiar({ paso }),
      elegirCancha: (idCancha) =>
        setEstado((e) => (e.idCancha === idCancha ? e : { ...e, idCancha, turno: e.turno?.idCancha === idCancha ? e.turno : null })),
      elegirFecha: (fecha) => setEstado((e) => ({ ...e, fecha, turno: e.turno?.fecha === fecha ? e.turno : null })),
      elegirTurno: (turno) => cambiar({ turno, turnoPerdido: null }),
      guardarCliente: (cliente, verificadoReniec) => cambiar({ cliente, verificadoReniec }),
      guardarPago: (p) => setEstado((e) => ({ ...e, pago: { ...e.pago, ...p } })),
      setArchivo: setArchivoEstado,
      registrarReserva: (reserva) => cambiar({ reserva }),
      marcarConflicto: () => setEstado((e) => ({ ...e, paso: 1, turnoPerdido: e.turno, turno: null, reserva: null })),
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
