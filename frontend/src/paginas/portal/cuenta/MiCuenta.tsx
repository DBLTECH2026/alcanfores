import { useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { BadgeCheck, CalendarDays, CalendarPlus, ChevronRight, Clock, Eye, KeyRound, LogOut, Mail, Save, Smartphone, Star, Ticket, UserRound } from 'lucide-react';
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
import { ReglasClave, errorClave } from '@/componentes/cuenta/ReglasClave';
import { esquemaCambioClaveCliente, esquemaDatosCuenta, type CambioClaveClienteEntrada, type DatosCuentaEntrada } from '@/esquemas/otros';
import { fmtFecha, hoyLima, numeroDia } from '@/utilidades/fecha';
import { fmtSoles, plural } from '@/utilidades/formato';
import { normalizarCelular } from '@/utilidades/documentos';
import type { IdIlustracion } from '@/utilidades/recursos';

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
  const toast = useToast();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  // Al cerrar sesión el componente se vuelve a pintar sin cliente: debe ir al inicio, no a «Ingresar».
  const saliendo = useRef(false);
  const sub = (useParams()['*'] ?? '').replace(/\/$/, '');
  const seccion: Seccion = sub === 'resenas' ? 'resenas' : sub === 'datos' ? 'datos' : 'reservas';
  const reservas = useCarga(() => cuentaApi.reservas({ size: 100 }), cliente ? `mis-reservas-${cliente.dni}` : null);
  if (!cliente) return saliendo.current ? <Navigate to="/" replace /> : <Navigate to={`/ingresar?volver=${encodeURIComponent(pathname)}`} replace />;
  const lista = reservas.datos?.contenido ?? [];
  const porCalificar = lista.filter((r) => r.puedeResenar && !r.resenada);
  function cerrarSesion() {
    saliendo.current = true;
    salir();
    toast.info('Cerraste sesión');
  }

  return (
    <div className="contenedor grid gap-5 py-6 lg:py-10">
      <div className="cuenta-cab">
        <Avatar nombre={`${cliente.nombres} ${cliente.apellidos}`} tamano="lg" />
        <div className="grid gap-0.5 min-w-0">
          <h1 className="t-h1 truncar">{`Hola, ${cliente.nombres.split(' ')[0]}`}</h1>
          <span className="t-sec">{reservas.datos ? plural(reservas.datos.totalElementos, 'reserva', 'reservas') : 'Tu cuenta'}</span>
        </div>
        {/* «Reservar» ya está en la cabecera del sitio. En celular «Salir» es solo el ícono. */}
        <div className="flex gap-2 ml-auto">
          <Boton variante="fantasma" icono={LogOut} etiqueta="Cerrar sesión" className="md:hidden" onClick={cerrarSesion} />
          <Boton variante="fantasma" icono={LogOut} etiqueta="Cerrar sesión" className="solo-tablet-arriba" onClick={cerrarSesion}>
            Salir
          </Boton>
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
        {/* El nombre de la cancha baja a 2 líneas antes que cortarse (antes se recortaba sin «…» a 320 px). */}
        <b className="flex items-start gap-1.5 min-w-0">
          <IconoDeporte deporte={r.cancha.deporte} className="ico ico-16 text-verde shrink-0 mt-[3px]" />
          <span className="min-w-0 line-clamp-2">{r.cancha.nombre}</span>
        </b>
        <span className="flex flex-wrap gap-x-3 gap-y-1 t-sec text-[13px]">
          <span className="inline-flex items-center gap-1 tnum">
            <Clock className="ico ico-14 shrink-0" aria-hidden />
            {/* Cada tramo viene sin cortes: solo se parte en « y ». */}
            <span>{horarioDeReserva(r)}</span>
          </span>
          <span className="tnum whitespace-nowrap">{fmtSoles(r.montoTotal)}</span>
          <span className="tnum whitespace-nowrap">{r.codigoReserva}</span>
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

type FiltroReservas = 'proximas' | 'pasadas' | 'canceladas' | 'todas';
const VACIO: Record<FiltroReservas, { il: IdIlustracion; titulo: string; texto: string }> = {
  proximas: { il: 'IL-30-hoy-sin-reservas', titulo: 'Sin reservas próximas', texto: 'Separa tu cancha en un par de minutos.' },
  pasadas: { il: 'IL-15-partido-jugado', titulo: 'Aún no hay partidos jugados', texto: 'Aquí verás tus reservas cuando ya hayas jugado.' },
  canceladas: { il: 'IL-24-sin-resultados', titulo: 'No tienes reservas canceladas', texto: 'Las que se cancelen o no se confirmen aparecerán aquí.' },
  todas: { il: 'IL-30-hoy-sin-reservas', titulo: 'Aún no tienes reservas', texto: 'Las reservas hechas con tu DNI aparecerán aquí.' },
};

function MisReservas({ lista, dni }: { lista: ReservaDeCliente[]; dni: string }) {
  const [filtro, setFiltro] = useState<FiltroReservas>('proximas');
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
        <Vacio
          ilustracion={VACIO[filtro].il}
          titulo={VACIO[filtro].titulo}
          texto={VACIO[filtro].texto}
          acciones={
            <BotonEnlace to="/reservar" icono={CalendarPlus}>
              Reservar
            </BotonEnlace>
          }
          compacto
        />
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
          <Vacio ilustracion="IL-15-partido-jugado" titulo="Nada por calificar" texto="Después de jugar, aquí podrás calificar la cancha." compacto />
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
                  <BotonEnlace to={`/canchas/${r.cancha.id}#h-resenas`} tamano="sm" variante="fantasma" icono={Eye}>
                    Ver reseñas
                  </BotonEnlace>
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
  const [fallaClave, setFallaClave] = useState<string | null>(null);
  const datos = useForm<DatosCuentaEntrada>({ resolver: zodResolver(esquemaDatosCuenta), mode: 'onTouched', defaultValues: { telefono: cliente?.telefono ?? '', correo: cliente?.correo ?? '' } });
  const clave = useForm<CambioClaveClienteEntrada>({ resolver: zodResolver(esquemaCambioClaveCliente), mode: 'onTouched', defaultValues: { actual: '', nueva: '', confirmacion: '' } });
  const nueva = clave.watch('nueva') ?? '';
  const reglas = errorClave(clave.formState.errors.nueva?.message);
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
    setFallaClave(null);
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
        if (!n) setFallaClave(e.message);
      } else setFallaClave('No se pudo cambiar.');
    }
  });

  return (
    <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
      <form className="card card-pad grid gap-4" onSubmit={guardarDatos} noValidate>
        <h2 className="t-h3">Tus datos</h2>
        {/* DNI y nombre vienen de RENIEC y no se editan: se muestran como texto, sin cortarse. */}
        <dl className="m-0 grid gap-3 rounded-12 bg-superficie-suave px-4 py-3 md:grid-cols-[auto_minmax(0,1fr)] md:gap-x-8">
          <div className="grid gap-0.5">
            <dt className="text-[12.5px] font-semibold text-texto-suave">DNI</dt>
            <dd className="m-0 font-semibold tnum inline-flex items-center gap-1.5">
              {cliente.dni}
              <BadgeCheck className="ico ico-16 text-verde" aria-label="Verificado con RENIEC" />
            </dd>
          </div>
          <div className="grid gap-0.5 min-w-0">
            <dt className="text-[12.5px] font-semibold text-texto-suave">Nombre</dt>
            <dd className="m-0 font-semibold break-words">{`${cliente.nombres} ${cliente.apellidos}`}</dd>
          </div>
        </dl>
        <div className="form-grid dos">
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
        {/* Celular: Nueva, reglas y Repite. Desde 768: Nueva y Repite lado a lado y las reglas debajo (flujo dense). */}
        <div className="form-grid dos md:grid-flow-row-dense">
          <CampoTexto label="Nueva" requerido type="password" autoComplete="new-password" maxLength={64} error={reglas.error} {...clave.register('nueva')} />
          <ReglasClave valor={nueva} conError={reglas.conError} className="md:col-span-2" />
          <CampoTexto label="Repite la nueva" requerido type="password" autoComplete="new-password" maxLength={64} error={clave.formState.errors.confirmacion?.message} {...clave.register('confirmacion')} />
        </div>
        {fallaClave && <Alerta tipo="error">{fallaClave}</Alerta>}
        <div>
          <Boton type="submit" icono={KeyRound} cargando={clave.formState.isSubmitting} textoCargando="Cambiando…">
            Cambiar contraseña
          </Boton>
        </div>
      </form>
    </div>
  );
}
