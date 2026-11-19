import { useEffect, useState } from 'react';
import { Activity, CircleCheck, CircleX, Gauge, Network, RefreshCw, Server, ShieldCheck } from 'lucide-react';
import { estadoApi } from '@/api/admin';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { ErrorCarga, Estado } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { Interruptor } from '@/componentes/ui/Campo';
import { Esqueleto, TarjetaKpi } from '@/componentes/ui/Datos';
import { fmtFechaHora } from '@/utilidades/fecha';
import { cx } from '@/utilidades/cx';

const DESCRIPCION: Record<string, string> = {
  'discovery-server': 'Registro de servicios (Eureka)',
  'api-gateway': 'Puerta de entrada, JWT y límites',
  'auth-service': 'Usuarios, login y bitácora',
  'catalogo-service': 'Canchas, fotos, reseñas y configuración',
  'reservas-service': 'Turnos, reservas, clientes y reportes',
  'pagos-service': 'Pagos, pasarela y comprobantes',
  'notificaciones-service': 'Correo, WhatsApp y recordatorios',
};

/** /admin/estado — salud de los microservicios (GET /api/estado del gateway) con refresco automático. Solo ADMIN. */
export default function EstadoSistema() {
  useTituloPagina('Estado del sistema');
  const [version, setVersion] = useState(0);
  const [auto, setAuto] = useState(true);
  const est = useCarga(() => estadoApi.obtener(), `estado-${version}`);
  useEffect(() => {
    if (!auto) return undefined;
    const t = window.setInterval(() => setVersion((v) => v + 1), 15000);
    return () => window.clearInterval(t);
  }, [auto]);
  const s = est.datos?.servicios ?? [];
  const arriba = s.filter((x) => x.estado === 'UP').length;
  const lat = s.filter((x) => x.latenciaMs != null).map((x) => x.latenciaMs ?? 0);
  const prom = lat.length ? Math.round(lat.reduce((a, b) => a + b, 0) / lat.length) : 0;
  const max = Math.max(50, ...lat);

  return (
    <>
      <CabeceraPagina
        titulo="Estado del sistema"
        subtitulo="Microservicios, instancias y latencia"
        busto="BU-lucho-silbato"
        acciones={
          <>
            <Interruptor activo={auto} alCambiar={setAuto}>
              Cada 15 s
            </Interruptor>
            <Boton variante="secundario" icono={RefreshCw} cargando={est.cargando} onClick={() => setVersion((v) => v + 1)}>
              Actualizar
            </Boton>
          </>
        }
      />
      {est.error && !est.datos ? (
        <ErrorCarga alReintentar={est.recargar} titulo="El gateway no responde" />
      ) : (
        <>
          <div className="kpis">
            <TarjetaKpi etiqueta="En línea" valor={est.datos ? `${arriba}/${s.length}` : '—'} icono={arriba === s.length ? CircleCheck : CircleX} tono={arriba === s.length ? 'verde' : 'rojo'} detalle="servicios" />
            <TarjetaKpi etiqueta="Latencia media" valor={est.datos ? `${prom} ms` : '—'} icono={Gauge} tono="noche" detalle="/actuator/health" />
            <TarjetaKpi etiqueta="Instancias" valor={s.reduce((a, b) => a + b.instancias, 0)} icono={Network} tono="gris" detalle="registradas en Eureka" />
            <TarjetaKpi etiqueta="Revisado" valor={est.datos ? est.datos.generadoEn.slice(11, 19) : '—'} icono={Activity} tono="gris" detalle={est.datos ? fmtFechaHora(est.datos.generadoEn, { h24: true }).split(',')[0] : ''} />
          </div>
          <div className="servicios">
            {!est.datos
              ? Array.from({ length: 7 }, (_, i) => <Esqueleto key={i} alto={130} radio={14} />)
              : s.map((x) => (
                  <article key={x.nombre} className={cx('servicio', x.estado !== 'UP' && 'caido')}>
                    <div className="flex items-center gap-2">
                      <span className="td-ico">{x.nombre === 'api-gateway' ? <ShieldCheck className="ico" aria-hidden /> : <Server className="ico" aria-hidden />}</span>
                      <b className="truncar">{x.nombre}</b>
                      <span className="ml-auto">
                        <Estado dominio="servicio" estado={x.estado === 'UP' ? 'UP' : 'DOWN'} tamano="sm" />
                      </span>
                    </div>
                    <span className="t-sec text-[12.5px]">{DESCRIPCION[x.nombre] ?? ''}</span>
                    <div className="flex items-end justify-between">
                      <span className="lat">
                        {x.latenciaMs ?? '—'}
                        <small>ms</small>
                      </span>
                      <span className="t-sec text-[12px]">
                        {x.instancias} {x.instancias === 1 ? 'instancia' : 'instancias'}
                      </span>
                    </div>
                    <div className="barra-lat" aria-hidden>
                      <i style={{ width: `${Math.min(100, ((x.latenciaMs ?? 0) / max) * 100)}%`, background: x.estado !== 'UP' ? 'var(--rojo)' : (x.latenciaMs ?? 0) > 500 ? 'var(--amarillo)' : undefined }} />
                    </div>
                  </article>
                ))}
          </div>
        </>
      )}
    </>
  );
}
