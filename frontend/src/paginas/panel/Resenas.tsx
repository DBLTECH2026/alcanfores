import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { CornerDownRight, Eye, EyeOff, MessageSquareReply, Star } from 'lucide-react';
import { canchasAdminApi, resenasAdminApi } from '@/api/admin';
import type { ResenaAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, ModalConfirmar, useFiltrosUrl } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { AreaTexto } from '@/componentes/ui/Campo';
import { Avatar, Esqueleto, Paginacion, Pestanas, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { InsigniaEstado } from '@/componentes/ui/Estados';
import { Estrellas } from '@/componentes/ui/Estrellas';
import { fmtFechaHora } from '@/utilidades/fecha';
import { cx } from '@/utilidades/cx';
import { esquemaRespuesta } from './esquemasPanel';

/** /admin/resenas — moderar (ocultar / publicar) y responder reseñas verificadas. Solo ADMIN. */
export default function Resenas() {
  useTituloPagina('Reseñas');
  const toast = useToast();
  const f = useFiltrosUrl();
  const [version, setVersion] = useState(0);
  const canchas = useCarga(() => canchasAdminApi.listar(), 'canchas-resenas');
  const filtros = { estado: f.leer('estado'), idCancha: f.leer('idCancha'), puntuacion: f.leer('puntuacion') };
  const lista = useCarga(() => resenasAdminApi.listar({ ...filtros, page: f.pagina, size: 10, sort: 'creadoEn,desc' }), JSON.stringify({ ...filtros, p: f.pagina, version }));
  const [moderar, setModerar] = useState<ResenaAdmin | null>(null);
  const [responder, setResponder] = useState<ResenaAdmin | null>(null);
  const tras = () => {
    setModerar(null);
    setResponder(null);
    setVersion((v) => v + 1);
  };

  const cambiarEstado = async (r: ResenaAdmin) => {
    const nuevo = r.estado === 'PUBLICADA' ? 'OCULTA' : 'PUBLICADA';
    try {
      await resenasAdminApi.estado(r.id, nuevo);
      toast.exito(nuevo === 'OCULTA' ? 'Reseña oculta' : 'Reseña publicada');
      tras();
    } catch {
      /* toast del interceptor */
    }
  };

  const promedio = canchas.datos?.length ? canchas.datos.reduce((s, c) => s + c.calificacion.promedio * c.calificacion.total, 0) / Math.max(1, canchas.datos.reduce((s, c) => s + c.calificacion.total, 0)) : 0;
  const totalPub = canchas.datos?.reduce((s, c) => s + c.calificacion.total, 0) ?? 0;

  return (
    <>
      <CabeceraPagina titulo="Reseñas" subtitulo="Opiniones verificadas de quienes jugaron" />
      <div className="kpis">
        <TarjetaKpi etiqueta="Promedio" valor={promedio ? promedio.toFixed(1) : '—'} icono={Star} tono="gol" detalle={`${totalPub} publicadas`} />
        {(canchas.datos ?? []).slice(0, 4).map((c) => (
          <TarjetaKpi
            key={c.id}
            etiqueta={c.nombre.split(' · ')[0] ?? c.nombre}
            valor={c.calificacion.total ? c.calificacion.promedio.toFixed(1) : '—'}
            icono={Star}
            tono="gris"
            detalle={`${c.calificacion.total} reseñas`}
            activa={filtros.idCancha === String(c.id)}
            alPulsar={() => f.poner({ idCancha: filtros.idCancha === String(c.id) ? null : c.id })}
          />
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-3 justify-between">
        <Pestanas
          etiqueta="Estado"
          activa={filtros.estado || 'TODAS'}
          alCambiar={(v) => f.poner({ estado: v === 'TODAS' ? null : v })}
          items={[
            { valor: 'TODAS', texto: 'Todas' },
            { valor: 'PUBLICADA', texto: 'Publicadas', icono: Eye },
            { valor: 'OCULTA', texto: 'Ocultas', icono: EyeOff },
          ]}
        />
        <div className="flex flex-wrap items-center gap-2">
          <select className="bb-select" aria-label="Cancha" value={filtros.idCancha} onChange={(e) => f.poner({ idCancha: e.target.value })}>
            <option value="">Todas las canchas</option>
            {(canchas.datos ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
          <select className="bb-select" aria-label="Estrellas" value={filtros.puntuacion} onChange={(e) => f.poner({ puntuacion: e.target.value })}>
            <option value="">Todas las estrellas</option>
            {[5, 4, 3, 2, 1].map((n) => (
              <option key={n} value={n}>
                {n} {n === 1 ? 'estrella' : 'estrellas'}
              </option>
            ))}
          </select>
        </div>
      </div>

      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <section className="pc">
          {!lista.datos ? (
            <div className="grid gap-3 p-4">
              <Esqueleto alto={90} />
              <Esqueleto alto={90} />
            </div>
          ) : lista.datos.contenido.length === 0 ? (
            <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin reseñas" texto="Prueba con otros filtros." compacto />
          ) : (
            <ul className="grid">
              {lista.datos.contenido.map((r) => (
                <li key={r.id} className={cx('resena-panel', r.estado === 'OCULTA' && 'oculta')}>
                  <div className="flex flex-wrap items-center gap-3">
                    <Avatar nombre={r.autor} />
                    <div className="grid min-w-0">
                      <b>{r.autor}</b>
                      <span className="t-sec text-[12.5px]">
                        {r.cancha} · {r.codigoReserva} · {fmtFechaHora(r.creadoEn, { h24: true })}
                      </span>
                    </div>
                    <Estrellas valor={r.puntuacion} />
                    <span className="ml-auto flex flex-wrap items-center gap-2">
                      <InsigniaEstado dominio="resena" estado={r.estado} tamano="sm" />
                      <Boton variante="secundario" tamano="sm" icono={MessageSquareReply} onClick={() => setResponder(r)}>
                        {r.respuesta ? 'Editar respuesta' : 'Responder'}
                      </Boton>
                      <Boton variante={r.estado === 'PUBLICADA' ? 'fantasma' : 'secundario'} tamano="sm" icono={r.estado === 'PUBLICADA' ? EyeOff : Eye} onClick={() => setModerar(r)}>
                        {r.estado === 'PUBLICADA' ? 'Ocultar' : 'Publicar'}
                      </Boton>
                    </span>
                  </div>
                  <p className="texto m-0 text-[14px] leading-relaxed break-words min-w-0">{r.comentario}</p>
                  {r.respuesta && (
                    <div className="respuesta">
                      <b className="inline-flex items-center gap-1.5 text-[12.5px] text-profundo">
                        <CornerDownRight className="ico ico-14" aria-hidden />
                        Respuesta del complejo{r.respondidoEn ? ` · ${fmtFechaHora(r.respondidoEn, { h24: true, sinAnio: true })}` : ''}
                      </b>
                      <p className="m-0 mt-1">{r.respuesta}</p>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
          {lista.datos && lista.datos.totalPaginas > 1 && (
            <div className="border-t border-[#EEF2F0] px-2">
              <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="reseñas" />
            </div>
          )}
        </section>
      )}

      <ModalConfirmar
        abierto={moderar !== null}
        alCerrar={() => setModerar(null)}
        titulo={moderar?.estado === 'PUBLICADA' ? 'Ocultar reseña' : 'Publicar reseña'}
        subtitulo={moderar ? `${moderar.autor} · ${moderar.cancha}` : ''}
        busto="BU-lucho-silbato"
        peligro={moderar?.estado === 'PUBLICADA'}
        textoConfirmar={moderar?.estado === 'PUBLICADA' ? 'Ocultar' : 'Publicar'}
        iconoConfirmar={moderar?.estado === 'PUBLICADA' ? EyeOff : Eye}
        alConfirmar={() => (moderar ? cambiarEstado(moderar) : undefined)}
      >
        {moderar && (
          <div className="grid gap-3">
            <Estrellas valor={moderar.puntuacion} />
            <p className="m-0 text-[14px]">«{moderar.comentario}»</p>
            <div className="caja-aviso">
              <div>
                {moderar.estado === 'PUBLICADA' ? <EyeOff className="ico" aria-hidden /> : <Eye className="ico" aria-hidden />}
                {moderar.estado === 'PUBLICADA' ? 'Deja de verse y de contar en el promedio' : 'Vuelve a verse y a contar en el promedio'}
              </div>
            </div>
          </div>
        )}
      </ModalConfirmar>
      <ModalResponder resena={responder} alCerrar={() => setResponder(null)} alHecho={tras} />
    </>
  );
}

function ModalResponder({ resena, alCerrar, alHecho }: { resena: ResenaAdmin | null; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    formState: { errors },
  } = useForm<z.infer<typeof esquemaRespuesta>>({ resolver: zodResolver(esquemaRespuesta), mode: 'onTouched' });
  useEffect(() => reset({ respuesta: resena?.respuesta ?? '' }), [resena, reset]);
  const enviar = handleSubmit(async ({ respuesta }) => {
    if (!resena) return;
    try {
      await resenasAdminApi.responder(resena.id, respuesta);
      toast.exito('Respuesta publicada');
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['respuesta']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo responder.');
    }
  });
  return (
    <ModalConfirmar
      abierto={resena !== null}
      alCerrar={alCerrar}
      titulo="Responder reseña"
      subtitulo={resena ? `${resena.autor} · ${resena.cancha}` : ''}
      busto="BU-lucho-silbato"
      tamano="lg"
      textoConfirmar="Publicar respuesta"
      iconoConfirmar={MessageSquareReply}
      alConfirmar={enviar}
    >
      {resena && (
        <form className="grid gap-3" onSubmit={enviar} noValidate>
          <div className="grid gap-1">
            <Estrellas valor={resena.puntuacion} />
            <p className="m-0 text-[14px] text-[#DCE5E0]">«{resena.comentario}»</p>
          </div>
          <AreaTexto label="Respuesta pública" requerido rows={4} maxLength={500} contador={{ actual: (watch('respuesta') ?? '').length, max: 500 }} error={errors.respuesta?.message} {...register('respuesta')} />
        </form>
      )}
    </ModalConfirmar>
  );
}
