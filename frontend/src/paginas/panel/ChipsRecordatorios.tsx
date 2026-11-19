import { useState } from 'react';
import { BellRing, Check, CircleAlert, Plus, X } from 'lucide-react';
import { Boton } from '@/componentes/ui/Boton';
import { fmtMinutosAntes } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import { MAX_RECORDATORIOS, leerListaMinutos } from './esquemasPanel';

const RAPIDAS = [15, 30, 60, 120, 180, 1440];

/**
 * «Recordatorios» de Reglas de reserva: chips («2 h antes») que se quitan con la X, «Agregar» con opciones
 * rápidas y «Otro» en minutos. Máximo 3, sin repetidos. Valor = lista separada por comas («120,15»).
 */
export function ChipsRecordatorios({
  etiqueta,
  ayuda,
  valor,
  alCambiar,
  error,
  min,
  max,
}: {
  etiqueta: string;
  ayuda?: string;
  valor: string;
  alCambiar: (v: string) => void;
  error?: string;
  min: number;
  max: number;
}) {
  const lista = leerListaMinutos(valor).filter((n) => Number.isFinite(n));
  const [abierto, setAbierto] = useState(false);
  const [otro, setOtro] = useState('');
  const [errorOtro, setErrorOtro] = useState<string | null>(null);
  const lleno = lista.length >= MAX_RECORDATORIOS;
  const fijar = (l: number[]) => alCambiar([...l].sort((a, b) => b - a).join(','));
  const agregar = (m: number) => {
    if (lista.includes(m) || lleno) return;
    fijar([...lista, m]);
    setAbierto(false);
    setOtro('');
    setErrorOtro(null);
  };
  const agregarOtro = () => {
    const n = Number(otro);
    if (!/^\d{1,4}$/.test(otro) || n < min || n > max) return setErrorOtro(`De ${min} a ${max} minutos.`);
    if (lista.includes(n)) return setErrorOtro('Ese recordatorio ya está.');
    agregar(n);
  };

  return (
    <div className={cx('campo chips-rec', error && 'is-error')} style={{ gridColumn: '1 / -1' }}>
      <span className="campo-label">
        {etiqueta}
        <span className="req" aria-hidden>
          *
        </span>
      </span>
      <div className="chips-rec-fila" role="list" aria-label={etiqueta}>
        {lista.map((m) => (
          <span key={m} className="chip-rec" role="listitem">
            <BellRing className="ico ico-16" aria-hidden />
            <span className="tnum">{fmtMinutosAntes(m)} antes</span>
            <button type="button" aria-label={`Quitar ${fmtMinutosAntes(m)} antes`} onClick={() => fijar(lista.filter((x) => x !== m))}>
              <X className="ico ico-16" aria-hidden />
            </button>
          </span>
        ))}
        {!lleno && (
          <Boton variante="fantasma" tamano="sm" icono={Plus} aria-expanded={abierto} onClick={() => setAbierto((a) => !a)}>
            Agregar
          </Boton>
        )}
      </div>
      {abierto && !lleno && (
        <div className="chips-rec-menu">
          <div className="flex flex-wrap gap-2">
            {RAPIDAS.filter((m) => m >= min && m <= max).map((m) => (
              <button key={m} type="button" className="chip-rapida" disabled={lista.includes(m)} onClick={() => agregar(m)}>
                {fmtMinutosAntes(m)}
              </button>
            ))}
          </div>
          <div className="flex items-start gap-2">
            <div className="control !w-[150px]">
              <input
                inputMode="numeric"
                maxLength={4}
                placeholder="Otro"
                aria-label="Otro recordatorio, en minutos"
                value={otro}
                onChange={(e) => {
                  setOtro(e.target.value.replace(/\D/g, ''));
                  setErrorOtro(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    agregarOtro();
                  }
                }}
              />
              <span className="control-afijo der">min</span>
            </div>
            <Boton variante="secundario" icono={Check} disabled={!otro} onClick={agregarOtro}>
              Agregar
            </Boton>
          </div>
          {errorOtro && (
            <div className="campo-error" role="alert">
              <CircleAlert className="ico" aria-hidden />
              <span>{errorOtro}</span>
            </div>
          )}
        </div>
      )}
      {error ? (
        <div className="campo-error" role="alert">
          <CircleAlert className="ico" aria-hidden />
          <span>{error}</span>
        </div>
      ) : (
        <div className="campo-ayuda">
          <span>{lleno ? `${ayuda ?? ''} · ya tienes ${MAX_RECORDATORIOS}` : ayuda}</span>
        </div>
      )}
    </div>
  );
}
