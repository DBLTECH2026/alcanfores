import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, Bell, CalendarDays, ChevronDown, ChevronRight, ExternalLink, House, KeyRound, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Search, X } from 'lucide-react';
import { panelApi } from '@/api/servicios';
import { useAuth } from '@/contexto/AuthContext';
import { Logo, Busto, PatronCancha } from '@/componentes/ui/Marca';
import { Avatar } from '@/componentes/ui/Datos';
import { itemDeRuta, menuPara, type ItemMenu } from '@/rutas/menuPanel';
import { ahoraLima, fmtFecha } from '@/utilidades/fecha';
import { iniciales } from '@/utilidades/formato';
import { MODO_MOCK, type IdBusto } from '@/utilidades/recursos';
import { cx } from '@/utilidades/cx';
import './panel-extra.css';

const ROL_TEXTO = { ADMIN: 'Administrador', RECEPCION: 'Recepción' } as const;

interface ApiPanel {
  /** Comprobantes por validar (contador del menú y de la campana). */
  porValidar: number;
  /** Vuelve a pedir el contador (después de validar o rechazar). */
  refrescarContador: () => void;
}
const ContextoPanel = createContext<ApiPanel>({ porValidar: 0, refrescarContador: () => undefined });
/** Datos compartidos del panel (contador por validar). */
export const usePanel = () => useContext(ContextoPanel);

function Navegacion({ items, porValidar, alNavegar }: { items: ItemMenu[]; porValidar: number; alNavegar?: () => void }) {
  const secciones = [...new Set(items.map((i) => i.seccion))];
  return (
    <nav className="ma-nav" aria-label="Menú del panel">
      {secciones.map((s) => (
        <div key={s} className="contents">
          <div className="ma-nav-sec">{s}</div>
          {items
            .filter((i) => i.seccion === s)
            .map((i) => {
              const I = i.icono;
              return (
                <NavLink key={i.clave} to={i.ruta} className="ma-item" onClick={alNavegar} title={i.texto}>
                  <I className="ico" aria-hidden />
                  <span>{i.texto}</span>
                  {i.contador && porValidar > 0 && (
                    <span className="ma-cnt" title="Por validar">
                      {porValidar}
                    </span>
                  )}
                </NavLink>
              );
            })}
        </div>
      ))}
    </nav>
  );
}

function Reloj() {
  const [ahora, setAhora] = useState(ahoraLima());
  useEffect(() => {
    const t = window.setInterval(() => setAhora(ahoraLima()), 20000);
    return () => window.clearInterval(t);
  }, []);
  return (
    <span className="mt-fecha" title="Hora de Lima">
      <CalendarDays className="ico" aria-hidden />
      <b>{fmtFecha(ahora.format('YYYY-MM-DD'), 'media')}</b>
      <span className="tnum">{ahora.format('HH:mm')}</span>
    </span>
  );
}

