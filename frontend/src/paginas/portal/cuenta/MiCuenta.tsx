import { useMemo, useState, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { CalendarDays, CalendarPlus, ChevronRight, Clock, KeyRound, LogOut, Mail, Save, Smartphone, Star, Ticket, UserRound } from 'lucide-react';
import { cuentaApi } from '@/api/servicios';
import type { ReservaDeCliente } from '@/api/tipos';
import { esErrorApi } from '@/api/cliente';
import { useCuenta } from '@/contexto/CuentaContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { Boton, BotonEnlace } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Avatar, Cargando, Pestanas, Segmentado, Vacio } from '@/componentes/ui/Datos';
import { Alerta, Insignia, type TonoInsignia } from '@/componentes/ui/Estados';
import { IconoDeporte } from '@/componentes/ui/Marca';
import { estadoCliente, horarioDeReserva, type ClaveCliente } from '@/componentes/portal/EstadoReserva';
import { esquemaCambioClaveCliente, esquemaDatosCuenta, type CambioClaveClienteEntrada, type DatosCuentaEntrada } from '@/esquemas/otros';
import { fmtFecha, hoyLima, numeroDia } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import { normalizarCelular } from '@/utilidades/documentos';

type Seccion = 'reservas' | 'resenas' | 'datos';
const ESTADO: Record<ClaveCliente, { texto: string; tono: TonoInsignia }> = {
  FALTA_PAGO: { texto: 'Falta pagar', tono: 'amarillo' },
  EN_REVISION: { texto: 'En revisión', tono: 'amarillo' },
  OBSERVADO: { texto: 'Revisa tu pago', tono: 'rojo-borde' },
  CONFIRMADA: { texto: 'Confirmada', tono: 'verde' },
  EN_JUEGO: { texto: 'En juego', tono: 'verde' },
  JUGADA: { texto: 'Jugada', tono: 'menta' },
  NO_ASISTIO: { texto: 'No asististe', tono: 'gris' },
  FINALIZADA: { texto: 'Jugada', tono: 'menta' },
  CANCELADA: { texto: 'Cancelada', tono: 'gris' },
  NO_CONFIRMADA: { texto: 'No confirmada', tono: 'gris' },
};

/** /mi-cuenta[/resenas|/datos] — portal del cliente con sesión (SPEC_V2 §E). Mis reservas = todas las de su DNI. */
export default function MiCuenta() {
  useTituloPagina('Mi cuenta');
  const { cliente, salir } = useCuenta();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const sub = (useParams()['*'] ?? '').replace(/\/$/, '');
  const seccion: Seccion = sub === 'resenas' ? 'resenas' : sub === 'datos' ? 'datos' : 'reservas';
  const reservas = useCarga(() => cuentaApi.reservas({ size: 100 }), cliente ? `mis-reservas-${cliente.dni}` : null);
  if (!cliente) return <Navigate to={`/ingresar?volver=${encodeURIComponent(pathname)}`} replace />;
  const lista = reservas.datos?.contenido ?? [];
  const porCalificar = lista.filter((r) => r.puedeResenar && !r.resenada);

  return (
    <div className="contenedor grid gap-5 py-6 lg:py-10">
      <div className="cuenta-cab">
        <Avatar nombre={`${cliente.nombres} ${cliente.apellidos}`} tamano="lg" />
        <div className="grid gap-0.5 min-w-0">
          <h1 className="t-h1 truncar">{`Hola, ${cliente.nombres.split(' ')[0]}`}</h1>
          <span className="t-sec">{reservas.datos ? plural(reservas.datos.totalElementos, 'reserva', 'reservas') : 'Tu cuenta'}</span>
        </div>
        <div className="flex gap-2 ml-auto">
          <BotonEnlace to="/reservar" icono={CalendarPlus} className="solo-tablet-arriba">
            Reservar
          </BotonEnlace>
          <Boton
            variante="fantasma"
            icono={LogOut}
            etiqueta="Cerrar sesión"
            onClick={() => {
              salir();
              navigate('/', { replace: true });
            }}
          />
        </div>
      </div>
      <Pestanas
        etiqueta="Mi cuenta"
        activa={seccion}
        alCambiar={(s) => navigate(s === 'reservas' ? '/mi-cuenta' : `/mi-cuenta/${s}`)}
        items={[
          { valor: 'reservas', texto: 'Mis reservas', icono: Ticket },
          { valor: 'resenas', texto: 'Mis reseñas', icono: Star, cuenta: porCalificar.length || undefined, alerta: porCalificar.length > 0 },
          { valor: 'datos', texto: 'Mis datos', icono: UserRound },
        ]}
      />
      {seccion === 'datos' ? (
        <MisDatos />
      ) : reservas.error ? (
        <Vacio ilustracion="IL-19-sin-conexion" titulo="No pudimos cargar tus reservas" acciones={<Boton variante="secundario" onClick={reservas.recargar}>Reintentar</Boton>} />
      ) : !reservas.datos ? (
        <Cargando texto="Cargando tus reservas…" />
      ) : seccion === 'reservas' ? (
        <MisReservas lista={lista} dni={cliente.dni} />
      ) : (
        <MisResenas lista={lista} />
      )}
    </div>
  );
}

function FilaReserva({ r, dni, accion }: { r: ReservaDeCliente; dni: string; accion?: ReactNode }) {
  const e = ESTADO[estadoCliente(r)];
  return (
    <li className="mr-fila">
      <span className="mr-fecha">
        <small>{fmtFecha(r.fecha, 'corta').split(' ')[0]}</small>
        <b className="tnum">{numeroDia(r.fecha)}</b>
        <small>{fmtFecha(r.fecha, 'dia-mes').split(' ')[1]}</small>
      </span>
      <span className="grid gap-1 min-w-0 flex-1">
        <b className="truncar inline-flex items-center gap-1.5">
          <IconoDeporte deporte={r.cancha.deporte} className="ico ico-16 text-verde" />
          {r.cancha.nombre}
        </b>
        <span className="flex flex-wrap gap-x-3 gap-y-1 t-sec text-[13px]">
          <span className="inline-flex items-center gap-1 tnum">
            <Clock className="ico ico-14" aria-hidden />
            {horarioDeReserva(r)}
          </span>
          <span className="tnum">{fmtSoles(r.montoTotal)}</span>
          <span className="tnum">{r.codigoReserva}</span>
        </span>
      </span>
      <Insignia tono={e.tono} tamano="sm" className="shrink-0">
        {e.texto}
      </Insignia>
      {accion ?? (
        <Link to={`/mi-reserva?codigo=${r.codigoReserva}`} state={{ dni }} className="mr-ir" aria-label={`Ver la reserva ${r.codigoReserva}`}>
          <ChevronRight className="ico" aria-hidden />
        </Link>
      )}
    </li>
  );
}

function MisReservas({ lista, dni }: { lista: ReservaDeCliente[]; dni: string }) {
  const [filtro, setFiltro] = useState<'proximas' | 'pasadas' | 'canceladas' | 'todas'>('proximas');
  const hoy = hoyLima();
  const filtradas = useMemo(() => {
    const orden = [...lista].sort((a, b) => `${b.fecha}${b.horaInicio}`.localeCompare(`${a.fecha}${a.horaInicio}`));
    if (filtro === 'proximas') return orden.filter((r) => r.estado !== 'CANCELADA' && r.fecha >= hoy).reverse();
    if (filtro === 'pasadas') return orden.filter((r) => r.estado !== 'CANCELADA' && r.fecha < hoy);
    if (filtro === 'canceladas') return orden.filter((r) => r.estado === 'CANCELADA');
    return orden;
  }, [lista, filtro, hoy]);
  return (
    <section className="grid gap-4">
      <Segmentado
        etiqueta="Mostrar"
        className="max-w-[560px]"
        bloque
        pequeno
        valor={filtro}
        alCambiar={setFiltro}
        opciones={[
          { valor: 'proximas', texto: 'Próximas' },
          { valor: 'pasadas', texto: 'Pasadas' },
          { valor: 'canceladas', texto: 'Canceladas' },
          { valor: 'todas', texto: 'Todas' },
        ]}
      />
      {filtradas.length === 0 ? (
        <div className="card">
          <Vacio
            ilustracion={filtro === 'proximas' ? 'IL-30-hoy-sin-reservas' : 'IL-24-sin-resultados'}
            titulo={filtro === 'proximas' ? 'Sin reservas próximas' : 'Nada por aquí'}
            acciones={
              filtro === 'proximas' ? (
                <BotonEnlace to="/reservar" icono={CalendarPlus}>
                  Reservar
                </BotonEnlace>
              ) : undefined
            }
            compacto
          />
        </div>
      ) : (
        <ul className="mr-lista card">
          {filtradas.map((r) => (
            <FilaReserva key={r.codigoReserva} r={r} dni={dni} />
          ))}
        </ul>
      )}
      <p className="t-mini inline-flex items-center gap-1.5">
        <CalendarDays className="ico ico-14" aria-hidden />
        Incluye las reservas que hiciste con tu DNI antes de crear la cuenta.
      </p>
    </section>
  );
}

function MisResenas({ lista }: { lista: ReservaDeCliente[] }) {
  const por = lista.filter((r) => r.puedeResenar && !r.resenada);
  const hechas = lista.filter((r) => r.resenada);
  return (
    <section className="grid gap-5">
      <div className="grid gap-2">
        <h2 className="t-h3">Por calificar</h2>
        {por.length === 0 ? (
          <div className="card">
            <Vacio ilustracion="IL-15-partido-jugado" titulo="Nada por calificar" texto="Después de jugar, aquí podrás calificar la cancha." compacto />
          </div>
        ) : (
          <ul className="mr-lista card">
            {por.map((r) => (
              <FilaReserva
                key={r.codigoReserva}
                r={r}
                dni=""
                accion={
                  <BotonEnlace to={`/canchas/${r.cancha.id}?resena=${r.codigoReserva}`} tamano="sm" icono={Star}>
                    Calificar
                  </BotonEnlace>
                }
              />
            ))}
          </ul>
        )}
      </div>
      {hechas.length > 0 && (
        <div className="grid gap-2">
          <h2 className="t-h3">Ya calificadas</h2>
          <ul className="mr-lista card">
            {hechas.map((r) => (
              <FilaReserva
                key={r.codigoReserva}
                r={r}
                dni=""
                accion={
                  <Link to={`/canchas/${r.cancha.id}#h-resenas`} className="enlace text-[13px] shrink-0">
                    Ver reseñas
                  </Link>
                }
              />
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function MisDatos() {
  const { cliente, actualizar } = useCuenta();
  const toast = useToast();
  const [errorDatos, setErrorDatos] = useState<string | null>(null);
  const [errorClave, setErrorClave] = useState<string | null>(null);
  const datos = useForm<DatosCuentaEntrada>({ resolver: zodResolver(esquemaDatosCuenta), mode: 'onTouched', defaultValues: { telefono: cliente?.telefono ?? '', correo: cliente?.correo ?? '' } });
  const clave = useForm<CambioClaveClienteEntrada>({ resolver: zodResolver(esquemaCambioClaveCliente), mode: 'onTouched', defaultValues: { actual: '', nueva: '', confirmacion: '' } });
  if (!cliente) return null;

  const guardarDatos = datos.handleSubmit(async (d) => {
    setErrorDatos(null);
    try {
      const r = await cuentaApi.editar({ nombres: cliente.nombres, apellidos: cliente.apellidos, telefono: normalizarCelular(String(d.telefono)), correo: d.correo.trim() });
      actualizar(r);
      datos.reset({ telefono: r.telefono, correo: r.correo });
      toast.exito('Datos guardados');
    } catch (e) {
      if (esErrorApi(e)) {
        let n = 0;
        for (const x of e.errores)
          if (x.campo === 'telefono' || x.campo === 'correo') {
            datos.setError(x.campo, { message: x.mensaje });
            n += 1;
          }
        if (e.codigo === 'CORREO_REGISTRADO') datos.setError('correo', { message: e.message });
        else if (!n) setErrorDatos(e.message);
      } else setErrorDatos('No se pudo guardar.');
    }
  });
  const cambiarClave = clave.handleSubmit(async (d) => {
    setErrorClave(null);
    try {
      await cuentaApi.cambiarContrasena({ actual: d.actual, nueva: d.nueva, confirmacion: d.confirmacion });
      clave.reset();
      toast.exito('Contraseña cambiada');
    } catch (e) {
      if (esErrorApi(e)) {
        let n = 0;
        for (const x of e.errores)
          if (x.campo === 'actual' || x.campo === 'nueva' || x.campo === 'confirmacion') {
            clave.setError(x.campo, { message: x.mensaje });
            n += 1;
          }
        if (!n) setErrorClave(e.message);
      } else setErrorClave('No se pudo cambiar.');
    }
  });

  return (
    <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
      <form className="card card-pad grid gap-4" onSubmit={guardarDatos} noValidate>
        <h2 className="t-h3">Tus datos</h2>
        <div className="form-grid dos">
          <CampoTexto label="DNI" lectura value={cliente.dni} readOnly />
          <CampoTexto label="Nombre" lectura value={`${cliente.nombres} ${cliente.apellidos}`} readOnly />
          <CampoTexto label="Celular" requerido prefijo={<><Smartphone className="ico ico-16" aria-hidden />+51</>} inputMode="tel" maxLength={15} error={datos.formState.errors.telefono?.message} {...datos.register('telefono')} />
          <CampoTexto label="Correo" requerido icono={Mail} type="email" maxLength={100} error={datos.formState.errors.correo?.message} {...datos.register('correo')} />
        </div>
        {errorDatos && <Alerta tipo="error">{errorDatos}</Alerta>}
        <div>
          <Boton type="submit" icono={Save} disabled={!datos.formState.isDirty} cargando={datos.formState.isSubmitting} textoCargando="Guardando…">
            Guardar
          </Boton>
        </div>
      </form>
      <form className="card card-pad grid gap-4" onSubmit={cambiarClave} noValidate>
        <h2 className="t-h3">Contraseña</h2>
        <CampoTexto label="Contraseña actual" requerido icono={KeyRound} type="password" autoComplete="current-password" maxLength={64} error={clave.formState.errors.actual?.message} {...clave.register('actual')} />
        <div className="form-grid dos">
          <CampoTexto label="Nueva" requerido type="password" autoComplete="new-password" maxLength={64} error={clave.formState.errors.nueva?.message} {...clave.register('nueva')} />
          <CampoTexto label="Repite la nueva" requerido type="password" autoComplete="new-password" maxLength={64} error={clave.formState.errors.confirmacion?.message} {...clave.register('confirmacion')} />
        </div>
        {errorClave && <Alerta tipo="error">{errorClave}</Alerta>}
        <div>
          <Boton type="submit" variante="secundario" icono={KeyRound} cargando={clave.formState.isSubmitting} textoCargando="Cambiando…">
            Cambiar contraseña
          </Boton>
        </div>
      </form>
    </div>
  );
}
