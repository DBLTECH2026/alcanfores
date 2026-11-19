import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarClock, Eye, LayoutTemplate, Settings2 } from 'lucide-react';
import { canchasAdminApi } from '@/api/admin';
import type { CanchaAdmin, HorarioAdmin, ResenaAdmin } from '@/api/admin/tipos';
import type { CanchaDetalle } from '@/api/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga } from '@/componentes/panel/Herramientas';
import { BotonExterno } from '@/componentes/ui/Boton';
import { Cargando, Pestanas } from '@/componentes/ui/Datos';
import { InsigniaEstado } from '@/componentes/ui/Estados';
import { textoSemana } from '@/utilidades/horario';
import { deCancha, type Entrada } from '../CanchaFormulario';
import { HORARIO_DEFECTO, cuerpoDe, erroresDe } from './utilCancha';
import { EditorVisual } from './EditorVisual';
import { HorarioCancha } from './HorarioCancha';
import { ConfigCancha } from './ConfigCancha';

export type Apartado = 'contenido' | 'horario' | 'configuracion';

/** Objeto PÚBLICO de la cancha armado con el borrador: es lo que pinta el editor visual. */
function aPublica(c: CanchaAdmin, e: Entrada, horario: HorarioAdmin | null, resenas: ResenaAdmin[]): CanchaDetalle {
  const pub = resenas.filter((r) => r.estado === 'PUBLICADA');
  const total = pub.length;
  const promedio = total ? Math.round((pub.reduce((a, r) => a + r.puntuacion, 0) / total) * 10) / 10 : 0;
  const distribucion = { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 } as Record<'1' | '2' | '3' | '4' | '5', number>;
  pub.forEach((r) => (distribucion[String(r.puntuacion) as '1'] += 1));
  const h = horario ?? HORARIO_DEFECTO;
  return {
    id: c.id,
    slug: c.slug,
    nombre: e.nombre || c.nombre,
    deporte: (e.deporte ?? c.deporte) as CanchaAdmin['deporte'],
    deporteTexto: c.deporteTexto,
    superficie: e.superficie || c.superficie,
    capacidad: Number(e.capacidad) || c.capacidad,
    techada: Boolean(e.techada),
    iluminacionLed: Boolean(e.iluminacionLed),
    descripcion: e.descripcion || '',
    descripcionLarga: e.descripcionLarga || null,
    medidas: e.medidas || null,
    caracteristicas: e.caracteristicas ?? [],
    precioDia: Number(e.precioDia) || 0,
    precioNoche: Number(e.precioNoche) || 0,
    fotoPrincipal: c.fotoPrincipal,
    fotos: [...c.fotos].sort((a, b) => Number(b.principal) - Number(a.principal) || a.orden - b.orden),
    calificacion: { promedio, total },
    resumenResenas: { promedio, total, distribucion },
    horario: { duracionTurnoMin: h.duracionTurnoMin, inicioNoche: h.inicioNoche, dias: h.dias, texto: textoSemana(h.dias) },
    feriado: h.feriado,
  };
}

/**
 * /admin/canchas/{id}[/horario|/configuracion] — ficha de la cancha con 3 apartados (SPEC_V2 §F), como el editor
 * de tours de Taste Perú: 1) Vista y contenido (editor VISUAL sobre el detalle público), 2) Horario y precios, 3) Configuración.
 */
