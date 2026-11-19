import { useMemo, useState, type ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, LabelList, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipProps } from 'recharts';
import {
  Banknote,
  CalendarCheck,
  CalendarDays,
  CircleX,
  ClipboardList,
  Download,
  Gauge,
  Info,
  Receipt,
  Timer,
  Trophy,
  UserX,
} from 'lucide-react';
import { reportesApi } from '@/api/admin';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { useToast } from '@/contexto/ToastContext';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga } from '@/componentes/panel/Herramientas';
import { descargarArchivo } from '@/componentes/panel/archivos';
import { Boton } from '@/componentes/ui/Boton';
import { Esqueleto, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { ahoraLima, fmtFecha, hoyLima, sumarDias } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import dayjs from 'dayjs';

/** Paleta categórica validada (scripts/validate_palette.js, modo claro): verde, ámbar, azul, magenta. Orden fijo. */
const C = { verde: '#27811F', ambar: '#C98A00', azul: '#2F6FB0', magenta: '#B4477A' } as const;
const EJE = { fontSize: 11.5, fill: '#6B7A73' };
const TEXTO_ORIGEN: Record<string, string> = { WEB: 'Web', PRESENCIAL: 'Presencial', TELEFONICA: 'Teléfono' };
const TEXTO_ACTOR: Record<string, string> = { CLIENTE: 'Cliente', ADMINISTRADOR: 'Complejo', SISTEMA: 'Sistema (sin pago)' };


type PropsEtiqueta = { x?: number | string; y?: number | string; width?: number | string; height?: number | string; value?: number | string };
const n = (v: number | string | undefined) => Number(v ?? 0);
/** Etiquetas de valor sin cortes de línea (recharts parte el texto si la barra es angosta). */
const etiquetaDer = (formato: (v: number) => string) => (p: PropsEtiqueta) => (
  <text x={n(p.x) + n(p.width) + 6} y={n(p.y) + n(p.height) / 2} dy={4} fontSize={12} fill="#091A2A">
    {formato(n(p.value))}
  </text>
);
const etiquetaArriba = (formato: (v: number) => string) => (p: PropsEtiqueta) => (
  <text x={n(p.x) + n(p.width) / 2} y={n(p.y) - 6} textAnchor="middle" fontSize={12} fill="#091A2A">
    {formato(n(p.value))}
  </text>
);
const etiquetaCentro = (p: PropsEtiqueta) =>
  n(p.value) > 0 ? (
    <text x={n(p.x) + n(p.width) / 2} y={n(p.y) + n(p.height) / 2} dy={5} textAnchor="middle" fontSize={13} fontWeight={700} fill="#fff">
      {n(p.value)}
    </text>
  ) : null;

type Rango = { desde: string; hasta: string };
const RAPIDOS: Array<{ id: string; texto: string; rango: () => Rango }> = [
  { id: 'hoy', texto: 'Hoy', rango: () => ({ desde: hoyLima(), hasta: hoyLima() }) },
  { id: '7', texto: '7 días', rango: () => ({ desde: sumarDias(hoyLima(), -6), hasta: hoyLima() }) },
  { id: 'mes', texto: 'Este mes', rango: () => ({ desde: ahoraLima().startOf('month').format('YYYY-MM-DD'), hasta: hoyLima() }) },
  {
    id: 'anterior',
    texto: 'Mes anterior',
    rango: () => {
      const m = ahoraLima().subtract(1, 'month');
      return { desde: m.startOf('month').format('YYYY-MM-DD'), hasta: m.endOf('month').format('YYYY-MM-DD') };
    },
  },
  { id: '30', texto: '30 días', rango: () => ({ desde: sumarDias(hoyLima(), -29), hasta: hoyLima() }) },
];

function validarRango(r: Rango): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(r.desde) || !/^\d{4}-\d{2}-\d{2}$/.test(r.hasta)) return 'Elige las dos fechas.';
  if (r.hasta < r.desde) return 'La fecha final debe ser igual o posterior a la inicial.';
  if (dayjs(r.hasta).diff(dayjs(r.desde), 'day') > 365) return 'El rango máximo es de un año.';
  return null;
}

