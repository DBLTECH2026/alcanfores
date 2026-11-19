import { Link } from 'react-router-dom';
import {
  ArrowRight,
  BadgeCheck,
  Building2,
  Car,
  CircleCheck,
  CloudRain,
  DoorOpen,
  Layers,
  Lightbulb,
  MessageSquareReply,
  PartyPopper,
  Ruler,
  ShowerHead,
  Sprout,
  Star,
  Users,
  Volleyball,
  type LucideIcon,
} from 'lucide-react';
import type { CanchaResumen, Resena } from '@/api/tipos';
import { IconoDeporte, Ilustracion, ilustracionDeporte } from '@/componentes/ui/Marca';
import { Avatar } from '@/componentes/ui/Datos';
import { Estrellas } from '@/componentes/ui/Estrellas';
import { clasesBoton } from '@/componentes/ui/Boton';
import { fmtFecha } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
import { cx } from '@/utilidades/cx';

/** Tarjeta de cancha: foto + deporte + datos con íconos + calificación + «desde S/». */
export function TarjetaCancha({ cancha, cta = 'Ver cancha', destino, className }: { cancha: CanchaResumen; cta?: string; destino?: string; className?: string }) {
  const a = destino ?? `/canchas/${cancha.slug || cancha.id}`;
  return (
    <article className={cx('cancha-card', className)}>
      <Link to={a} className="cancha-card-media" aria-label={`Ver ${cancha.nombre}`} tabIndex={-1}>
        {cancha.fotoPrincipal ? <img className="foto" src={cancha.fotoPrincipal} alt="" loading="lazy" /> : <Ilustracion id={ilustracionDeporte(cancha.deporte)} />}
        <span className="badge b-noche">
          <IconoDeporte deporte={cancha.deporte} />
          <span>{cancha.deporteTexto}</span>
        </span>
      </Link>
      <div className="cancha-card-cuerpo content-start">
        <div className="flex items-start justify-between gap-2">
          <h3 className="t-h3">
            <Link to={a} className="hover:underline">
              {cancha.nombre}
            </Link>
          </h3>
          {cancha.calificacion.total > 0 && (
            <span className="cancha-card-calif" aria-label={`${cancha.calificacion.promedio.toFixed(1)} de 5, ${cancha.calificacion.total} reseñas`}>
              <Star className="ico ico-16 text-amarillo fill-amarillo" aria-hidden />
              <b className="tnum">{cancha.calificacion.promedio.toFixed(1)}</b>
              <span className="tnum">({cancha.calificacion.total})</span>
            </span>
          )}
        </div>
        <div className="cancha-card-datos">
          <span>
            <Layers className="ico" aria-hidden />
            {cancha.superficie}
          </span>
          <span>
            <Users className="ico" aria-hidden />
            {`${cancha.capacidad} jugadores`}
          </span>
          {cancha.techada && (
            <span>
              <CloudRain className="ico" aria-hidden />
              Techada
            </span>
          )}
        </div>
      </div>
      <div className="cancha-card-pie">
        <div className="desde">
          <span className="desde-et">Desde</span>
          <span className="desde-val">
            {fmtSoles(cancha.precioDia, { decimales: 0 })}
            <small>/ hora</small>
          </span>
        </div>
        <Link to={a} className={clasesBoton({ variante: 'secundario', tamano: 'sm' })}>
          <span>{cta}</span>
          <ArrowRight className="ico" aria-hidden />
        </Link>
      </div>
    </article>
  );
}

/** Esqueleto con la misma silueta de la tarjeta. */
export function TarjetaCanchaEsqueleto() {
  return (
    <div className="cancha-card" aria-hidden>
      <span className="skel block h-[170px] !rounded-none" />
      <div className="grid gap-3 p-4">
        <span className="skel skel-linea block w-1/2 h-[18px]" />
        <span className="skel skel-linea block w-4/5 h-3" />
        <span className="skel skel-linea block w-3/5 h-3" />
      </div>
      <div className="flex justify-between items-end gap-3 px-4 pb-4 pt-3 border-t border-dashed border-borde">
        <span className="skel block w-[72px] h-[30px] !rounded-8" />
        <span className="skel block w-[112px] h-9 !rounded-12" />
      </div>
    </div>
  );
}

