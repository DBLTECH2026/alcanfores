import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  CalendarPlus,
  Clock,
  LayoutGrid,
  Lock,
  LogIn,
  MapPin,
  Menu,
  MessageCircle,
  ShieldCheck,
  Smartphone,
  Ticket,
  X,
} from 'lucide-react';
import { Logo, PatronCancha } from '@/componentes/ui/Marca';
import { Modal } from '@/componentes/ui/Modal';
import { Boton, BotonEnlace, BotonExterno } from '@/componentes/ui/Boton';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { fmtHora12 } from '@/utilidades/fecha';
import { MODO_MOCK } from '@/utilidades/recursos';
import { cx } from '@/utilidades/cx';

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

/**
 * Marco del portal (prototipo `marcoCliente`): cabecera fija con logo SIN eslogan, navegación y «Reservar»;
 * en móvil «Mi reserva» + menú lateral. modo 'flujo' (asistente de reserva): solo «Ayuda» y «Salir», sin pie.
 */
export function MarcoPortal({ children, modo = 'completo' }: { children: ReactNode; modo?: 'completo' | 'flujo' }) {
  const { datos: complejo } = useComplejo();
  const [menu, setMenu] = useState(false);
  const [salir, setSalir] = useState(false);
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const enInicio = pathname === '/';

  useEffect(() => {
    setMenu(false);
    window.scrollTo({ top: 0 });
  }, [pathname]);

  const horario = complejo ? `Todos los días · ${fmtHora12(complejo.horario.primerTurno)} – ${fmtHora12(complejo.horario.ultimoTurno)}` : 'Todos los días · 8:00 a. m. – 11:00 p. m.';

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
                  {NAV.map(({ a, texto, icono: Icono }) => (
                    <NavLink key={a} to={a} className="gap-2" aria-current={pathname.startsWith(a) ? 'page' : undefined}>
                      <Icono className="ico ico-18" aria-hidden />
                      {texto}
                    </NavLink>
                  ))}
                </nav>
                <div className="mc-acciones">
                  <BotonEnlace to="/mi-reserva" variante="secundario" tamano="sm" icono={Ticket} className="mc-mireserva-movil">
                    Mi reserva
                  </BotonEnlace>
                  <Boton variante="fantasma" icono={Menu} etiqueta="Abrir menú" className="mc-hamburguesa" aria-expanded={menu} onClick={() => setMenu(true)} />
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
                      {complejo?.whatsapp ?? '+51 914 995 187'}
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
                  <Link to="/mi-reserva" className="inline-flex items-center gap-1.5">
                    <Ticket className="ico ico-16" aria-hidden />
                    Mi reserva
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

      {menu &&
        createPortal(
          <div className="capa-modal" style={{ padding: 0, justifyContent: 'flex-end' }} onMouseDown={(e) => e.target === e.currentTarget && setMenu(false)}>
            <aside className="drawer drawer-der animate-entrar" role="dialog" aria-modal="true" aria-label="Menú">
              <div className="drawer-cab">
                <Logo variante="horizontal" className="h-[24px]" />
                <Boton variante="fantasma" icono={X} etiqueta="Cerrar menú" onClick={() => setMenu(false)} autoFocus />
              </div>
              <nav className="drawer-nav">
                {[{ a: '/reservar', texto: 'Reservar', icono: CalendarPlus }, ...NAV, { a: '/terminos', texto: 'Términos', icono: ShieldCheck }, { a: '/privacidad', texto: 'Privacidad', icono: Lock }].map(
                  ({ a, texto, icono: Icono }) => (
                    <Link key={a} to={a} aria-current={pathname.startsWith(a) ? 'page' : undefined} onClick={() => setMenu(false)}>
                      <Icono className="ico" aria-hidden />
                      {texto}
                    </Link>
                  ),
                )}
              </nav>
              <div className="drawer-pie">
                <BotonExterno href={enlaceWhatsapp(complejo?.whatsapp)} target="_blank" variante="whatsapp" icono={MessageCircle} bloque>
                  {`WhatsApp ${complejo?.whatsapp ?? '+51 914 995 187'}`}
                </BotonExterno>
                <span className={cx('sello-demo claro justify-self-start')}>{SELLO}</span>
              </div>
            </aside>
          </div>,
          document.body,
        )}

      <Modal
        abierto={salir}
        alCerrar={() => setSalir(false)}
        titulo="¿Salir de la reserva?"
        icono={X}
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
    </div>
  );
}