function MenuUsuario() {
  const { usuario, salir } = useAuth();
  const [abierto, setAbierto] = useState(false);
  const caja = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!abierto) return undefined;
    const fuera = (e: MouseEvent) => {
      if (!caja.current?.contains(e.target as Node)) setAbierto(false);
    };
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setAbierto(false);
    document.addEventListener('mousedown', fuera);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', fuera);
      document.removeEventListener('keydown', esc);
    };
  }, [abierto]);
  if (!usuario) return null;
  const nombre = `${usuario.nombres} ${usuario.apellidos}`;
  return (
    <div className="relative" ref={caja}>
      <button type="button" className="mt-usuario bg-transparent border-0 cursor-pointer text-left" aria-haspopup="menu" aria-expanded={abierto} onClick={() => setAbierto((a) => !a)}>
        <span className="mt-av">{iniciales(nombre)}</span>
        <div>
          <b>{nombre}</b>
          <small>{ROL_TEXTO[usuario.rol]}</small>
        </div>
        <ChevronDown className="ico" aria-hidden />
      </button>
      {abierto && (
        <div className="mt-menu animate-entrar" role="menu">
          <Link to="/admin/cuenta" role="menuitem" onClick={() => setAbierto(false)}>
            <KeyRound className="ico" aria-hidden />
            Cambiar contraseña
          </Link>
          <a href="/" target="_blank" rel="noopener" role="menuitem">
            <ExternalLink className="ico" aria-hidden />
            Ver el portal
          </a>
          <button type="button" role="menuitem" onClick={() => salir('manual')}>
            <LogOut className="ico" aria-hidden />
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}

function Buscador() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const entrada = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const atajo = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        entrada.current?.focus();
      }
    };
    document.addEventListener('keydown', atajo);
    return () => document.removeEventListener('keydown', atajo);
  }, []);
  return (
    <form
      className="mt-buscador"
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        if (q.trim()) navigate(`/admin/reservas?q=${encodeURIComponent(q.trim())}`);
      }}
    >
      <Search className="ico ico-16" aria-hidden />
      <input ref={entrada} type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Buscar reserva o cliente…" aria-label="Buscar reserva por código, DNI, nombre o celular" />
      <kbd>Ctrl K</kbd>
    </form>
  );
}

/**
 * Armazón del panel con el esqueleto «ELESS» del prototipo: barra lateral azul noche (menú por rol, plegable),
 * barra superior blanca con migas, buscador ⌃K, fecha de Lima, avisos y usuario. En < 1024: barra azul + menú lateral.
 */
