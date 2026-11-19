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
import { Esqueleto, Tabla, TarjetaKpi, Vacio } from '@/componentes/ui/Datos';
import { Alerta } from '@/componentes/ui/Estados';
import { ahoraLima, fmtFecha, hoyLima, sumarDias } from '@/utilidades/fecha';
import { fmtSoles } from '@/utilidades/formato';
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
function Globo({ active, payload, label, formato = (v) => String(v), etiqueta }: TooltipProps<number, string> & { formato?: (v: number) => string; etiqueta?: (l: string) => string }) {
  if (!active || !payload?.length) return null;
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
    </div>
  );
}

function Grafico({ titulo, icono: I, children, nota, alto = false }: { titulo: string; icono: typeof Gauge; children: ReactNode; nota?: ReactNode; alto?: boolean }) {
  return (
    <section className="pc">
      <div className="pc-cab">
        <h2>
          <I className="ico" aria-hidden />
          {titulo}
        </h2>
        {nota && <span className="pc-der t-sec text-[12.5px]">{nota}</span>}
      </div>
      <div className={`pc-cuerpo ${alto ? 'grafico-alto' : 'grafico'}`}>{children}</div>
    </section>
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
        <div className="rango-rapido" role="group" aria-label="Rango rápido">
          {RAPIDOS.map((r) => (
            <button key={r.id} type="button" aria-pressed={rapido === r.id} onClick={() => aplicar(r.rango(), r.id)}>
              {r.texto}
            </button>
          ))}
        </div>
        <label className="grid gap-1 text-[12px] text-texto-suave">
          Desde
          <input type="date" className="bb-fecha" value={borrador.desde} max={hoyLima()} onChange={(e) => setBorrador((b) => ({ ...b, desde: e.target.value }))} />
        </label>
        <label className="grid gap-1 text-[12px] text-texto-suave">
          Hasta
          <input type="date" className="bb-fecha" value={borrador.hasta} onChange={(e) => setBorrador((b) => ({ ...b, hasta: e.target.value }))} />
        </label>
        <Boton variante="oscuro" icono={CalendarDays} onClick={() => aplicar(borrador)}>
          Aplicar
        </Boton>
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
            <TarjetaKpi etiqueta="Ingresos" valor={fmtSoles(ing.total, { decimales: 0 })} icono={Banknote} tono="verde" detalle={`${ing.cantidadPagos} pagos validados`} />
            <TarjetaKpi etiqueta="Ticket promedio" valor={fmtSoles(ing.ticketPromedio)} icono={Receipt} tono="noche" detalle={ing.reembolsado ? `${fmtSoles(ing.reembolsado)} reembolsado` : 'sin reembolsos'} />
            <TarjetaKpi etiqueta="Registradas" valor={t.registradas} icono={ClipboardList} tono="noche" detalle={`${t.pendientes} pendientes`} />
            <TarjetaKpi etiqueta="Confirmadas" valor={t.confirmadas} icono={CalendarCheck} tono="verde" barra={t.registradas ? (t.confirmadas / t.registradas) * 100 : 0} detalle={`${t.registradas ? Math.round((t.confirmadas / t.registradas) * 100) : 0} % de ${t.registradas}`} />
            <TarjetaKpi etiqueta="Canceladas" valor={t.canceladas} icono={CircleX} tono="rojo" detalle={Object.entries(res.cancelaciones.porActor).map(([k, v]) => `${v} ${(TEXTO_ACTOR[k] ?? k).split(' ')[0]?.toLowerCase()}`).join(' · ')} />
            <TarjetaKpi etiqueta="No asistió" valor={`${t.tasaNoShowPct} %`} icono={UserX} tono="rojo" detalle={`${t.noAsistio} de ${t.asistidas + t.noAsistio} partidos`} />
            <TarjetaKpi etiqueta="Ocupación" valor={`${t.ocupacionPct} %`} icono={Gauge} tono="gris" barra={t.ocupacionPct} detalle="turnos reservados" />
            <TarjetaKpi etiqueta="Validación" valor={ing.tiempoMedioValidacionMin != null ? `${Math.round(ing.tiempoMedioValidacionMin)} min` : '—'} icono={Timer} tono="gol" detalle={`${ing.pagosRechazados} rechazados`} />
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
            <Grafico titulo="Por cancha" icono={ClipboardList} nota="reservas · ocupación en el globo">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porCancha} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }}>
                  <CartesianGrid horizontal={false} stroke="#EEF2F0" />
                  <XAxis type="number" allowDecimals={false} tick={EJE} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="cancha" tick={EJE} tickLine={false} axisLine={false} width={96} />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => String(v)} />} />
                  <Bar dataKey="Reservas" fill={C.verde} radius={[0, 4, 4, 0]} barSize={22} label={etiquetaDer(String)} />
                </BarChart>
              </ResponsiveContainer>
            </Grafico>
            <Grafico titulo="Ocupación por cancha" icono={Gauge} nota="% de turnos reservados">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={porCancha} margin={{ top: 16, right: 4, left: -18, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#EEF2F0" />
                  <XAxis dataKey="cancha" tick={EJE} tickLine={false} axisLine={{ stroke: '#E3EAE6' }} interval={0} />
                  <YAxis tick={EJE} tickLine={false} axisLine={false} unit=" %" />
                  <Tooltip cursor={{ fill: 'rgba(9,26,42,.05)' }} content={<Globo formato={(v) => `${v} %`} />} />
                  <Bar dataKey="Ocupación" fill={C.azul} radius={[4, 4, 0, 0]} barSize={36} label={etiquetaArriba((v) => `${v} %`)} />
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
            <Grafico titulo="Asistencia (no-show)" icono={UserX} nota={`${t.tasaNoShowPct} % no asistió`}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={asistencia} layout="vertical" margin={{ top: 24, right: 16, left: 8, bottom: 0 }} barSize={40}>
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
            <section className="grid gap-2">
              <h2 className="t-h3">Mejores clientes</h2>
              <Tabla
                etiqueta="Mejores clientes"
                filas={res.topClientes}
                claveFila={(c) => c.dni}
                vacio="Sin datos"
                columnas={[
                  { clave: 'nombre', titulo: 'Cliente', celda: (c) => <b>{c.nombre}</b> },
                  { clave: 'dni', titulo: 'DNI', celda: (c) => <span className="tnum">{c.dni}</span> },
                  { clave: 'reservas', titulo: 'Reservas', alinear: 'der', celda: (c) => <span className="tnum">{c.reservas}</span> },
                  { clave: 'asistidas', titulo: 'Asistió', alinear: 'der', celda: (c) => <span className="tnum">{c.asistidas}</span> },
                ]}
              />
            </section>
            <section className="grid gap-2">
              <h2 className="t-h3">Motivos de cancelación</h2>
              <Tabla
                etiqueta="Motivos de cancelación"
                filas={res.cancelaciones.motivosFrecuentes}
                claveFila={(m) => m.motivo}
                vacio="Sin cancelaciones"
                columnas={[
                  { clave: 'motivo', titulo: 'Motivo', celda: (m) => m.motivo },
                  { clave: 'veces', titulo: 'Veces', alinear: 'der', celda: (m) => <b className="tnum">{m.veces}</b> },
                ]}
              />
            </section>
          </div>
        </>
      )}
    </>
  );
}
