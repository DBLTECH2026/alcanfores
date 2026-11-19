import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

// Tipografías autoalojadas (subconjunto latino: cubre á é í ó ú ñ ¿ ¡).
import '@fontsource/inter/latin-400.css';
import '@fontsource/inter/latin-500.css';
import '@fontsource/inter/latin-600.css';
import '@fontsource/inter/latin-700.css';
import '@fontsource/barlow-semi-condensed/latin-600.css';
import '@fontsource/barlow-semi-condensed/latin-700.css';
import '@fontsource/barlow-semi-condensed/latin-800.css';
import '@fontsource/barlow-semi-condensed/latin-700-italic.css';
import '@fontsource/barlow-semi-condensed/latin-800-italic.css';

// Orden: base de Tailwind → sistema del prototipo → panel ELESS → piezas nuevas → utilidades de Tailwind (ganan).
import './estilos/tailwind.css';
import './estilos/alcanfores.css';
import './estilos/panel.css';
import './estilos/app.css';
import './estilos/utilidades.css';

import './utilidades/fecha';
import { router } from './rutas/router';

async function prepararMocks() {
  // Modo híbrido de desarrollo: solo los endpoints nuevos de la v2 simulados (ver src/mocks/v2.ts).
  if (import.meta.env.VITE_MOCK === 'v2') {
    const { iniciarMocksV2 } = await import('./mocks/v2');
    await iniciarMocksV2();
    return;
  }
  if (import.meta.env.VITE_MOCK !== 'true') return;
  const { iniciarMocks } = await import('./mocks/navegador');
  await iniciarMocks();
}

void prepararMocks().then(() => {
  const raiz = document.getElementById('root');
  if (!raiz) throw new Error('Falta #root');
  createRoot(raiz).render(
    <StrictMode>
      <RouterProvider router={router} future={{ v7_startTransition: true }} />
    </StrictMode>,
  );
});
