import type { ComponentType } from 'react';
import { Navigate, Outlet, createBrowserRouter, useRouteError, type RouteObject } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import { AuthProvider } from '@/contexto/AuthContext';
import { ToastProvider } from '@/contexto/ToastContext';
import { MarcoPortal } from '@/componentes/portal/MarcoPortal';
import { PanelLayout } from '@/componentes/panel/PanelLayout';
import { Vacio } from '@/componentes/ui/Datos';
import { Boton } from '@/componentes/ui/Boton';
import Inicio from '@/paginas/portal/Inicio';
import { NoEncontrada, Privacidad, Terminos } from '@/paginas/portal/Legales';
import { RutaProtegida } from './RutaProtegida';
import { MENU_PANEL } from './menuPanel';

/** Proveedores globales DENTRO del router (AuthContext usa useNavigate). */
function Raiz() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Outlet />
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
      {
        path: '/admin',
        element: <RutaProtegida />,
        children: [
          {
            element: <PanelLayout />,
            children: [
              { index: true, element: <Navigate to="/admin/hoy" replace /> },
              { path: '/admin/cuenta', ...perezosa(() => import('@/paginas/panel/Cuenta')) },
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