/** Tooltip con tokens de texto (el color solo en la marca). */
function Globo({
  active,
  payload,
  label,
  formato = (v) => String(v),
  etiqueta,
  extra,
}: TooltipProps<number, string> & { formato?: (v: number) => string; etiqueta?: (l: string) => string; extra?: (fila: Record<string, number>) => Array<[string, string]> }) {
  if (!active || !payload?.length) return null;
  const lineas = extra?.((payload[0]?.payload ?? {}) as Record<string, number>) ?? [];
  return (
    <div className="rounded-[10px] border border-borde-panel bg-white px-3 py-2 shadow-flota text-[12.5px] grid gap-1">
      <b className="text-noche">{etiqueta ? etiqueta(String(label)) : label}</b>
      {payload.map((p) => (
        <span key={String(p.dataKey)} className="flex items-center gap-2 text-texto-suave">
          <i className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: p.color }} />
          {p.name}
          <b className="ml-auto pl-3 text-noche tnum">{formato(Number(p.value))}</b>
        </span>
      ))}
      {lineas.map(([k, v]) => (
        <span key={k} className="flex items-center gap-2 text-texto-suave">
          <i className="w-2.5 h-2.5 inline-block" />
          {k}
          <b className="ml-auto pl-3 text-noche tnum">{v}</b>
        </span>
      ))}
    </div>
  );
}

/** Tarjeta con la cabecera de los reportes (ícono + título; en celular la nota baja debajo del título). */
function Tarjeta({ titulo, icono: I, nota, children }: { titulo: string; icono: typeof Gauge; nota?: ReactNode; children: ReactNode }) {
  return (
    <section className="pc min-w-0 flex flex-col">
      <div className="pc-cab max-sm:flex-col max-sm:!items-start max-sm:gap-0.5">
        <h2>
          <I className="ico" aria-hidden />
          {titulo}
        </h2>
        {nota && <span className="pc-der t-sec text-[12.5px] max-sm:!ml-0 max-sm:pl-[27px]">{nota}</span>}
      </div>
      {children}
    </section>
  );
}

/**
 * `alto`: gráfico de 320 px; `altoPx`: alto MÍNIMO (p. ej. la barra única de Asistencia). Con `altoPx` el gráfico
 * llena la tarjeta cuando la fila es más alta (2 columnas) y mide `altoPx` cuando va sola (celular y tablet).
 */
function Grafico({ titulo, icono, children, nota, alto = false, altoPx }: { titulo: string; icono: typeof Gauge; children: ReactNode; nota?: ReactNode; alto?: boolean; altoPx?: number }) {
  return (
    <Tarjeta titulo={titulo} icono={icono} nota={nota}>
      {altoPx ? (
        <div className="pc-cuerpo relative flex-1" style={{ minHeight: altoPx }}>
          <div className="absolute inset-x-[18px] inset-y-4">{children}</div>
        </div>
      ) : (
        <div className={`pc-cuerpo ${alto ? 'grafico-alto' : 'grafico'}`}>{children}</div>
      )}
    </Tarjeta>
  );
}

