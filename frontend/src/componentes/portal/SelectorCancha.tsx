import { useState } from 'react';
import { Check, Layers, Moon, Repeat2, Star, Sun, Users } from 'lucide-react';
import type { CanchaResumen } from '@/api/tipos';
import { Boton } from '@/componentes/ui/Boton';
import { IconoDeporte, Ilustracion, ilustracionDeporte } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

/** «Cancha 1 · Fútbol 5» → «Cancha 1» (el deporte ya va en su propia línea). */
function nombreCorto(nombre: string): string {
  return nombre.split(' · ')[0] || nombre;
}

function Portada({ cancha, className }: { cancha: CanchaResumen; className?: string }) {
  return (
    <span className={cx('sc-portada', className)}>
      {cancha.fotoPrincipal ? <img src={cancha.fotoPrincipal} alt="" loading="lazy" /> : <Ilustracion id={ilustracionDeporte(cancha.deporte)} />}
    </span>
  );
}

/** Modal con TARJETAS de todas las canchas (grilla responsive: escala si hay más canchas). Tocar una la elige y cierra. */
export function ModalCanchas({ abierto, canchas, activa, alElegir, alCerrar }: { abierto: boolean; canchas: CanchaResumen[]; activa: number | null; alElegir: (id: number) => void; alCerrar: () => void }) {
  return (
    <Modal abierto={abierto} alCerrar={alCerrar} titulo="Elige la cancha" tamano="xl" variante="hoja" className="modal-canchas">
      <div className="sc-grilla" role="radiogroup" aria-label="Canchas">
        {canchas.map((c) => {
          const sel = c.id === activa;
          return (
            <button
              key={c.id}
              type="button"
              role="radio"
              aria-checked={sel}
              className={cx('sc-tarjeta', sel && 'activa')}
              onClick={() => {
                alElegir(c.id);
                alCerrar();
              }}
            >
              <Portada cancha={c} />
              {sel && (
                <span className="sc-marca" aria-hidden>
                  <Check className="ico" />
                </span>
              )}
              <span className="sc-cuerpo">
                <span className="sc-dep">
                  <IconoDeporte deporte={c.deporte} />
                  <span className="sc-dep-txt">{c.deporteTexto}</span>
                </span>
                <b className="sc-nombre">{nombreCorto(c.nombre)}</b>
                <span className="sc-datos">
                  <span>
                    <Users className="ico" aria-hidden />
                    {c.capacidad}
                  </span>
                  <span>
                    <Layers className="ico" aria-hidden />
                    {c.superficie}
                  </span>
                  {c.calificacion.total > 0 && (
                    <span>
                      <Star className="ico text-amarillo fill-amarillo" aria-hidden />
                      {c.calificacion.promedio.toFixed(1)}
                    </span>
                  )}
                </span>
                <span className="sc-precios tnum">
                  <span>
                    <Sun className="ico" aria-hidden />
                    {fmtSoles(c.precioDia, { decimales: 0 })}
                  </span>
                  <span>
                    <Moon className="ico" aria-hidden />
                    {fmtSoles(c.precioNoche, { decimales: 0 })}
                  </span>
                </span>
              </span>
            </button>
          );
        })}
      </div>
    </Modal>
  );
}

/**
 * Cancha elegida en una tarjeta compacta (portada pequeña, nombre corto y deporte) + «Cambiar», que abre el modal de tarjetas.
 * Debajo de 400 px «Cambiar» queda solo con el ícono (40x40). Lo usan el paso 1 del portal y la «Nueva reserva» del panel.
 */
export function SelectorCancha({ canchas, activa, alElegir, className }: { canchas: CanchaResumen[]; activa: number | null; alElegir: (id: number) => void; className?: string }) {
  const [abierto, setAbierto] = useState(false);
  const c = canchas.find((x) => x.id === activa) ?? null;
  return (
    <>
      <div className={cx('sc-elegida', className)}>
        {c ? (
          <>
            <Portada cancha={c} className="pequena" />
            <span className="sc-elegida-txt">
              <b className="sc-elegida-nom" title={c.nombre}>
                {nombreCorto(c.nombre)}
              </b>
              <span className="sc-dep">
                <IconoDeporte deporte={c.deporte} />
                <span className="sc-dep-txt">{c.deporteTexto}</span>
              </span>
            </span>
          </>
        ) : (
          <span className="t-sec">Elige una cancha</span>
        )}
        {canchas.length > 1 && (
          <Boton variante="secundario" tamano="sm" icono={Repeat2} etiqueta="Cambiar cancha" className="sc-cambiar ml-auto shrink-0" onClick={() => setAbierto(true)}>
            <span className="sc-cambiar-txt">Cambiar</span>
          </Boton>
        )}
      </div>
      <ModalCanchas abierto={abierto} canchas={canchas} activa={activa} alElegir={alElegir} alCerrar={() => setAbierto(false)} />
    </>
  );
}
