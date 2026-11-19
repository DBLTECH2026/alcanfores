import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { z } from 'zod';
import { AtSign, CircleCheck, CirclePause, KeyRound, Lock, Mail, Pencil, Plus, ShieldCheck, UserPlus, UserRound } from 'lucide-react';
import { usuariosApi } from '@/api/admin';
import type { UsuarioAdmin } from '@/api/admin/tipos';
import { esErrorApi } from '@/api/cliente';
import { useAuth } from '@/contexto/AuthContext';
import { useToast } from '@/contexto/ToastContext';
import { useCarga } from '@/hooks/useCarga';
import { useTituloPagina } from '@/hooks/useTituloPagina';
import { aplicarErroresServidor } from '@/hooks/useErroresServidor';
import { CabeceraPagina } from '@/componentes/panel/PanelLayout';
import { BarraBusqueda, ErrorCarga, Estado, MenuFila, ModalConfirmar, useFiltrosUrl, useRetraso } from '@/componentes/panel/Herramientas';
import { Boton } from '@/componentes/ui/Boton';
import { CampoTexto } from '@/componentes/ui/Campo';
import { Avatar, Paginacion, Segmentado, Tabla, Vacio } from '@/componentes/ui/Datos';
import { Etiqueta } from '@/componentes/ui/Estados';
import { fmtFechaHora, fmtHace } from '@/utilidades/fecha';
import { aTitulo } from '@/utilidades/formato';
import { esquemaRestablecer, esquemaUsuarioEditar, esquemaUsuarioNuevo } from './esquemasPanel';

