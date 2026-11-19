import type { ComponentType } from 'react';
import { Navigate, Outlet, createBrowserRouter, useRouteError, type RouteObject } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import { AuthProvider } from '@/contexto/AuthContext';
import { CuentaProvider } from '@/contexto/CuentaContext';
import { ToastProvider } from '@/contexto/ToastContext';
import { MarcoPortal } from '@/componentes/portal/MarcoPortal';
import { PanelLayout } from '@/componentes/panel/PanelLayout';
import { Vacio } from '@/componentes/ui/Datos';
import { Boton } from '@/componentes/ui/Boton';
import { AvisoReintento } from '@/componentes/ui/AvisoReintento';
import Inicio from '@/paginas/portal/Inicio';
import { NoEncontrada, Privacidad, Terminos } from '@/paginas/portal/Legales';
import { RutaProtegida } from './RutaProtegida';
import { MENU_PANEL } from './menuPanel';

/** Proveedores globales DENTRO del router (AuthContext usa useNavigate). */
function Raiz() {
  return (
    <ToastProvider>
      <AuthProvider>
        <CuentaProvider>
          <Outlet />
          <AvisoReintento />
        </CuentaProvider>
      </AuthProvider>
    </ToastProvider>
  );
}

function ErrorDeRuta() {
  const error = useRouteError();
  console.error(error);
  return (
    <div className="pantalla grid place-items-center p-6">
      <Vacio
        ilustracion="IL-19-sin-conexion"
        titulo="Algo falló"
        texto="Recarga la página para continuar."
        acciones={
          <Boton icono={RefreshCw} onClick={() => window.location.reload()}>
            Recargar
          </Boton>
        }
      />
    </div>
  );
}

const portal = (modo: 'completo' | 'flujo' = 'completo') =>
  function Marco() {
    return (
      <MarcoPortal modo={modo}>
        <Outlet />
      </MarcoPortal>
    );
  };
const MarcoCompleto = portal('completo');
const MarcoFlujo = portal('flujo');

/** Carga diferida: cada página en su propio archivo JS. */
const perezosa = (cargar: () => Promise<{ default: ComponentType }>): Pick<RouteObject, 'lazy'> => ({
  lazy: async () => ({ Component: (await cargar()).default }),
});

/**
 * Pantallas del panel. El siguiente agente reemplaza `EnConstruccion` por la página real de cada `ruta`
 * (las rutas, el menú y los roles salen de src/rutas/menuPanel.ts).
 */
const PAGINAS_PANEL: Record<string, Pick<RouteObject, 'lazy'>> = {
  '/admin/hoy': perezosa(() => import('@/paginas/panel/Hoy')),
  '/admin/reservas': perezosa(() => import('@/paginas/panel/Reservas')),
  '/admin/nueva-reserva': perezosa(() => import('@/paginas/panel/NuevaReserva')),
  '/admin/agenda': perezosa(() => import('@/paginas/panel/Agenda')),
  '/admin/canchas': perezosa(() => import('@/paginas/panel/Canchas')),
  '/admin/pagos': perezosa(() => import('@/paginas/panel/Pagos')),
  '/admin/clientes': perezosa(() => import('@/paginas/panel/Clientes')),
  '/admin/reportes': perezosa(() => import('@/paginas/panel/Reportes')),
  '/admin/notificaciones': perezosa(() => import('@/paginas/panel/Notificaciones')),
  '/admin/configuracion': perezosa(() => import('@/paginas/panel/Configuracion')),
  '/admin/usuarios': perezosa(() => import('@/paginas/panel/Usuarios')),
  '/admin/bitacora': perezosa(() => import('@/paginas/panel/Bitacora')),
  '/admin/estado': perezosa(() => import('@/paginas/panel/EstadoSistema')),
};

export const router = createBrowserRouter([
  {
    element: <Raiz />,
    errorElement: <ErrorDeRuta />,
    children: [
      {
        element: <MarcoCompleto />,
        children: [
          { path: '/', element: <Inicio /> },
          { path: '/canchas', ...perezosa(() => import('@/paginas/portal/Canchas')) },
          { path: '/canchas/:idOSlug', ...perezosa(() => import('@/paginas/portal/CanchaDetalle')) },
          { path: '/reservar/listo/:codigo', ...perezosa(() => import('@/paginas/portal/ReservaListo')) },
          { path: '/mi-reserva', ...perezosa(() => import('@/paginas/portal/MiReserva')) },
          // v2: cuentas de clientes
          { path: '/ingresar', ...perezosa(() => import('@/paginas/portal/cuenta/Ingresar')) },
          { path: '/recuperar', ...perezosa(() => import('@/paginas/portal/cuenta/Recuperar')) },
          { path: '/crear-cuenta', ...perezosa(() => import('@/paginas/portal/cuenta/CrearCuenta')) },
          { path: '/mi-cuenta/*', ...perezosa(() => import('@/paginas/portal/cuenta/MiCuenta')) },
          { path: '/pago/resultado', ...perezosa(() => import('@/paginas/portal/PagoResultado')) },
          { path: '/terminos', element: <Terminos /> },
          { path: '/privacidad', element: <Privacidad /> },
          { path: '*', element: <NoEncontrada /> },
        ],
      },
      {
        element: <MarcoFlujo />,
        children: [{ path: '/reservar', ...perezosa(() => import('@/paginas/portal/reservar/Reservar')) }],
      },
      { path: '/pasarela/:ref', ...perezosa(() => import('@/paginas/portal/Pasarela')) },
      { path: '/admin/login', ...perezosa(() => import('@/paginas/panel/Login')) },
      { path: '/admin/recuperar', ...perezosa(() => import('@/paginas/panel/Recuperar')) },
      {
        path: '/admin',
        element: <RutaProtegida />,
        children: [
          {
            element: <PanelLayout />,
            children: [
              { index: true, element: <Navigate to="/admin/hoy" replace /> },
              { path: '/admin/cuenta', ...perezosa(() => import('@/paginas/panel/Cuenta')) },
              // v2: WhatsApp es una tarjeta de Configuración y las reseñas viven en la ficha de cada cancha.
              { path: '/admin/whatsapp', element: <Navigate to="/admin/configuracion/whatsapp" replace /> },
              { path: '/admin/resenas/*', element: <Navigate to="/admin/canchas" replace /> },
              ...MENU_PANEL.map<RouteObject>((item) => ({
                path: item.ruta,
                element: <RutaProtegida roles={item.roles} />,
                children: [
                  { index: true, ...(PAGINAS_PANEL[item.ruta] ?? perezosa(() => import('@/paginas/panel/EnConstruccion'))) },
                  { path: '*', ...(PAGINAS_PANEL[item.ruta] ?? perezosa(() => import('@/paginas/panel/EnConstruccion'))) },
                ],
              })),
              { path: '*', ...perezosa(() => import('@/paginas/panel/EnConstruccion')) },
            ],
          },
        ],
      },
    ],
  },
], {
  future: { v7_relativeSplatPath: true, v7_fetcherPersist: true, v7_normalizeFormMethod: true, v7_partialHydration: true, v7_skipActionErrorRevalidation: true },
});
