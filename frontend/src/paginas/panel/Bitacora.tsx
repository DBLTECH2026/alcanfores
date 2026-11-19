import { useEffect, useState } from 'react';
import { Server, UserRound } from 'lucide-react';
import { bitacoraApi } from '@/api/admin';
import type { EntradaBitacora } from '@/api/admin/tipos';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { BarraBusqueda, CeldaDoc, ErrorCarga, FiltrosActivos, GrupoFiltro, OpcionesFiltro, PanelFiltros, useFiltrosUrl, useRetraso } from '@/componentes/panel/Herramientas';
import { Paginacion, Tabla, Vacio } from '@/componentes/ui/Datos';
import { Insignia } from '@/componentes/ui/Estados';
import { fmtFecha, fmtFechaHora } from '@/utilidades/fecha';
import { textoAccion, textoEntidad, textoRol, textoServicio } from '@/utilidades/textosBitacora';

/** Correo con su punto de corte natural después de la arroba («cliente…@» / «example.com»); el resto, tal cual. */
const conCorte = (texto: string) => {
  const i = texto.indexOf('@');
  return i > 0 ? (
    <>
      {texto.slice(0, i + 1)}
      <wbr />
      {texto.slice(i + 1)}
    </>
  ) : (
    texto
  );
};

/** Opciones del filtro ordenadas por su texto en español. */
const ordenadas = <T extends { texto: string }>(xs: T[]) => [...xs].sort((a, b) => a.texto.localeCompare(b.texto, 'es'));

/** /admin/bitacora — acciones auditadas de todos los servicios (evento auditoria.accion). Solo ADMIN. */
export default function Bitacora() {
  useTituloPagina('Bitácora');
  const f = useFiltrosUrl();
  const [actor, setActor] = useState(f.leer('actor'));
  const a = useRetraso(actor);
  useEffect(() => {
    if (a !== f.leer('actor')) f.poner({ actor: a });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [a]);
  const [abierto, setAbierto] = useState(false);
  const opciones = useCarga(() => bitacoraApi.opciones(), 'bit-op');
  const filtros = { servicio: f.leer('servicio'), accion: f.leer('accion'), actor: f.leer('actor'), desde: f.leer('desde'), hasta: f.leer('hasta') };
  const lista = useCarga(
    () => bitacoraApi.listar({ ...filtros, page: f.pagina, size: 20 }),
    JSON.stringify({ ...filtros, p: f.pagina }),
  );
  const activos = (
    [
      ['servicio', textoServicio],
      ['accion', textoAccion],
      ['desde', (v: string) => `Desde ${fmtFecha(v, 'dia-mes')}`],
      ['hasta', (v: string) => `Hasta ${fmtFecha(v, 'dia-mes')}`],
    ] as Array<[keyof typeof filtros, (v: string) => string]>
  )
    .filter(([k]) => filtros[k])
    .map(([k, t]) => ({ clave: k, texto: t(filtros[k]), alQuitar: () => f.poner({ [k]: null }) }));

  return (
    <>
      <CabeceraPagina titulo="Bitácora" subtitulo="Quién hizo qué y cuándo, en todos los servicios" busto="BU-rosa-pizarra" />
      <BarraBusqueda valor={actor} alCambiar={setActor} placeholder="Usuario que hizo la acción" filtrosActivos={activos.length} alAbrirFiltros={() => setAbierto(true)} conteo={lista.datos ? `${lista.datos.totalElementos} acciones` : ''} />
      <FiltrosActivos items={activos} alLimpiar={() => f.limpiar(['actor'])} />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<EntradaBitacora>
          etiqueta="Bitácora"
          filas={lista.datos?.contenido ?? []}
          claveFila={(b) => b.id}
          cargando={lista.cargando}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin acciones" compacto />}
          columnas={[
            { clave: 'fecha', titulo: 'Fecha', celda: (b) => <span className="tnum whitespace-nowrap">{fmtFechaHora(b.fecha, { h24: true })}</span> },
            { clave: 'actor', titulo: 'Actor', celda: (b) => <CeldaDoc icono={UserRound} titulo={<span className="whitespace-normal [overflow-wrap:anywhere]">{conCorte(b.actor)}</span>} sub={b.rol ? textoRol(b.rol) : undefined} /> },
            { clave: 'accion', titulo: 'Acción', celda: (b) => <Insignia tono={/FALLID|RECHAZ|ANUL|CANCEL/.test(b.accion) ? 'rojo-borde' : 'neutro'}>{textoAccion(b.accion)}</Insignia> },
            { clave: 'entidad', titulo: 'Entidad', celda: (b) => (b.entidad ? <span className="tnum">{`${textoEntidad(b.entidad)}${b.idEntidad ? ` · ${b.idEntidad}` : ''}`}</span> : <span className="t-sec">—</span>) },
            { clave: 'detalle', titulo: 'Detalle', celda: (b) => <span className="celda-larga block">{b.detalle ?? '—'}</span> },
            {
              clave: 'servicio',
              titulo: 'Servicio',
              celda: (b) => (
                <span className="dato-ico">
                  <Server className="ico" aria-hidden />
                  <span className="grid min-w-0">
                    <span>{textoServicio(b.servicio)}</span>
                    {b.ip && <span className="sub t-sec tnum [overflow-wrap:anywhere]">{b.ip}</span>}
                  </span>
                </span>
              ),
            },
          ]}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="acciones" />}
        />
      )}
      <PanelFiltros abierto={abierto} alCerrar={() => setAbierto(false)} alLimpiar={() => f.limpiar(['actor'])} resultados={lista.datos?.totalElementos}>
        <GrupoFiltro titulo="Servicio">
          <OpcionesFiltro valor={filtros.servicio} alCambiar={(v) => f.poner({ servicio: v })} opciones={ordenadas((opciones.datos?.servicios ?? []).map((s) => ({ valor: s, texto: textoServicio(s), icono: Server })))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Acción">
          <OpcionesFiltro valor={filtros.accion} alCambiar={(v) => f.poner({ accion: v })} opciones={ordenadas((opciones.datos?.acciones ?? []).map((x) => ({ valor: x, texto: textoAccion(x) })))} />
        </GrupoFiltro>
        <GrupoFiltro titulo="Fecha">
          <div className="pf-fechas">
            <label>
              Desde
              <input type="date" value={filtros.desde} max={filtros.hasta || undefined} onChange={(e) => f.poner({ desde: e.target.value })} />
            </label>
            <label>
              Hasta
              <input type="date" value={filtros.hasta} min={filtros.desde || undefined} onChange={(e) => f.poner({ hasta: e.target.value })} />
            </label>
          </div>
        </GrupoFiltro>
      </PanelFiltros>
    </>
  );
}
