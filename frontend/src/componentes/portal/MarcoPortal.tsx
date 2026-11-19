import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  CalendarPlus,
  Clock,
  LayoutGrid,
  Lock,
  LogIn,
  LogOut,
  MapPin,
  Menu,
  MessageCircle,
  ShieldCheck,
  Smartphone,
  Ticket,
  UserRound,
  X,
} from 'lucide-react';
import { useCuenta } from '@/contexto/CuentaContext';
import { fmtCelular, iniciales } from '@/utilidades/formato';
import { Logo, PatronCancha } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { Boton, BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { PanelLateral } from '@/componentes/ui/PanelLateral';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { fmtHora12 } from '@/utilidades/fecha';
import { MODO_MOCK } from '@/utilidades/recursos';
import { ChatCanchita } from './ChatCanchita';

const NAV = [
  { a: '/canchas', texto: 'Canchas', icono: LayoutGrid },
  { a: '/mi-reserva', texto: 'Mi reserva', icono: Ticket },
];

/** wa.me/51914995187 a partir de «+51 914 995 187». */
export function enlaceWhatsapp(numero: string | undefined, texto?: string): string {
  const d = String(numero ?? '+51 914 995 187').replace(/\D/g, '');
  return `https://wa.me/${d.startsWith('51') ? d : `51${d}`}${texto ? `?text=${encodeURIComponent(texto)}` : ''}`;
}

const SELLO = MODO_MOCK ? 'Proyecto UTP · datos simulados' : 'Proyecto UTP · Desarrollo Web Integrado';

/** «+51 914 995 187» con el número que venga del API (crudo o ya formateado). */
function telefonoComplejo(numero: string | undefined): string {
  return numero && /\d/.test(numero) ? fmtCelular(numero, { prefijo: true }) : '+51 914 995 187';
}

/**
 * Ítem resaltado del menú: «Mis reservas» (/mi-cuenta) solo en su pantalla; «Mi cuenta» (/mi-cuenta/datos) en datos y
 * reseñas; el resto por sección (/canchas también resalta en /canchas/:slug). Así nunca quedan dos a la vez.
 */
function esActivo(a: string, pathname: string): boolean {
  if (a === '/mi-cuenta') return pathname === '/mi-cuenta' || pathname === '/mi-cuenta/';
  if (a === '/mi-cuenta/datos') return pathname.startsWith('/mi-cuenta/') && pathname !== '/mi-cuenta/';
  return pathname === a || pathname.startsWith(`${a}/`);
}

/**
 * Marco del portal (prototipo `marcoCliente`): cabecera fija con logo SIN eslogan, navegación y «Reservar»;
 * en móvil «Mi reserva» + menú lateral. modo 'flujo' (asistente de reserva): solo «Ayuda» y «Salir», sin pie.
 */
export function MarcoPortal({ children, modo = 'completo' }: { children: ReactNode; modo?: 'completo' | 'flujo' }) {
  const { datos: complejo } = useComplejo();
  const { cliente } = useCuenta();
  const aMiReserva = cliente ? '/mi-cuenta' : '/mi-reserva';
  const nav = NAV.map((n) => (n.a === '/mi-reserva' && cliente ? { ...n, a: '/mi-cuenta', texto: 'Mis reservas' } : n));
  const [menu, setMenu] = useState(false);
  const [salir, setSalir] = useState(false);
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const enInicio = pathname === '/';
  const botonMenu = useRef<HTMLButtonElement>(null);
  const telefono = telefonoComplejo(complejo?.whatsapp);

  useEffect(() => {
    setMenu(false);
    window.scrollTo({ top: 0 });
  }, [pathname]);

  // Sin datos del back: el horario por defecto (SPEC_V2 §0), armado igual (con espacios duros) para que no se parta.
  const horario = `Todos los días · ${fmtHora12(complejo?.horario.primerTurno ?? '08:00')} – ${fmtHora12(complejo?.horario.ultimoTurno ?? '23:00')}`;

  return (
    <div className="pantalla">
      <a href="#contenido" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[100] focus:bg-white focus:px-4 focus:py-2 focus:rounded-12 focus:shadow-flota">
        Saltar al contenido
      </a>
      <div className="mc">
        <header className="mc-header">
          <div className="contenedor mc-header-in">
            <Link className="mc-logo" to="/" aria-label="Alcanfores, ir al inicio">
              <Logo variante="horizontal" className="!h-[26px] md:!h-[30px]" />
            </Link>
            {modo === 'flujo' ? (
              <div className="mc-acciones">
                <a className="mc-ayuda" href={enlaceWhatsapp(complejo?.whatsapp, 'Hola, necesito ayuda con mi reserva.')} target="_blank" rel="noopener noreferrer" aria-label="Ayuda por WhatsApp">
                  <MessageCircle className="ico ico-18" aria-hidden />
                  <span>Ayuda</span>
                </a>
                <Boton variante="fantasma" tamano="sm" icono={X} onClick={() => setSalir(true)}>
                  Salir
                </Boton>
              </div>
            ) : (
              <>
                <nav className="mc-nav" aria-label="Principal">
                  {nav.map(({ a, texto, icono: Icono }) => (
                    <Link key={a} to={a} className="gap-2" aria-current={esActivo(a, pathname) ? 'page' : undefined}>
                      <Icono className="ico ico-18" aria-hidden />
                      {texto}
                    </Link>
                  ))}
                </nav>
                <div className="mc-acciones">
                  {/* v2: cuenta del cliente, discreta (reservar sin cuenta sigue igual) */}
                  {/* En celular (<768) el avatar e «Ingresar» se ocultan: ya están en el menú lateral. */}
                  {cliente ? (
                    <Link to="/mi-cuenta/datos" className="mc-cuenta" aria-label={`Mi cuenta: ${cliente.nombres}`} aria-current={esActivo('/mi-cuenta/datos', pathname) ? 'page' : undefined}>
                      <span className="mc-cuenta-av">{iniciales(`${cliente.nombres} ${cliente.apellidos}`)}</span>
                      <span className="solo-escritorio">{cliente.nombres.split(' ')[0]}</span>
                    </Link>
                  ) : (
                    <Link to="/ingresar" className="mc-ingresar">
                      <LogIn className="ico ico-18" aria-hidden />
                      <span>Ingresar</span>
                    </Link>
                  )}
                  <BotonEnlace to={aMiReserva} variante="secundario" tamano="sm" icono={Ticket} className="mc-mireserva-movil" etiqueta={cliente ? 'Mis reservas' : 'Mi reserva'}>
                    {/* Debajo de 360 px queda solo el ícono (el texto sigue para lectores de pantalla). */}
                    <span className="mc-mireserva-txt">{cliente ? 'Mis reservas' : 'Mi reserva'}</span>
                  </BotonEnlace>
                  <Boton ref={botonMenu} variante="fantasma" icono={Menu} etiqueta="Abrir menú" className="mc-hamburguesa" aria-haspopup="dialog" aria-expanded={menu} onClick={() => setMenu(true)} />
                  <span className="solo-tablet-arriba">
                    <BotonEnlace to="/reservar" variante={enInicio ? 'oscuro' : 'primario'} icono={CalendarPlus}>
                      Reservar
                    </BotonEnlace>
                  </span>
                </div>
              </>
            )}
          </div>
        </header>

        <main className="mc-main" id="contenido" tabIndex={-1}>
          {children}
        </main>

        {modo === 'completo' && (
          <footer className="mc-footer sobre-oscuro">
            <PatronCancha />
            <div className="contenedor">
              <div className="relative grid gap-5 py-7 md:grid-cols-[auto_1fr] md:items-center md:gap-12 md:py-9">
                <Logo variante="horizontal-oscuro" className="h-[28px]" />
                <ul className="grid grid-cols-2 !gap-x-6 !gap-y-2.5 lg:grid-cols-[auto_auto] lg:justify-end lg:!gap-x-10" aria-label="Datos del complejo">
                  <li className="fila-ico col-span-2 lg:col-span-1">
                    <MapPin className="ico ico-18" aria-hidden />
                    <span>
                      <span className="sr-only">Dirección: </span>
                      {complejo?.direccion ?? 'Carretera Central km 11.5, Santa Clara, Ate'}
                    </span>
                  </li>
                  <li className="fila-ico col-span-2 lg:col-span-1">
                    <Clock className="ico ico-18" aria-hidden />
                    <span>
                      <span className="sr-only">Horario: </span>
                      {horario}
                    </span>
                  </li>
                  <li className="fila-ico">
                    <MessageCircle className="ico ico-18" aria-hidden />
                    <a href={enlaceWhatsapp(complejo?.whatsapp)} target="_blank" rel="noopener noreferrer">
                      <span className="sr-only">WhatsApp: </span>
                      {telefono}
                    </a>
                  </li>
                  <li className="fila-ico">
                    <Smartphone className="ico ico-18" aria-hidden />
                    <span>
                      <span className="sr-only">Pagos: </span>Yape · Plin · Tarjeta
                    </span>
                  </li>
                </ul>
              </div>
              <div className="mc-footer-base">
                <nav className="flex flex-wrap gap-x-5 gap-y-2 text-[13.5px]" aria-label="Enlaces del pie">
                  <Link to="/reservar" className="inline-flex items-center gap-1.5">
                    <CalendarPlus className="ico ico-16" aria-hidden />
                    Reservar
                  </Link>
                  <Link to={aMiReserva} className="inline-flex items-center gap-1.5">
                    <Ticket className="ico ico-16" aria-hidden />
                    {cliente ? 'Mis reservas' : 'Mi reserva'}
                  </Link>
                  <Link to="/terminos" className="inline-flex items-center gap-1.5">
                    <ShieldCheck className="ico ico-16" aria-hidden />
                    Términos
                  </Link>
                  <Link to="/privacidad" className="inline-flex items-center gap-1.5">
                    <Lock className="ico ico-16" aria-hidden />
                    Privacidad
                  </Link>
                  <Link to="/admin/login" className="inline-flex items-center gap-1.5">
                    <LogIn className="ico ico-16" aria-hidden />
                    Personal
                  </Link>
                </nav>
                <span className="flex flex-wrap items-center gap-x-4 gap-y-2">
                  <span>© {new Date().getFullYear()} Alcanfores</span>
                  <span className="sello-demo">{SELLO}</span>
                </span>
              </div>
            </div>
          </footer>
        )}
      </div>

      {/* Menú en celular: el mismo PanelLateral del panel (entra de DERECHA a IZQUIERDA, la hamburguesa está a la derecha).
          Él bloquea el scroll, atrapa el foco, cierra con Esc, la X, el fondo o deslizando, y devuelve el foco a la hamburguesa. */}
      <PanelLateral
        abierto={menu}
        alCerrar={() => setMenu(false)}
        devolverFoco={botonMenu}
        className="menu-portal"
        etiquetaCerrar="Cerrar menú"
        titulo={
          <>
            <Logo variante="horizontal" alt="" className="h-[24px]" />
            <span className="sr-only">Menú</span>
          </>
        }
        pie={
          <>
            <BotonExterno href={enlaceWhatsapp(complejo?.whatsapp)} target="_blank" variante="whatsapp" icono={MessageCircle} bloque>
              {`WhatsApp ${telefono}`}
            </BotonExterno>
            <span className="sello-demo claro">{SELLO}</span>
          </>
        }
      >
        <nav className="drawer-nav" aria-label="Menú principal">
          {[
            { a: '/reservar', texto: 'Reservar', icono: CalendarPlus },
            ...nav,
            // Con sesión: «Mis reservas» (/mi-cuenta) y «Mi cuenta» (/mi-cuenta/datos) van a pantallas distintas.
            cliente ? { a: '/mi-cuenta/datos', texto: 'Mi cuenta', icono: UserRound } : { a: '/ingresar', texto: 'Ingresar', icono: LogIn },
            { a: '/terminos', texto: 'Términos', icono: ShieldCheck },
            { a: '/privacidad', texto: 'Privacidad', icono: Lock },
          ].map(({ a, texto, icono: Icono }) => (
            <Link key={a} to={a} aria-current={esActivo(a, pathname) ? 'page' : undefined} onClick={() => setMenu(false)}>
              <Icono className="ico" aria-hidden />
              {texto}
            </Link>
          ))}
        </nav>
      </PanelLateral>

      <Modal
        abierto={salir}
        alCerrar={() => setSalir(false)}
        titulo="¿Salir de la reserva?"
        icono={LogOut}
        tono="aviso"
        tamano="sm"
        acciones={
          <>
            <Boton variante="secundario" onClick={() => setSalir(false)}>
              Seguir aquí
            </Boton>
            <Boton
              variante="oscuro"
              onClick={() => {
                setSalir(false);
                navigate('/');
              }}
            >
              Salir
            </Boton>
          </>
        }
      >
        <p className="t-sec">Guardamos lo que elegiste mientras no cierres el navegador.</p>
      </Modal>

      {/* Canchita (IA): botón flotante + chat. Solo si el chat web está activo; en /reservar sube sobre la barra fija. */}
      <ChatCanchita modo={modo} />
    </div>
  );
}