/** /admin/usuarios — cuentas del panel: crear, editar y rol, activar/desactivar, restablecer contraseña. Solo ADMIN. */
export default function Usuarios() {
  useTituloPagina('Usuarios');
  const toast = useToast();
  const { usuario: yo } = useAuth();
  const f = useFiltrosUrl();
  const [busqueda, setBusqueda] = useState(f.leer('q'));
  const q = useRetraso(busqueda);
  useEffect(() => {
    if (q !== f.leer('q')) f.poner({ q });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);
  const [version, setVersion] = useState(0);
  const filtros = { q: f.leer('q'), rol: f.leer('rol'), estado: f.leer('estado') };
  const lista = useCarga(() => usuariosApi.listar({ ...filtros, page: f.pagina, size: 15 }), JSON.stringify({ ...filtros, p: f.pagina, version }));
  const [form, setForm] = useState<{ usuario: UsuarioAdmin | null } | null>(null);
  const [estado, setEstado] = useState<UsuarioAdmin | null>(null);
  const [clave, setClave] = useState<UsuarioAdmin | null>(null);
  const recargar = () => setVersion((v) => v + 1);

  const cambiarEstado = async (u: UsuarioAdmin) => {
    try {
      await usuariosApi.estado(u.id, u.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO');
      toast.exito(u.estado === 'ACTIVO' ? 'Usuario desactivado' : 'Usuario activado');
      setEstado(null);
      recargar();
    } catch {
      setEstado(null);
    }
  };

  return (
    <>
      <CabeceraPagina
        titulo="Usuarios"
        subtitulo="Quién entra al panel y con qué rol"
        busto="BU-rosa-panel"
        acciones={
          <Boton icono={UserPlus} onClick={() => setForm({ usuario: null })}>
            Nuevo usuario
          </Boton>
        }
      />
      <BarraBusqueda
        valor={busqueda}
        alCambiar={setBusqueda}
        placeholder="Nombre, usuario o correo"
        conteo={lista.datos ? `${lista.datos.totalElementos} usuarios` : ''}
        extra={
          <>
            <select className="bb-select" aria-label="Rol" value={filtros.rol} onChange={(e) => f.poner({ rol: e.target.value })}>
              <option value="">Todos los roles</option>
              <option value="ADMIN">Administrador</option>
              <option value="RECEPCION">Recepción</option>
            </select>
            <select className="bb-select" aria-label="Estado" value={filtros.estado} onChange={(e) => f.poner({ estado: e.target.value })}>
              <option value="">Activos e inactivos</option>
              <option value="ACTIVO">Activos</option>
              <option value="INACTIVO">Inactivos</option>
            </select>
          </>
        }
      />
      {lista.error ? (
        <ErrorCarga alReintentar={lista.recargar} />
      ) : (
        <Tabla<UsuarioAdmin>
          etiqueta="Usuarios"
          filas={lista.datos?.contenido ?? []}
          claveFila={(u) => u.id}
          cargando={lista.cargando}
          vacio={lista.cargando ? 'Cargando…' : <Vacio ilustracion="IL-24-sin-resultados" titulo="Sin usuarios" compacto />}
          insignias={(u) => (
            <>
              <Estado dominio="rol" estado={u.rol} />
              <Estado dominio="usuario" estado={u.estado} />
            </>
          )}
          columnas={[
            {
              clave: 'usuario',
              titulo: 'Usuario',
              celda: (u) => (
                <div className="td-doc">
                  <Avatar nombre={`${u.nombres} ${u.apellidos}`} tamano="sm" admin={u.rol === 'ADMIN'} />
                  <div>
                    <b className="block">
                      {u.nombres} {u.apellidos}
                      {u.id === yo?.id && <span className="t-sec font-normal"> (tú)</span>}
                    </b>
                    <span className="sub t-sec">@{u.usuario}</span>
                  </div>
                </div>
              ),
            },
            { clave: 'correo', titulo: 'Correo', celda: (u) => u.correo },
            { clave: 'rol', titulo: 'Rol', enTarjeta: false, celda: (u) => <Estado dominio="rol" estado={u.rol} /> },
            {
              clave: 'estado',
              titulo: 'Estado',
              enTarjeta: false,
              celda: (u) => (
                <span className="inline-flex flex-wrap gap-1">
                  <Estado dominio="usuario" estado={u.estado} />
                  {u.bloqueado && (
                    <Etiqueta tono="rojo" icono={Lock}>
                      bloqueado
                    </Etiqueta>
                  )}
                </span>
              ),
            },
            { clave: 'acceso', titulo: 'Último acceso', celda: (u) => <span className="tnum" title={u.ultimoAcceso ? fmtFechaHora(u.ultimoAcceso, { h24: true }) : undefined}>{u.ultimoAcceso ? fmtHace(u.ultimoAcceso) : 'Nunca'}</span> },
          ]}
          acciones={(u) => (
            <MenuFila
              acciones={[
                { texto: 'Editar', icono: Pencil, alPulsar: () => setForm({ usuario: u }) },
                { texto: 'Restablecer contraseña', icono: KeyRound, alPulsar: () => setClave(u) },
                { texto: u.estado === 'ACTIVO' ? 'Desactivar' : 'Activar', icono: u.estado === 'ACTIVO' ? CirclePause : CircleCheck, peligro: u.estado === 'ACTIVO', alPulsar: () => setEstado(u), oculta: u.id === yo?.id },
              ]}
            />
          )}
          pie={lista.datos && <Paginacion pagina={lista.datos.pagina} totalPaginas={lista.datos.totalPaginas} totalElementos={lista.datos.totalElementos} tamanio={lista.datos.tamanio} alCambiar={(p) => f.poner({ page: p || null })} etiqueta="usuarios" />}
        />
      )}

      {form && <FormUsuario usuario={form.usuario} esYo={form.usuario?.id === yo?.id} alCerrar={() => setForm(null)} alHecho={() => { setForm(null); recargar(); }} />}
      <ModalRestablecer usuario={clave} alCerrar={() => setClave(null)} />
      <ModalConfirmar
        abierto={estado !== null}
        alCerrar={() => setEstado(null)}
        titulo={estado?.estado === 'ACTIVO' ? 'Desactivar usuario' : 'Activar usuario'}
        subtitulo={estado ? `@${estado.usuario}` : ''}
        peligro={estado?.estado === 'ACTIVO'}
        textoConfirmar={estado?.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
        iconoConfirmar={estado?.estado === 'ACTIVO' ? CirclePause : CircleCheck}
        alConfirmar={() => (estado ? cambiarEstado(estado) : undefined)}
      >
        <div className="caja-aviso">
          <div>
            <Lock className="ico" aria-hidden />
            {estado?.estado === 'ACTIVO' ? 'No podrá entrar al panel' : 'Podrá volver a entrar con su contraseña'}
          </div>
        </div>
      </ModalConfirmar>
    </>
  );
}

type EntradaNuevo = z.input<typeof esquemaUsuarioNuevo>;
function FormUsuario({ usuario, esYo, alCerrar, alHecho }: { usuario: UsuarioAdmin | null; esYo: boolean; alCerrar: () => void; alHecho: () => void }) {
  const toast = useToast();
  const nuevo = usuario === null;
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    watch,
    formState: { errors },
  } = useForm<EntradaNuevo>({
    resolver: zodResolver((nuevo ? esquemaUsuarioNuevo : esquemaUsuarioEditar) as typeof esquemaUsuarioNuevo),
    mode: 'onTouched',
    defaultValues: { nombres: usuario?.nombres ?? '', apellidos: usuario?.apellidos ?? '', usuario: usuario?.usuario ?? '', correo: usuario?.correo ?? '', rol: usuario?.rol ?? 'RECEPCION', contrasena: '', confirmacion: '' },
  });
  const enviar = handleSubmit(async (d) => {
    const datos = { nombres: aTitulo(d.nombres), apellidos: aTitulo(d.apellidos), usuario: d.usuario.trim(), correo: d.correo.trim(), rol: d.rol };
    try {
      if (nuevo) await usuariosApi.crear({ ...datos, contrasena: d.contrasena });
      else await usuariosApi.editar(usuario.id, datos);
      toast.exito(nuevo ? 'Usuario creado' : 'Usuario guardado');
      alHecho();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['nombres', 'apellidos', 'usuario', 'correo', 'rol', 'contrasena']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo guardar.');
    }
  });
  return (
    <ModalConfirmar
      abierto
      alCerrar={alCerrar}
      titulo={nuevo ? 'Nuevo usuario' : 'Editar usuario'}
      subtitulo={nuevo ? 'Acceso al panel' : `@${usuario.usuario}`}
      tamano="lg"
      textoConfirmar={nuevo ? 'Crear usuario' : 'Guardar'}
      iconoConfirmar={nuevo ? Plus : Pencil}
      alConfirmar={enviar}
    >
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <div className="cajas2">
          <CampoTexto label="Nombres" requerido icono={UserRound} error={errors.nombres?.message} {...register('nombres')} />
          <CampoTexto label="Apellidos" requerido error={errors.apellidos?.message} {...register('apellidos')} />
          <CampoTexto label="Usuario" requerido icono={AtSign} autoComplete="off" maxLength={30} ayuda="Minúsculas, 4 a 30" error={errors.usuario?.message} {...register('usuario')} />
          <CampoTexto label="Correo" requerido type="email" icono={Mail} error={errors.correo?.message} {...register('correo')} />
        </div>
        <span className="etiqueta-osc">Rol</span>
        <Segmentado
          etiqueta="Rol"
          bloque
          valor={watch('rol')}
          alCambiar={(r) => !esYo && setValue('rol', r)}
          opciones={[
            { valor: 'RECEPCION', texto: 'Recepción', icono: UserRound },
            { valor: 'ADMIN', texto: 'Administrador', icono: ShieldCheck },
          ]}
        />
        {esYo && <span className="text-[12px] text-[#6B7C74]">No puedes quitarte el rol de administrador.</span>}
        {nuevo && (
          <div className="cajas2">
            <CampoTexto label="Contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" ayuda="8+ con mayúscula, minúscula y número" error={errors.contrasena?.message} {...register('contrasena')} />
            <CampoTexto label="Repite la contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" error={errors.confirmacion?.message} {...register('confirmacion')} />
          </div>
        )}
      </form>
    </ModalConfirmar>
  );
}

function ModalRestablecer({ usuario, alCerrar }: { usuario: UsuarioAdmin | null; alCerrar: () => void }) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<z.infer<typeof esquemaRestablecer>>({ resolver: zodResolver(esquemaRestablecer), mode: 'onTouched', defaultValues: { nueva: '', confirmacion: '' } });
  useEffect(() => reset({ nueva: '', confirmacion: '' }), [usuario, reset]);
  const enviar = handleSubmit(async ({ nueva }) => {
    if (!usuario) return;
    try {
      await usuariosApi.restablecer(usuario.id, nueva);
      toast.exito('Contraseña restablecida');
      alCerrar();
    } catch (e) {
      const { aplicados } = aplicarErroresServidor(e, setError, ['nueva']);
      if (!aplicados) toast.error(esErrorApi(e) ? e.message : 'No se pudo restablecer.');
    }
  });
  return (
    <ModalConfirmar abierto={usuario !== null} alCerrar={alCerrar} titulo="Restablecer contraseña" subtitulo={usuario ? `@${usuario.usuario}` : ''} textoConfirmar="Restablecer" iconoConfirmar={KeyRound} alConfirmar={enviar}>
      <form className="grid gap-3" onSubmit={enviar} noValidate>
        <CampoTexto label="Nueva contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" ayuda="8+ con mayúscula, minúscula y número" error={errors.nueva?.message} {...register('nueva')} />
        <CampoTexto label="Repite la contraseña" requerido type="password" icono={KeyRound} autoComplete="new-password" error={errors.confirmacion?.message} {...register('confirmacion')} />
        <div className="caja-aviso">
          <div>
            <Lock className="ico" aria-hidden />
            Dísela en persona; luego la cambia en Mi cuenta.
          </div>
        </div>
      </form>
    </ModalConfirmar>
  );
}