export function PanelLayout() {
  const { usuario, salir } = useAuth();
  const { pathname } = useLocation();
  const [menuMovil, setMenuMovil] = useState(false);
  const [plegado, setPlegado] = useState(() => localStorage.getItem('alc-menu-plegado') === '1');
  const [porValidar, setPorValidar] = useState(0);
  const [version, setVersion] = useState(0);
  const items = menuPara(usuario?.rol);
  const actual = itemDeRuta(pathname);

  useEffect(() => {
    let vigente = true;
    panelApi
      .dashboard(undefined, { silenciar: true })
      .then((d) => vigente && setPorValidar(d.kpis.porValidar))
      .catch(() => undefined);
    return () => {
      vigente = false;
    };
  }, [version]);

  useEffect(() => setMenuMovil(false), [pathname]);

  const nombre = usuario ? `${usuario.nombres} ${usuario.apellidos}` : '';
  const pie = (
    <div className="ma-pie">
      {MODO_MOCK && <span className="sello-demo">Datos simulados</span>}
      <div className="ma-usuario">
        <Avatar nombre={nombre} admin />
        <div className="min-w-0">
          <b className="truncar">{nombre}</b>
          <span className="ma-rol">{usuario ? ROL_TEXTO[usuario.rol] : ''}</span>
        </div>
        <button type="button" className="ma-salir" aria-label="Cerrar sesión" title="Cerrar sesión" onClick={() => salir('manual')}>
          <LogOut className="ico ico-18" aria-hidden />
        </button>
      </div>
    </div>
  );

  return (
    <ContextoPanel.Provider value={{ porValidar, refrescarContador: () => setVersion((v) => v + 1) }}>
      <div className="pantalla">
        <div className={cx('ma', plegado && 'plegado')}>
          <aside className="ma-sidebar sobre-oscuro" aria-label="Barra lateral">
            <PatronCancha />
            <div className="ma-sidebar-in">
              <Link className="ma-logo" to="/admin/hoy" aria-label="Alcanfores, ir a Hoy">
                {plegado ? <Logo variante="isotipo" className="!h-[30px]" /> : <Logo variante="horizontal-oscuro" className="!h-[28px]" />}
              </Link>
              <Navegacion items={items} porValidar={porValidar} />
              {pie}
              <button
                type="button"
                className="ma-plegar bg-transparent border-0 cursor-pointer"
                aria-pressed={plegado}
                onClick={() => {
                  localStorage.setItem('alc-menu-plegado', plegado ? '0' : '1');
                  setPlegado((p) => !p);
                }}
              >
                {plegado ? <PanelLeftOpen className="ico" aria-hidden /> : <PanelLeftClose className="ico" aria-hidden />}
                <span>{plegado ? 'Desplegar menú' : 'Plegar menú'}</span>
              </button>
            </div>
          </aside>

          <div className="ma-cuerpo">
            <header className="ma-topbar">
              <button type="button" className="btn btn-fantasma btn-icono ma-menu-movil" aria-label="Abrir menú" aria-expanded={menuMovil} onClick={() => setMenuMovil(true)}>
                <Menu className="ico" aria-hidden />
              </button>
              <Logo variante="isotipo" className="isotipo" alt="" />
              <span className="ma-titulo-movil">{actual?.texto ?? 'Panel'}</span>
              <nav className="mt-migas" aria-label="Migas">
                <Link to="/admin/hoy">
                  <House className="ico ico-16" aria-hidden />
                  Alcanfores
                </Link>
                <ChevronRight className="ico ico-14" aria-hidden />
                {actual ? (
                  <span className="mt-chip" aria-current="page">
                    <actual.icono className="ico" aria-hidden />
                    {actual.texto}
                  </span>
                ) : (
                  <span className="mt-chip">Panel</span>
                )}
              </nav>
              <Buscador />
              <Reloj />
              <Link to="/admin/reservas?estadoPago=EN_REVISION" className="mt-avisos" aria-label={`Avisos: ${porValidar} por validar`}>
                <Bell className="ico" aria-hidden />
                {porValidar > 0 && <i>{porValidar}</i>}
              </Link>
              <MenuUsuario />
            </header>
            <main className="ma-main" id="contenido" tabIndex={-1}>
              <Outlet />
            </main>
          </div>
        </div>

        {menuMovil &&
          createPortal(
            <div className="capa-modal" style={{ padding: 0, justifyContent: 'flex-start' }} onMouseDown={(e) => e.target === e.currentTarget && setMenuMovil(false)}>
              <aside className="drawer drawer-izq sobre-oscuro animate-entrar" role="dialog" aria-modal="true" aria-label="Menú del panel">
                <div className="drawer-cab">
                  <Logo variante="horizontal-oscuro" className="h-[24px]" />
                  <button type="button" className="btn btn-contorno-claro btn-sm btn-icono" aria-label="Cerrar menú" onClick={() => setMenuMovil(false)} autoFocus>
                    <X className="ico" aria-hidden />
                  </button>
                </div>
                <div className="px-3 py-2 overflow-y-auto flex-1">
                  <Navegacion items={items} porValidar={porValidar} alNavegar={() => setMenuMovil(false)} />
                </div>
                <div className="px-3 pb-4">{pie}</div>
              </aside>
            </div>,
            document.body,
          )}
      </div>
    </ContextoPanel.Provider>
  );
}

/**
 * Cabecera de página del panel: busto en cuadro + título + subtítulo + acciones (prototipo `marcoAdmin`).
 * Uso: <CabeceraPagina titulo="Reservas" subtitulo="…" acciones={<Boton …/>} volver="/admin/reservas" />
 */
export function CabeceraPagina({ titulo, subtitulo, acciones, busto, volver }: { titulo: string; subtitulo?: ReactNode; acciones?: ReactNode; busto?: IdBusto; volver?: string }) {
  const { pathname } = useLocation();
  const b = busto ?? itemDeRuta(pathname)?.busto ?? 'BU-rosa-panel';
  return (
    <div className="ma-encabezado">
      <div className="ma-encabezado-txt">
        {volver && (
          <Link to={volver} className="cab-volver" aria-label="Volver">
            <ArrowLeft className="ico ico-18" aria-hidden />
          </Link>
        )}
        <Busto id={b} />
        <div>
          <h1 className="t-h1">{titulo}</h1>
          {subtitulo && <p className="t-sec">{subtitulo}</p>}
        </div>
      </div>
      {acciones && <div className="grupo-botones">{acciones}</div>}
    </div>
  );
}
