import { useEffect, useState } from 'react';
import { http } from '@/api/cliente';
import { rutaApi } from '@/api/admin';

/**
 * Archivos protegidos del panel (PDF, XML, Excel, imagen del comprobante): necesitan el token Bearer, así que no
 * sirven como <a href> directo. Se piden como Blob con el cliente HTTP y se abren o descargan con una URL local.
 */
export async function obtenerBlob(ruta: string, silenciar = false): Promise<Blob> {
  const r = await http.get<Blob>(rutaApi(ruta), { responseType: 'blob', timeout: 30000, silenciarToast: silenciar });
  return r.data;
}

/** Abre el archivo en una pestaña nueva (la ventana se abre en el clic para que el navegador no la bloquee). */
export async function abrirArchivo(ruta: string): Promise<void> {
  const ventana = window.open('', '_blank');
  try {
    const blob = await obtenerBlob(ruta);
    const url = URL.createObjectURL(blob);
    if (ventana) ventana.location.href = url;
    else window.location.href = url;
    window.setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch {
    ventana?.close();
  }
}

/** Descarga el archivo con el nombre dado. */
export async function descargarArchivo(ruta: string, nombre: string): Promise<void> {
  const blob = await obtenerBlob(ruta);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 10000);
}

/** URL local (blob:) de un archivo protegido, para <img> o <iframe>. `null` = no pedir. */
export function useArchivo(ruta: string | null): { url: string | null; cargando: boolean; error: boolean } {
  const [estado, setEstado] = useState<{ url: string | null; cargando: boolean; error: boolean }>({ url: null, cargando: Boolean(ruta), error: false });
  useEffect(() => {
    if (!ruta) {
      setEstado({ url: null, cargando: false, error: false });
      return undefined;
    }
    let vigente = true;
    let url: string | null = null;
    setEstado({ url: null, cargando: true, error: false });
    obtenerBlob(ruta, true)
      .then((b) => {
        url = URL.createObjectURL(b);
        if (vigente) setEstado({ url, cargando: false, error: false });
      })
      .catch(() => vigente && setEstado({ url: null, cargando: false, error: true }));
    return () => {
      vigente = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [ruta]);
  return estado;
}