/** Ícono para cada característica según palabras clave (texto libre del back). */
export function iconoCaracteristica(texto: string): LucideIcon {
  const t = texto.toLowerCase();
  if (/camerin|vestuario/.test(t)) return DoorOpen;
  if (/ducha/.test(t)) return ShowerHead;
  if (/estacion|parqueo|cochera/.test(t)) return Car;
  if (/led|ilumina|luz/.test(t)) return Lightbulb;
  if (/techad|techo|lluvia/.test(t)) return CloudRain;
  if (/grass|césped|cesped|sintétic/.test(t)) return Sprout;
  if (/explanada|evento|fiesta/.test(t)) return PartyPopper;
  if (/vóley|voley|básquet|basquet|multiuso/.test(t)) return Volleyball;
  if (/medida|metros|m²|largo/.test(t)) return Ruler;
  if (/tribuna|graderí|grader/.test(t)) return Building2;
  return CircleCheck;
}

/** Promedio grande + estrellas + barras de distribución 5→1. */
export function ResumenCalificacion({ promedio, total, distribucion }: { promedio: number; total: number; distribucion: Record<string, number> }) {
  return (
    <div className="grid gap-4">
      <div className="flex items-end gap-4">
        <span className="font-titular font-extrabold text-[56px] leading-none tnum">{total ? promedio.toFixed(1) : '–'}</span>
        <div className="grid gap-1 pb-1">
          <Estrellas valor={promedio} tamano={20} />
          <span className="t-sec tnum">{total === 1 ? '1 reseña verificada' : `${total} reseñas verificadas`}</span>
        </div>
      </div>
      <div className="distribucion" aria-label="Distribución de calificaciones">
        {[5, 4, 3, 2, 1].map((n) => {
          const c = distribucion[String(n)] ?? 0;
          const pct = total ? Math.round((c / total) * 100) : 0;
          return (
            <div key={n} className="dist-fila">
              <b>
                {n}
                <Star className="ico ico-14 text-amarillo fill-amarillo" aria-hidden />
              </b>
              <span className="dist-barra" role="img" aria-label={`${n} estrellas: ${c} reseñas (${pct} %)`}>
                <i style={{ width: `${pct}%` }} />
              </span>
              <span className="text-right">{c}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** Una reseña: autor, estrellas, fecha, insignia «Reserva verificada», comentario y respuesta del complejo. */
export function ItemResena({ resena }: { resena: Resena }) {
  return (
    <article className="resena">
      <div className="resena-cab">
        <Avatar nombre={resena.autor} />
        <div className="grid gap-0.5 min-w-0">
          <b className="text-[14.5px]">{resena.autor}</b>
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <Estrellas valor={resena.puntuacion} tamano={14} />
            <span className="t-mini tnum">{fmtFecha(resena.fecha.slice(0, 10), 'dia-mes-anio')}</span>
          </span>
        </div>
        {resena.verificada && (
          <span className="badge badge-sm b-menta ml-auto shrink-0">
            <BadgeCheck className="ico" aria-hidden />
            <span>Reserva verificada</span>
          </span>
        )}
      </div>
      <p className="text-[14.5px] leading-relaxed">{resena.comentario}</p>
      {resena.respuesta && (
        <div className="resena-respuesta">
          <span className="inline-flex items-center gap-1.5 font-semibold text-profundo text-[13px]">
            <MessageSquareReply className="ico ico-16" aria-hidden />
            Respuesta del complejo
          </span>
          <span>{resena.respuesta}</span>
        </div>
      )}
    </article>
  );
}