/** /admin/reportes — reservas e ingresos por rango con gráficos (recharts) y descarga Excel. Solo ADMIN. */
export default function Reportes() {
  useTituloPagina('Reportes');
  const toast = useToast();
  const [rapido, setRapido] = useState('30');
  const [rango, setRango] = useState<Rango>(() => RAPIDOS[4]!.rango());
  const [borrador, setBorrador] = useState<Rango>(rango);
  const [errorRango, setErrorRango] = useState<string | null>(null);
  const [descargando, setDescargando] = useState(false);
  const clave = `${rango.desde}_${rango.hasta}`;
  const rep = useCarga(() => Promise.all([reportesApi.reservas(rango.desde, rango.hasta), reportesApi.ingresos(rango.desde, rango.hasta)]), clave);
  const [res, ing] = rep.datos ?? [null, null];

  const aplicar = (r: Rango, id = '') => {
    const e = validarRango(r);
    setErrorRango(e);
    if (e) return;
    setRapido(id);
    setBorrador(r);
    setRango(r);
  };
  const descargar = async () => {
    setDescargando(true);
    try {
      await descargarArchivo(reportesApi.rutaExcel(rango.desde, rango.hasta), `reservas-${rango.desde}-a-${rango.hasta}.xlsx`);
      toast.exito('Excel descargado');
    } catch {
      /* toast del interceptor */
    } finally {
      setDescargando(false);
    }
  };

  const porDia = useMemo(
    () =>
      (res?.porDia ?? []).map((d) => ({
        dia: d.fecha,
        Confirmadas: d.confirmadas,
        Pendientes: Math.max(0, d.registradas - d.confirmadas - d.canceladas),
        Canceladas: d.canceladas,
        Ingresos: ing?.porDia.find((x) => x.fecha === d.fecha)?.total ?? 0,
      })),
    [res, ing],
  );
  const porCancha = (res?.porCancha ?? []).map((c) => ({ cancha: (c.cancha.split(' · ')[0] ?? c.cancha).replace('Losa multiusos', 'Losa'), Reservas: c.reservas, Ocupación: c.ocupacionPct }));
  const porHora = (res?.porHora ?? []).map((h) => ({ hora: h.hora, Reservas: h.reservas }));
  const porMetodo = (ing?.porMetodo ?? []).map((m) => ({ metodo: m.texto, Total: m.total, Pagos: m.cantidad }));
  const porOrigen = (res?.porOrigen ?? []).map((o) => ({ origen: TEXTO_ORIGEN[o.origen] ?? o.origen, Reservas: o.reservas }));
  const asistencia = res ? [{ k: 'Partidos', Asistió: res.totales.asistidas, 'No asistió': res.totales.noAsistio }] : [];
  const t = res?.totales;
  const fDia = (f: string) => fmtFecha(f, 'dia-mes');
  const vacio = t && t.registradas === 0 && (ing?.cantidadPagos ?? 0) === 0;

  return (
    <>
      <CabeceraPagina
        titulo="Reportes"
        subtitulo="Reservas, ingresos, ocupación e inasistencias del periodo"
        busto="BU-rosa-pizarra"
        acciones={
          <Boton icono={Download} cargando={descargando} textoCargando="Descargando…" onClick={descargar}>
            Descargar Excel
          </Boton>
        }
      />

      <section className="card card-pad flex flex-wrap items-end gap-3">
        <div className="rango-rapido max-sm:w-full [&>button]:whitespace-nowrap [&>button]:max-sm:flex-auto" role="group" aria-label="Rango rápido">
          {RAPIDOS.map((r) => (
            <button key={r.id} type="button" aria-pressed={rapido === r.id} onClick={() => aplicar(r.rango(), r.id)}>
              {r.texto}
            </button>
          ))}
        </div>
        {/* Desde, Hasta y Aplicar son un solo bloque: se envuelven juntos (en celular, fechas lado a lado y Aplicar debajo). */}
        <div className="grid grid-cols-2 items-end gap-3 max-sm:w-full sm:flex sm:flex-wrap">
          <label className="grid gap-1 text-[12px] text-texto-suave min-w-0">
            Desde
            <input type="date" className="bb-fecha w-full min-w-0" value={borrador.desde} max={hoyLima()} onChange={(e) => setBorrador((b) => ({ ...b, desde: e.target.value }))} />
          </label>
          <label className="grid gap-1 text-[12px] text-texto-suave min-w-0">
            Hasta
            <input type="date" className="bb-fecha w-full min-w-0" value={borrador.hasta} onChange={(e) => setBorrador((b) => ({ ...b, hasta: e.target.value }))} />
          </label>
          <Boton variante="oscuro" icono={CalendarDays} className="max-sm:col-span-2" onClick={() => aplicar(borrador)}>
            Aplicar
          </Boton>
        </div>
        <span className="dato-ico ml-auto">
          <Info className="ico" aria-hidden />
          Según fecha del turno · hora de Lima
        </span>
        {errorRango && (
          <Alerta tipo="error" className="w-full">
            {errorRango}
          </Alerta>
        )}
      </section>

      {rep.error ? (
        <ErrorCarga alReintentar={rep.recargar} />
      ) : !res || !ing || !t ? (
        <div className="graficos">
          <Esqueleto alto={300} radio={14} />
          <Esqueleto alto={300} radio={14} />
        </div>
      ) : vacio ? (
        <div className="card">
          <Vacio ilustracion="IL-27-reporte-vacio" titulo="Sin datos en el periodo" texto={`${fmtFecha(rango.desde, 'dia-mes-anio')} – ${fmtFecha(rango.hasta, 'dia-mes-anio')}`} />
        </div>
      ) : (
        <>
          <div className="kpis">
            <TarjetaKpi etiqueta="Ingresos" valor={fmtSoles(ing.total, { decimales: 0 })} icono={Banknote} tono="verde" detalle={plural(ing.cantidadPagos, 'pago validado', 'pagos validados')} />
            <TarjetaKpi etiqueta="Ticket promedio" valor={fmtSoles(ing.ticketPromedio)} icono={Receipt} tono="noche" detalle={ing.reembolsado ? `${fmtSoles(ing.reembolsado)} reembolsado` : 'sin reembolsos'} />
            <TarjetaKpi etiqueta="Registradas" valor={t.registradas} icono={ClipboardList} tono="noche" detalle={plural(t.pendientes, 'pendiente', 'pendientes')} />
            <TarjetaKpi etiqueta="Confirmadas" valor={t.confirmadas} icono={CalendarCheck} tono="verde" barra={t.registradas ? (t.confirmadas / t.registradas) * 100 : 0} detalle={`${t.registradas ? Math.round((t.confirmadas / t.registradas) * 100) : 0} % de ${t.registradas}`} />
            <TarjetaKpi etiqueta="Canceladas" valor={t.canceladas} icono={CircleX} tono="rojo" detalle={Object.entries(res.cancelaciones.porActor).map(([k, v]) => `${v} ${(TEXTO_ACTOR[k] ?? k).split(' ')[0]?.toLowerCase()}`).join(' · ')} />
            <TarjetaKpi etiqueta="No asistió" valor={`${t.tasaNoShowPct} %`} icono={UserX} tono="rojo" detalle={`${t.noAsistio} de ${plural(t.asistidas + t.noAsistio, 'partido', 'partidos')}`} />
            <TarjetaKpi etiqueta="Ocupación" valor={`${t.ocupacionPct} %`} icono={Gauge} tono="gris" barra={t.ocupacionPct} detalle="turnos reservados" />
            <TarjetaKpi etiqueta="Validación" valor={ing.tiempoMedioValidacionMin != null ? `${Math.round(ing.tiempoMedioValidacionMin)} min` : '—'} icono={Timer} tono="gol" detalle={plural(ing.pagosRechazados, 'rechazado', 'rechazados')} />
          </div>

          <div className="graficos">
            <Grafico titulo="Reservas por día" icono={CalendarDays} alto nota={`${fmtFecha(rango.desde, 'dia-mes')} – ${fmtFecha(rango.hasta, 'dia-mes')}`}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porDia} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#EEF2F0" />
                  <XAxis dataKey="dia" tickFormatter={fDia} tick={EJE} tickLine={false} axisLine={{ stroke: '#E3EAE6' }} minTickGap={18} />
                  <YAxis allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo etiqueta={(l) => fmtFecha(l, 'corta')} />} />
                  <Legend iconType="square" iconSize={10} wrapperStyle={{ fontSize: 12.5 }} formatter={(v: string) => <span style={{ color: '#091A2A' }}>{v}</span>} />
                  <Bar dataKey="Confirmadas" stackId="d" fill={C.verde} stroke="#fff" strokeWidth={1} />
                  <Bar dataKey="Pendientes" stackId="d" fill={C.ambar} stroke="#fff" strokeWidth={1} />
                  <Bar dataKey="Canceladas" stackId="d" fill={C.magenta} stroke="#fff" strokeWidth={1} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Ingresos por día" icono={Banknote} alto nota={fmtSoles(ing.total)}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porDia} margin={{ top: 4, right: 4, left: -6, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#EEF2F0" />
                  <XAxis dataKey="dia" tickFormatter={fDia} tick={EJE} tickLine={false} axisLine={{ stroke: '#E3EAE6' }} minTickGap={18} />
                  <YAxis tick={EJE} tickLine={false} axisLine={false} tickFormatter={(v: number) => `S/ ${v}`} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => fmtSoles(v)} etiqueta={(l) => fmtFecha(l, 'corta')} />} />
                  <Bar dataKey="Ingresos" fill={C.verde} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Por cancha" icono={ClipboardList} nota="reservas · toca una barra para ver la ocupación">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porCancha} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }}>
                  <CartesianGrid horizontal={false} stroke="#EEF2F0" />
                  <XAxis type="number" allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="cancha" tick={EJE} tickLine={false} axisLine={false} width={96} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => String(v)} extra={(f) => [['Ocupación', `${f.Ocupación ?? 0} %`]]} />} />
                  <Bar dataKey="Reservas" fill={C.verde} radius={[0, 4, 4, 0]} barSize={22} label={etiquetaDer(String)} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Ocupación por cancha" icono={Gauge} nota="% de turnos reservados">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porCancha} layout="vertical" margin={{ top: 0, right: 40, left: 8, bottom: 0 }}>
                  <CartesianGrid horizontal={false} stroke="#EEF2F0" />
                  <XAxis type="number" tick={EJE} tickLine={false} axisLine={false} unit=" %" />
                  <YAxis type="category" dataKey="cancha" tick={EJE} tickLine={false} axisLine={false} width={96} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => `${v} %`} />} />
                  <Bar dataKey="Ocupación" fill={C.azul} radius={[0, 4, 4, 0]} barSize={22} label={etiquetaDer((v) => `${v} %`)} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Por hora" icono={Timer} nota="hora de inicio del turno">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porHora} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#EEF2F0" />
                  <XAxis dataKey="hora" tick={EJE} tickLine={false} axisLine={{ stroke: '#E3EAE6' }} interval="preserveStartEnd" />
                  <YAxis allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo />} />
                  <Bar dataKey="Reservas" fill={C.verde} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Por método de pago" icono={Banknote} nota="soles validados">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porMetodo} layout="vertical" margin={{ top: 0, right: 64, left: 8, bottom: 0 }}>
                  <CartesianGrid horizontal={false} stroke="#EEF2F0" />
                  <XAxis type="number" tick={EJE} tickLine={false} axisLine={false} tickFormatter={(v: number) => `S/ ${v}`} />
                  <YAxis type="category" dataKey="metodo" tick={EJE} tickLine={false} axisLine={false} width={96} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => fmtSoles(v)} />} />
                  <Bar dataKey="Total" fill={C.verde} radius={[0, 4, 4, 0]} barSize={20} label={etiquetaDer((v) => `S/ ${Math.round(v)}`)} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Por origen" icono={ClipboardList}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porOrigen} margin={{ top: 16, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#EEF2F0" />
                  <XAxis dataKey="origen" tick={EJE} tickLine={false} axisLine={{ stroke: '#E3EAE6' }} />
                  <YAxis allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo />} />
                  <Bar dataKey="Reservas" fill={C.verde} radius={[4, 4, 0, 0]} barSize={48} label={etiquetaArriba(String)} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Asistencia (no-show)" icono={UserX} nota={`${t.tasaNoShowPct} % no asistió`} altoPx={136}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={asistencia} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }} barSize={36}>
                  <XAxis type="number" allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="k" hide />
                  <Tooltip cursor={false} content={<Globo />} />
                  <Legend iconType="square" iconSize={10} wrapperStyle={{ fontSize: 12.5 }} formatter={(v: string) => <span style={{ color: '#091A2A' }}>{v}</span>} />
                  <Bar dataKey="Asistió" stackId="a" fill={C.verde} stroke="#fff" strokeWidth={2}>
                    <LabelList dataKey="Asistió" content={etiquetaCentro} />
                  </Bar>
                  <Bar dataKey="No asistió" stackId="a" fill={C.magenta} stroke="#fff" strokeWidth={2} radius={[0, 4, 4, 0]}>
                    <LabelList dataKey="No asistió" content={etiquetaCentro} />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
          </div>

          <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
            <Tarjeta titulo="Mejores clientes" icono={Trophy} nota="por reservas">
              {res.topClientes.length ? (
                <ol className="m-0 p-0 list-none divide-y divide-borde-panel">
                  {res.topClientes.map((c, i) => (
                    <li key={c.dni} className="flex items-center gap-3 px-[18px] py-3">
                      <span className="grid place-items-center w-7 h-7 rounded-full bg-menta text-verde text-[12.5px] font-bold tnum shrink-0">{i + 1}</span>
                      <span className="grid min-w-0 flex-1">
                        <b className="line-clamp-2 [overflow-wrap:anywhere] text-noche">{c.nombre}</b>
                        <span className="t-sec text-[12.5px] tnum">DNI {c.dni}</span>
                      </span>
                      <span className="grid text-right shrink-0">
                        <b className="tnum text-noche">{plural(c.reservas, 'reserva', 'reservas')}</b>
                        <span className="t-sec text-[12.5px] tnum">asistió a {c.asistidas}</span>
                      </span>
                    </li>
                  ))}
                </ol>
              ) : (
                <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin clientes en el periodo" compacto />
              )}
            </Tarjeta>
            <Tarjeta titulo="Motivos de cancelación" icono={CircleX} nota={plural(t.canceladas, 'cancelada', 'canceladas')}>
              {res.cancelaciones.motivosFrecuentes.length ? (
                <ul className="m-0 p-0 list-none divide-y divide-borde-panel">
                  {res.cancelaciones.motivosFrecuentes.map((m) => (
                    <li key={m.motivo} className="flex items-center gap-3 px-[18px] py-3">
                      <span className="min-w-0 flex-1 text-noche">{m.motivo}</span>
                      <b className="tnum text-noche shrink-0">{plural(m.veces, 'vez', 'veces')}</b>
                    </li>
                  ))}
                </ul>
              ) : (
                <Vacio ilustracion="IL-23-todo-al-dia" titulo="Sin cancelaciones" compacto />
              )}
            </Tarjeta>
          </div>
        </>
      )}
    </>
  );
}