export default function FichaCanchaAdmin({ id, apartado }: { id: number; apartado: Apartado }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const carga = useCarga(() => canchasAdminApi.obtener(id), `cancha-${id}-${version}`);
  const hor = useCarga(() => canchasAdminApi.horario(id).catch(() => null), `horario-${id}`);
  const res = useCarga(() => canchasAdminApi.resenas(id).catch(() => [] as ResenaAdmin[]), `resenas-${id}`);
  const cancha = carga.datos;
  useTituloPagina(cancha?.nombre ?? 'Cancha');

  // Borrador común de los datos de la cancha (el PUT lleva todo): lo comparten el editor y Configuración.
  const [entrada, setEntrada] = useState<Entrada | null>(null);
  useEffect(() => {
    if (cancha) setEntrada(deCancha(cancha));
  }, [cancha]);
  const original = useMemo(() => (cancha ? JSON.stringify(deCancha(cancha)) : ''), [cancha]);
  const sucio = Boolean(entrada) && JSON.stringify(entrada) !== original;
  const [guardando, setGuardando] = useState(false);

  const cambiar = (p: Partial<Entrada>) => setEntrada((e) => (e ? { ...e, ...p } : e));
  const descartar = () => cancha && setEntrada(deCancha(cancha));
  /** Guarda el borrador; devuelve los errores por campo si no pasa la validación. */
  const guardar = async (extra?: Partial<Entrada>): Promise<Record<string, string> | null> => {
    if (!entrada) return null;
    const e = { ...entrada, ...extra };
    const errores = erroresDe(e);
    if (Object.keys(errores).length) {
      toast.error('Revisa los datos marcados');
      return errores;
    }
    setGuardando(true);
    try {
      const r = await canchasAdminApi.editar(id, cuerpoDe(e));
      carga.setDatos(r);
      toast.exito('Cambios publicados');
      return null;
    } catch (err) {
      const errores2: Record<string, string> = {};
      if (esErrorApi(err)) for (const x of err.errores) errores2[x.campo.replace(/\[\d+\]$/, '')] = x.mensaje;
      toast.error(esErrorApi(err) ? err.message : 'No se pudo guardar.');
      return errores2;
    } finally {
      setGuardando(false);
    }
  };

  if (carga.error) return <ErrorCarga alReintentar={carga.recargar} />;
  if (!cancha || !entrada) return <Cargando texto="Cargando cancha…" />;
  const horario = hor.datos ?? null;
  const publica = aPublica(cancha, entrada, horario, res.datos ?? []);

  return (
    <>
      <CabeceraPagina
        titulo={cancha.nombre}
        subtitulo={
          <span className="inline-flex flex-wrap items-center gap-2">
            <InsigniaEstado dominio="cancha" estado={cancha.estado} tamano="sm" />
            {horario ? textoSemana(horario.dias) : cancha.deporteTexto}
          </span>
        }
        busto="BU-rosa-gorra"
        volver="/admin/canchas"
        acciones={
          <BotonExterno href={`/canchas/${cancha.slug || cancha.id}`} target="_blank" rel="noopener" variante="secundario" icono={Eye}>
            Ver en el portal
          </BotonExterno>
        }
      />
      <Pestanas
        etiqueta="Apartados de la cancha"
        activa={apartado}
        alCambiar={(a) => navigate(a === 'contenido' ? `/admin/canchas/${id}` : `/admin/canchas/${id}/${a}`)}
        items={[
          { valor: 'contenido', texto: 'Vista y contenido', icono: LayoutTemplate },
          { valor: 'horario', texto: 'Horario y precios', icono: CalendarClock },
          { valor: 'configuracion', texto: 'Configuración', icono: Settings2 },
        ]}
      />
      {apartado === 'contenido' && (
        <EditorVisual
          cancha={cancha}
          publica={publica}
          entrada={entrada}
          cambiar={cambiar}
          sucio={sucio}
          guardando={guardando}
          guardar={guardar}
          descartar={descartar}
          resenas={res.datos ?? []}
          recargarResenas={res.recargar}
          recargarCancha={() => setVersion((v) => v + 1)}
          irAHorario={() => navigate(`/admin/canchas/${id}/horario`)}
        />
      )}
      {apartado === 'horario' && (
        <HorarioCancha
          cancha={cancha}
          horario={horario}
          cargando={hor.cargando}
          entrada={entrada}
          cambiar={cambiar}
          guardarCancha={guardar}
          alGuardado={(h) => hor.setDatos(h)}
        />
      )}
      {apartado === 'configuracion' && <ConfigCancha cancha={cancha} entrada={entrada} cambiar={cambiar} sucio={sucio} guardando={guardando} guardar={guardar} descartar={descartar} alCambiarEstado={() => setVersion((v) => v + 1)} />}
    </>
  );
}
