import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { CalendarClock, CalendarDays, Clock, Moon, Pencil, Plus, Star, Sun, UsersRound, Layers, EyeOff, CirclePause, CircleCheck } from 'lucide-react';
import { canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin } from '@/api/admin/tipos';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { DatoIcono, ErrorCarga, ModalConfirmar } from '@/componentes/panel/Herramientas';
import { BotonEnlace } from '@/componentes/ui/Boton';
import { Interruptor } from '@/componentes/ui/Campo';
import { Esqueleto, Vacio } from '@/componentes/ui/Datos';
import { IconoDeporte, Ilustracion, ilustracionDeporte } from '@/componentes/ui/Marca';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';
import CanchaFormulario from './CanchaFormulario';
import FichaCanchaAdmin, { type Apartado } from './cancha/FichaCanchaAdmin';
import { textoSemana } from '@/utilidades/horario';

/**
 * /admin/canchas (tarjetas), /admin/canchas/nueva y /admin/canchas/{id}[/horario|/configuracion] (ficha con 3 apartados,
 * SPEC_V2 §F). Solo ADMIN.
 */
export default function Canchas() {
  const sub = useParams()['*'] ?? '';
  if (sub === 'nueva') return <CanchaFormulario />;
  const m = /^(\d+)(?:\/(horario|configuracion))?\/?$/.exec(sub);
  if (m) return <FichaCanchaAdmin key={m[1]} id={Number(m[1])} apartado={(m[2] ?? 'contenido') as Apartado} />;
  return <ListaCanchas />;
}

function ListaCanchas() {
  useTituloPagina('Canchas');
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const lista = useCarga(() => canchasAdminApi.listar(), `canchas-${version}`);
  const [cambio, setCambio] = useState<CanchaAdmin | null>(null);

  const cambiarEstado = async (c: CanchaAdmin) => {
    const nuevo = c.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await canchasAdminApi.estado(c.id, nuevo);
      toast.exito(nuevo === 'ACTIVA' ? 'Cancha activada' : 'Cancha desactivada');
      setCambio(null);
      setVersion((v) => v + 1);
    } catch {
      /* toast del interceptor */
    }
  };

  return (
    <>
      <CabeceraPagina
        titulo="Canchas"
        subtitulo="Toca una cancha para editar su ficha, horario y precios"
        acciones={
          <BotonEnlace to="/admin/canchas/nueva" icono={Plus}>
            Nueva cancha
          </BotonEnlace>
        }
      />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : !lista.datos ? (
        <div className="canchas-panel">
          {Array.from({ length: 4 }, (_, i) => (
            <Esqueleto key={i} alto={360} radio={14} />
          ))}
        </div>
      ) : lista.datos.length === 0 ? (
        <div className="card">
          <Vacio ilustracion="IL-25-sin-canchas" titulo="Sin canchas" acciones={<BotonEnlace to="/admin/canchas/nueva" icono={Plus}>Nueva cancha</BotonEnlace>} />
        </div>
      ) : (
        <div className="canchas-panel">
          {lista.datos.map((c) => {
            const activa = c.estado === 'ACTIVA';
            return (
              <article key={c.id} className={cx('cancha-panel', !activa && 'inactiva')}>
                <Link to={`/admin/canchas/${c.id}`} className="cancha-panel-media" aria-label={`Editar ${c.nombre}`} tabIndex={-1}>
                  {c.fotoPrincipal ? <img src={c.fotoPrincipal} alt={c.nombre} loading="lazy" /> : <Ilustracion id={ilustracionDeporte(c.deporte)} />}
                  <span className="dep">
                    <IconoDeporte deporte={c.deporte} />
                    {c.deporteTexto}
                  </span>
                  {!activa && <span className="estado-portada">Inactiva</span>}
                </Link>
                <div className="cancha-panel-cuerpo">
                  <div className="flex items-start justify-between gap-2">
                    <h3>
                      <Link to={`/admin/canchas/${c.id}`} className="hover:underline">
                        {c.nombre}
                      </Link>
                    </h3>
                    <Interruptor activo={activa} alCambiar={() => setCambio(c)}>
                      <span className="sr-only">{activa ? 'Activa' : 'Inactiva'}</span>
                    </Interruptor>
                  </div>
                  <DatoIcono icono={Clock}>{c.horario?.texto || (c.horario?.dias?.length ? textoSemana(c.horario.dias) : 'Horario por defecto')}</DatoIcono>
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                    <DatoIcono icono={Layers}>{c.superficie}</DatoIcono>
                    <DatoIcono icono={UsersRound}>{c.capacidad}</DatoIcono>
                  </div>
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5">
                    <DatoIcono icono={Sun}>
                      <b>{fmtSoles(c.precioDia, { decimales: 0 })}</b>
                    </DatoIcono>
                    <DatoIcono icono={Moon}>
                      <b>{fmtSoles(c.precioNoche, { decimales: 0 })}</b>
                    </DatoIcono>
                    <DatoIcono icono={Star}>
                      {c.calificacion.total ? `${c.calificacion.promedio.toFixed(1)} (${c.calificacion.total})` : 'Sin reseñas'}
                    </DatoIcono>
                  </div>
                  {!activa && (
                    <div className="oculta-portal">
                      <EyeOff className="ico" aria-hidden />
                      Oculta en el portal
                    </div>
                  )}
                </div>
                <div className="cancha-panel-pie">
                  <BotonEnlace to={`/admin/canchas/${c.id}`} variante="secundario" tamano="sm" icono={Pencil} className="flex-1">
                    Editar
                  </BotonEnlace>
                  <BotonEnlace to={`/admin/canchas/${c.id}/horario`} variante="fantasma" tamano="sm" icono={CalendarClock}>
                    Horario
                  </BotonEnlace>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <ModalConfirmar
        abierto={cambio !== null}
        alCerrar={() => setCambio(null)}
        titulo={cambio?.estado === 'ACTIVA' ? 'Desactivar cancha' : 'Activar cancha'}
        subtitulo={cambio?.nombre}
        busto="BU-rosa-gorra"
        peligro={cambio?.estado === 'ACTIVA'}
        textoConfirmar={cambio?.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'}
        iconoConfirmar={cambio?.estado === 'ACTIVA' ? CirclePause : CircleCheck}
        alConfirmar={() => (cambio ? cambiarEstado(cambio) : undefined)}
      >
        <div className="caja-aviso">
          {cambio?.estado === 'ACTIVA' ? (
            <>
              <div>
                <EyeOff className="ico" aria-hidden />
                Se oculta en el portal
              </div>
              <div>
                <CalendarDays className="ico" aria-hidden />
                Sus turnos libres futuros quedan bloqueados («Cancha inactiva»)
              </div>
              <div>
                <CircleCheck className="ico" aria-hidden />
                Las reservas vigentes se mantienen
              </div>
            </>
          ) : (
            <>
              <div>
                <CircleCheck className="ico" aria-hidden />
                Vuelve a verse en el portal
              </div>
              <div>
                <CalendarDays className="ico" aria-hidden />
                Sus turnos futuros se liberan
              </div>
            </>
          )}
        </div>
      </ModalConfirmar>
    </>
  );
}
