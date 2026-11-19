import { setupWorker } from 'msw/browser';
import { manejadores } from './manejadores';
import { obtenerBd, reiniciarBd } from './bd';

/** Arranca MSW en el navegador (solo con VITE_MOCK=true). Peticiones que no son /api pasan de largo. */
export async function iniciarMocks() {
  obtenerBd();
  const worker = setupWorker(...manejadores);
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true, serviceWorker: { url: '/mockServiceWorker.js' } });
  // Atajo para pruebas manuales en la consola: alcanforesMock.reiniciar()
  (window as unknown as { alcanforesMock: unknown }).alcanforesMock = { reiniciar: reiniciarBd, bd: obtenerBd };
  console.info('[mock] Alcanfores: back-end simulado con MSW (VITE_MOCK=true).');
}
