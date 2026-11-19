import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-react';
import { registrarNotificador } from '@/api/cliente';
import { cx } from '@/utilidades/cx';

export type TipoToast = 'exito' | 'info' | 'advertencia' | 'error';
interface Toast {
  id: number;
  tipo: TipoToast;
  texto: string;
  accion?: { texto: string; alHacer: () => void };
}
interface ApiToast {
  mostrar: (tipo: TipoToast, texto: string, accion?: Toast['accion']) => void;
  exito: (texto: string) => void;
  info: (texto: string) => void;
  advertencia: (texto: string) => void;
  error: (texto: string) => void;
}

const Contexto = createContext<ApiToast | null>(null);
const ICONOS = { exito: CircleCheck, info: Info, advertencia: TriangleAlert, error: CircleAlert } as const;
const DURACION = 4500;

/** Toasts del sistema (prototipo §9: arriba, ≤ 8 palabras + ícono). También recibe los errores del interceptor de Axios. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);

  const cerrar = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);

  const mostrar = useCallback<ApiToast['mostrar']>(
    (tipo, texto, accion) => {
      seq.current += 1;
      const id = seq.current;
      setToasts((t) => [...t.filter((x) => x.texto !== texto).slice(-2), { id, tipo, texto, accion }]);
      window.setTimeout(() => cerrar(id), tipo === 'error' ? DURACION + 1500 : DURACION);
    },
    [cerrar],
  );

  const api = useMemo<ApiToast>(
    () => ({
      mostrar,
      exito: (t) => mostrar('exito', t),
      info: (t) => mostrar('info', t),
      advertencia: (t) => mostrar('advertencia', t),
      error: (t) => mostrar('error', t),
    }),
    [mostrar],
  );

  useEffect(() => {
    registrarNotificador((m) => mostrar('error', m));
    return () => registrarNotificador(null);
  }, [mostrar]);

  return (
    <Contexto.Provider value={api}>
      {children}
      <div className="capa-toast" aria-live="polite">
        {toasts.map((t) => {
          const Icono = ICONOS[t.tipo];
          return (
            <div key={t.id} className={cx('toast animate-entrar', `toast-${t.tipo}`)} role={t.tipo === 'error' ? 'alert' : 'status'}>
              <Icono className="ico" aria-hidden />
              <span className="toast-texto">{t.texto}</span>
              {t.accion && (
                <button
                  type="button"
                  className="toast-accion"
                  onClick={() => {
                    t.accion?.alHacer();
                    cerrar(t.id);
                  }}
                >
                  {t.accion.texto}
                </button>
              )}
              <button type="button" className="toast-cerrar" aria-label="Cerrar aviso" onClick={() => cerrar(t.id)}>
                <X className="ico ico-16" aria-hidden />
              </button>
            </div>
          );
        })}
      </div>
    </Contexto.Provider>
  );
}

export function useToast(): ApiToast {
  const c = useContext(Contexto);
  if (!c) throw new Error('useToast fuera de ToastProvider');
  return c;
}
