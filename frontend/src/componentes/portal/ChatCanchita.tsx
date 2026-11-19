import { Fragment, useCallback, useEffect, useId, useRef, useState, type CSSProperties, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router-dom';
import { CreditCard, ExternalLink, ImagePlus, MessageCircle, RotateCcw, SendHorizontal, Ticket, X } from 'lucide-react';
import { asistenteApi, sesionChat, type EnlaceCanchita, type MensajeHistorial, type RolMensaje, type SolicitudMensaje } from '@/api/asistente';
import { esErrorApi } from '@/api/cliente';
import type { Complejo } from '@/api/tipos';
import { useComplejo } from '@/hooks/useDatosPublicos';
import { cx } from '@/utilidades/cx';
import { MODO_MOCK } from '@/utilidades/recursos';
import './chat-canchita.css';

/**
 * Canchita en el portal (docs/SPEC_IA.md §5): botón flotante abajo a la derecha con su avatar y, al tocarlo, el chat.
 *  - Celular (≤ 640 px): panel a pantalla completa que entra de DERECHA a IZQUIERDA (mismo lenguaje que PanelLateral).
 *  - Escritorio: ventana de 380 × 600 anclada abajo a la derecha (no bloquea la página).
 * Respuesta en streaming (SSE) con respaldo a JSON, chips de opciones, enlaces como botones y foto del comprobante
 * comprimida en el navegador. Se muestra solo si el chat web está activo; nunca en /pasarela ni en /admin.
 */

const AVATAR = '/canchita/canchita-avatar.webp';
const SALUDO = '/canchita/canchita-saludo.webp';
const CLAVE_GLOBO = 'alc-canchita-globo';
const SALIDA_MS = 200;
const TIEMPO_MAXIMO_MS = 90_000;
const MAX_TEXTO = 1000;
const FOTO_LADO_MAX = 1600;
const FOTO_BYTES_MAX = 1.5 * 1024 * 1024;
const INICIOS = ['Quiero reservar', 'Ver precios', 'Consultar mi reserva'];
/** Rutas del portal que se abren dentro de la app (el resto, en otra pestaña). */
const RUTAS_INTERNAS = /^\/(pasarela|mi-reserva|mi-cuenta|reservar|canchas)(\/|$)/;

interface Burbuja {
  id: string;
  rol: RolMensaje;
  texto: string;
  /** data URL de la foto enviada en esta sesión, o `true` si viene del historial (sin imagen). */
  imagen?: string | true;
  opciones?: string[];
  enlace?: EnlaceCanchita;
}

interface FallaEnvio {
  mensaje: string;
  reintentar?: SolicitudMensaje;
  conWhatsapp?: boolean;
}

type Fase = 'cerrado' | 'abierto' | 'saliendo';

let contadorLocal = 0;
const idLocal = () => `l${Date.now().toString(36)}${(contadorLocal++).toString(36)}`;

const desdeHistorial = (m: MensajeHistorial): Burbuja => ({
  id: `h${m.id}`,
  rol: m.rol,
  texto: m.texto,
  ...(m.imagen ? { imagen: true as const } : {}),
  ...(m.opciones?.length ? { opciones: m.opciones } : {}),
  ...(m.enlace ? { enlace: m.enlace } : {}),
});

/** wa.me/51XXXXXXXXX (mismo criterio que el marco del portal; aquí para no importar el marco desde el chat). */
function enlaceWhatsapp(numero: string | undefined, texto: string): string {
  const d = String(numero ?? '914995187').replace(/\D/g, '') || '914995187';
  return `https://wa.me/${d.startsWith('51') ? d : `51${d}`}?text=${encodeURIComponent(texto)}`;
}

const reducirMovimiento = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function useEsCelular(): boolean {
  const consulta = '(max-width: 640px)';
  const [es, setEs] = useState(() => window.matchMedia(consulta).matches);
  useEffect(() => {
    const mq = window.matchMedia(consulta);
    const cambio = () => setEs(mq.matches);
    mq.addEventListener('change', cambio);
    return () => mq.removeEventListener('change', cambio);
  }, []);
  return es;
}

/**
 * Cuánto hay que subir el botón para no tapar la barra inferior fija del flujo de reserva (`.barra-fija`, < 1024 px).
 * Mide dónde empieza la barra respecto del borde inferior de la ventana (crece si se despliega su resumen).
 */
function useDesplazoBarra(activo: boolean): number {
  const [desplazo, setDesplazo] = useState(0);
  useEffect(() => {
    if (!activo) {
      setDesplazo(0);
      return undefined;
    }
    let barra: HTMLElement | null = null;
    let cuadro = 0;
    const ro = new ResizeObserver(() => medir());
    function medir() {
      cancelAnimationFrame(cuadro);
      cuadro = requestAnimationFrame(() => {
        const b = document.querySelector<HTMLElement>('.barra-fija');
        if (b !== barra) {
          if (barra) ro.unobserve(barra);
          barra = b;
          if (b) ro.observe(b);
        }
        let d = 0;
        if (b && getComputedStyle(b).display !== 'none') {
          const r = b.getBoundingClientRect();
          if (r.height > 0 && r.top < window.innerHeight) d = Math.round(window.innerHeight - r.top);
        }
        setDesplazo((previo) => (previo === d ? previo : d));
      });
    }
    const mo = new MutationObserver(medir);
    mo.observe(document.getElementById('contenido') ?? document.body, { childList: true, subtree: true });
    window.addEventListener('scroll', medir, { passive: true });
    window.addEventListener('resize', medir);
    medir();
    return () => {
      cancelAnimationFrame(cuadro);
      ro.disconnect();
      mo.disconnect();
      window.removeEventListener('scroll', medir);
      window.removeEventListener('resize', medir);
    };
  }, [activo]);
  return desplazo;
}

// ------------------------------ foto del comprobante ------------------------------
function cargarImagen(archivo: File): Promise<HTMLImageElement> {
  return new Promise((ok, falla) => {
    const url = URL.createObjectURL(archivo);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      ok(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      falla(new Error('No pudimos abrir esa imagen. Prueba con una captura JPG o PNG.'));
    };
    img.src = url;
  });
}

function blobADataUrl(blob: Blob): Promise<string> {
  return new Promise((ok, falla) => {
    const lector = new FileReader();
    lector.onload = () => ok(String(lector.result));
    lector.onerror = () => falla(new Error('No pudimos leer la imagen.'));
    lector.readAsDataURL(blob);
  });
}

/** JPG de hasta 1600 px por lado y 1,5 MB (baja la calidad y luego el tamaño hasta que entre). */
async function comprimirFoto(archivo: File): Promise<{ dataUrl: string; bytes: number }> {
  if (!archivo.type.startsWith('image/')) throw new Error('Sube una imagen (captura o foto del comprobante).');
  if (archivo.size > 30 * 1024 * 1024) throw new Error('La imagen pesa demasiado. Sube una captura de pantalla.');
  const img = await cargarImagen(archivo);
  const lado = Math.max(img.naturalWidth, img.naturalHeight) || 1;
  let escala = Math.min(1, FOTO_LADO_MAX / lado);
  let calidad = 0.86;
  const lienzo = document.createElement('canvas');
  const ctx = lienzo.getContext('2d');
  if (!ctx) throw new Error('Tu navegador no pudo preparar la imagen.');
  for (let intento = 0; intento < 10; intento++) {
    lienzo.width = Math.max(1, Math.round(img.naturalWidth * escala));
    lienzo.height = Math.max(1, Math.round(img.naturalHeight * escala));
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, lienzo.width, lienzo.height);
    ctx.drawImage(img, 0, 0, lienzo.width, lienzo.height);
    const blob = await new Promise<Blob | null>((ok) => lienzo.toBlob(ok, 'image/jpeg', calidad));
    if (!blob) break;
    if (blob.size <= FOTO_BYTES_MAX) return { dataUrl: await blobADataUrl(blob), bytes: blob.size };
    if (calidad > 0.62) calidad -= 0.12;
    else escala *= 0.8;
  }
  throw new Error('No pudimos achicar la imagen. Prueba con una captura de pantalla.');
}

const fmtPeso = (bytes: number) => (bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

// ------------------------------ texto de los mensajes ------------------------------
const PATRON_EN_LINEA = /(\*[^*\n]+\*)|(https?:\/\/[^\s<>()]+)/g;

/** *negrita* y enlaces sueltos de una línea. */
function enLinea(linea: string, clave: string): ReactNode[] {
  const piezas: ReactNode[] = [];
  let ultimo = 0;
  let n = 0;
  for (const m of linea.matchAll(PATRON_EN_LINEA)) {
    const i = m.index ?? 0;
    if (i > ultimo) piezas.push(linea.slice(ultimo, i));
    if (m[1]) {
      piezas.push(<b key={`${clave}-${n++}`}>{m[1].slice(1, -1)}</b>);
      ultimo = i + m[1].length;
    } else if (m[2]) {
      const url = m[2].replace(/[.,;:!?*]+$/, '');
      piezas.push(
        <a key={`${clave}-${n++}`} href={url} target="_blank" rel="noopener noreferrer">
          {url.replace(/^https?:\/\//, '')}
        </a>,
      );
      ultimo = i + url.length;
    }
  }
  if (ultimo < linea.length) piezas.push(linea.slice(ultimo));
  return piezas;
}

/** Párrafos y viñetas («- …») con *negritas* de WhatsApp. Quita del texto la URL que ya va como botón. */
function TextoMensaje({ texto, quitar }: { texto: string; quitar?: string }) {
  let limpio = texto;
  if (quitar && limpio.includes(quitar)) limpio = limpio.split(quitar).join('').replace(/[ \t]+\n/g, '\n').replace(/[ \t]{2,}/g, ' ').trim();
  const lineas = limpio.split('\n');
  const bloques: ReactNode[] = [];
  let lista: ReactNode[] = [];
  const cerrarLista = () => {
    if (lista.length) bloques.push(<ul key={`u${bloques.length}`}>{lista}</ul>);
    lista = [];
  };
  lineas.forEach((l, i) => {
    const vineta = /^\s*[-•]\s+(.*)$/.exec(l);
    if (vineta) {
      lista.push(<li key={i}>{enLinea(vineta[1] ?? '', `l${i}`)}</li>);
      return;
    }
    cerrarLista();
    if (l.trim()) bloques.push(<p key={i}>{enLinea(l, `p${i}`)}</p>);
  });
  cerrarLista();
  return <>{bloques}</>;
}

// ------------------------------ componente ------------------------------
export function ChatCanchita({ modo }: { modo: 'completo' | 'flujo' }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { datos: complejo } = useComplejo();
  const webActivaComplejo = (complejo as (Complejo & { asistente?: { webActiva?: boolean } }) | null)?.asistente?.webActiva;
  const [activa, setActiva] = useState<boolean | null>(null);
  const [estadoListo, setEstadoListo] = useState(false);

  useEffect(() => {
    if (MODO_MOCK) return undefined;
    let vigente = true;
    asistenteApi
      .estadoWeb()
      .then((e) => vigente && setActiva(e.activa))
      .catch(() => vigente && setActiva(null))
      .finally(() => vigente && setEstadoListo(true));
    return () => {
      vigente = false;
    };
  }, []);

  const rutaPermitida = !/^\/(pasarela|admin)(\/|$)/.test(pathname);
  // Manda el estado del servicio; si no respondió, el dato público del complejo.
  const visible = !MODO_MOCK && rutaPermitida && estadoListo && (activa ?? webActivaComplejo === true);
  if (!visible) return null;
  return <Chat modo={modo} whatsapp={complejo?.whatsapp} navegar={navigate} />;
}

function Chat({ modo, whatsapp, navegar }: { modo: 'completo' | 'flujo'; whatsapp: string | undefined; navegar: (ruta: string) => void }) {
  const idTitulo = useId();
  const idLista = useId();
  const esCelular = useEsCelular();
  const desplazo = useDesplazoBarra(true);
  const [fase, setFase] = useState<Fase>('cerrado');
  const abierto = fase === 'abierto';
  const abiertoRef = useRef(false);
  abiertoRef.current = abierto;
  const [sesion] = useState(sesionChat);
  const [mensajes, setMensajes] = useState<Burbuja[]>([]);
  const [cargandoHist, setCargandoHist] = useState(false);
  const historialPedido = useRef(false);
  const [esperando, setEsperando] = useState(false);
  const [textoEspera, setTextoEspera] = useState<string | null>(null);
  const [falla, setFalla] = useState<FallaEnvio | null>(null);
  const [texto, setTexto] = useState('');
  const [foto, setFoto] = useState<{ dataUrl: string; bytes: number } | null>(null);
  const [preparandoFoto, setPreparandoFoto] = useState(false);
  const [errorFoto, setErrorFoto] = useState<string | null>(null);
  const [sinLeer, setSinLeer] = useState(0);
  const [globo, setGlobo] = useState(false);
  const [vista, setVista] = useState<{ alto: number; arriba: number } | null>(null);

  const fab = useRef<HTMLButtonElement>(null);
  const caja = useRef<HTMLDivElement>(null);
  const lista = useRef<HTMLDivElement>(null);
  const campo = useRef<HTMLTextAreaElement>(null);
  const archivo = useRef<HTMLInputElement>(null);
  const envio = useRef<AbortController | null>(null);
  const devolverFoco = useRef(false);

  // Globo «¿Te ayudo a reservar?»: solo la primera vez (se recuerda al mostrarlo) y se va solo a los 12 s.
  useEffect(() => {
    // En el flujo de reserva el globo taparía los turnos: allí solo queda el botón (más chico en celular).
    if (modo === 'flujo') return undefined;
    let visto = true;
    try {
      visto = Boolean(localStorage.getItem(CLAVE_GLOBO));
    } catch {
      visto = true;
    }
    if (visto) return undefined;
    const mostrar = window.setTimeout(() => {
      setGlobo(true);
      try {
        localStorage.setItem(CLAVE_GLOBO, '1');
      } catch {
        /* sin localStorage */
      }
    }, 1500);
    const ocultar = window.setTimeout(() => setGlobo(false), 13_500);
    return () => {
      window.clearTimeout(mostrar);
      window.clearTimeout(ocultar);
    };
  }, [modo]);

  // Al salir de la pantalla corta el envío en curso (la respuesta queda en el historial).
  useEffect(() => () => envio.current?.abort(), []);

  const abrir = () => {
    setGlobo(false);
    setSinLeer(0);
    setFase('abierto');
  };
  const cerrar = useCallback(() => {
    devolverFoco.current = true;
    if (reducirMovimiento()) setFase('cerrado');
    else setFase('saliendo');
  }, []);

  useEffect(() => {
    if (fase !== 'saliendo') return undefined;
    const t = window.setTimeout(() => setFase('cerrado'), SALIDA_MS);
    return () => window.clearTimeout(t);
  }, [fase]);

  // Al cerrar, el foco vuelve al botón flotante.
  useEffect(() => {
    if (fase === 'cerrado' && devolverFoco.current) {
      devolverFoco.current = false;
      fab.current?.focus({ preventScroll: true });
    }
  }, [fase]);

  // Historial al abrir por primera vez.
  useEffect(() => {
    if (!abierto || historialPedido.current) return undefined;
    historialPedido.current = true;
    setCargandoHist(true);
    // Sin limpieza: si se cierra el chat mientras carga, el historial igual queda para la próxima vez.
    asistenteApi
      .historial(sesion)
      .then((h) => setMensajes((previos) => (previos.length ? previos : h.mensajes.map(desdeHistorial))))
      .catch(() => {
        historialPedido.current = false;
      })
      .finally(() => setCargandoHist(false));
    return undefined;
  }, [abierto, sesion]);

  // Teclado a nivel del documento (el foco puede quedar suelto cuando desaparece el botón que se tocó):
  // Esc cierra; en celular (modal) Tab no sale del panel. Si el foco está en un modal de la página, no se toca.
  useEffect(() => {
    if (!abierto) return undefined;
    const alTeclear = (e: globalThis.KeyboardEvent) => {
      const c = caja.current;
      if (!c) return;
      const activo = document.activeElement;
      const suelto = !activo || activo === document.body;
      if (!suelto && !c.contains(activo)) return;
      if (e.key === 'Escape') {
        cerrar();
        return;
      }
      if (e.key !== 'Tab' || !esCelular) return;
      const items = Array.from(c.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), textarea:not([disabled])'));
      const primero = items[0];
      const ultimo = items[items.length - 1];
      if (!primero || !ultimo) return;
      if (suelto || activo === c || (e.shiftKey && activo === primero) || (!e.shiftKey && activo === ultimo)) {
        e.preventDefault();
        (e.shiftKey ? ultimo : primero).focus();
      }
    };
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [abierto, cerrar, esCelular]);

  // Abierto: foco dentro (en celular al panel, para no subir el teclado de golpe; en escritorio al campo).
  useEffect(() => {
    if (!abierto) return undefined;
    const t = window.setTimeout(() => {
      if (esCelular) caja.current?.focus({ preventScroll: true });
      else campo.current?.focus({ preventScroll: true });
    }, 40);
    return () => window.clearTimeout(t);
    // Solo al abrir; si cambia el ancho con el chat abierto no se mueve el foco.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto]);

  // Celular: bloquea el scroll de la página y sigue el alto visible (teclado en pantalla).
  useEffect(() => {
    if (!abierto || !esCelular) {
      setVista(null);
      return undefined;
    }
    const previo = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const vv = window.visualViewport;
    const ajustar = () => {
      if (!vv) return;
      const alto = Math.round(vv.height);
      const arriba = Math.round(vv.offsetTop);
      setVista((v) => (v && v.alto === alto && v.arriba === arriba ? v : { alto, arriba }));
    };
    ajustar();
    vv?.addEventListener('resize', ajustar);
    vv?.addEventListener('scroll', ajustar);
    return () => {
      document.body.style.overflow = previo;
      vv?.removeEventListener('resize', ajustar);
      vv?.removeEventListener('scroll', ajustar);
    };
  }, [abierto, esCelular]);

  // Siempre al último mensaje.
  useEffect(() => {
    const el = lista.current;
    if (!el || !abierto) return;
    el.scrollTo({ top: el.scrollHeight, behavior: reducirMovimiento() ? 'auto' : 'smooth' });
  }, [mensajes, esperando, falla, abierto, cargandoHist]);

  // Campo que crece hasta 4 líneas.
  useEffect(() => {
    const el = campo.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 116)}px`;
  }, [texto, abierto]);

  const mandar = async (sol: SolicitudMensaje, burbuja: Burbuja | null) => {
    if (esperando) return;
    if (burbuja) setMensajes((m) => [...m, burbuja]);
    setFalla(null);
    setEsperando(true);
    setTextoEspera(null);
    const ctrl = new AbortController();
    envio.current = ctrl;
    let porTiempo = false;
    const reloj = window.setTimeout(() => {
      porTiempo = true;
      ctrl.abort();
    }, TIEMPO_MAXIMO_MS);
    try {
      await asistenteApi.enviar(
        sol,
        {
          escribiendo: () => setTextoEspera(null),
          herramienta: (t) => setTextoEspera(t.trim() || null),
          mensaje: (m) => {
            setTextoEspera(null);
            setMensajes((lista_) => [...lista_, { id: idLocal(), rol: 'CANCHITA', texto: m.texto, ...(m.opciones?.length ? { opciones: m.opciones } : {}), ...(m.enlace ? { enlace: m.enlace } : {}) }]);
            if (!abiertoRef.current) setSinLeer((n) => n + 1);
          },
        },
        ctrl.signal,
      );
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        if (porTiempo) setFalla({ mensaje: 'Canchita tardó demasiado. Reintenta.', reintentar: sol });
        return;
      }
      if (esErrorApi(e)) {
        if (e.codigo === 'CANCHITA_APAGADA') setFalla({ mensaje: 'Canchita no está disponible ahora. Escríbenos por WhatsApp.', conWhatsapp: true });
        else if (e.codigo === 'VALIDACION') setFalla({ mensaje: e.errores[0]?.mensaje ?? e.message });
        else setFalla({ mensaje: e.message, reintentar: sol, conWhatsapp: e.status >= 500 });
      } else {
        setFalla({ mensaje: 'No pude responder ahora. Intenta en unos segundos.', reintentar: sol });
      }
    } finally {
      window.clearTimeout(reloj);
      if (envio.current === ctrl) envio.current = null;
      setEsperando(false);
      setTextoEspera(null);
    }
  };

  /** El chip o el botón que se tocó desaparece al enviar: el foco pasa al campo (en celular, al panel, sin abrir el teclado). */
  const enfocarEntrada = () => {
    if (esCelular) caja.current?.focus({ preventScroll: true });
    else campo.current?.focus({ preventScroll: true });
  };

  const enviarTexto = (valor: string) => {
    const t = valor.trim().slice(0, MAX_TEXTO);
    if (!t || esperando) return;
    void mandar({ sesion, texto: t }, { id: idLocal(), rol: 'CLIENTE', texto: t });
    enfocarEntrada();
  };

  const alEnviar = (e?: FormEvent) => {
    e?.preventDefault();
    if (esperando || preparandoFoto) return;
    const t = texto.trim().slice(0, MAX_TEXTO);
    if (!t && !foto) return;
    const sol: SolicitudMensaje = { sesion, ...(t ? { texto: t } : {}), ...(foto ? { imagen: foto.dataUrl } : {}) };
    void mandar(sol, { id: idLocal(), rol: 'CLIENTE', texto: t, ...(foto ? { imagen: foto.dataUrl } : {}) });
    setTexto('');
    setFoto(null);
    setErrorFoto(null);
    if (document.activeElement !== campo.current) enfocarEntrada();
  };

  const alTeclearCampo = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      alEnviar();
    }
  };

  const elegirFoto = async (f: File | undefined) => {
    if (!f) return;
    setErrorFoto(null);
    setPreparandoFoto(true);
    try {
      setFoto(await comprimirFoto(f));
    } catch (err) {
      setFoto(null);
      setErrorFoto(err instanceof Error ? err.message : 'No pudimos preparar la imagen.');
    } finally {
      setPreparandoFoto(false);
      if (archivo.current) archivo.current.value = '';
    }
  };

  const abrirEnlace = (enlace: EnlaceCanchita) => {
    let destino: URL | null = null;
    try {
      destino = new URL(enlace.url, window.location.origin);
    } catch {
      destino = null;
    }
    if (destino && RUTAS_INTERNAS.test(destino.pathname)) {
      if (esCelular) cerrar();
      navegar(`${destino.pathname}${destino.search}`);
      return;
    }
    window.open(enlace.url, '_blank', 'noopener,noreferrer');
  };

  const ultimo = mensajes[mensajes.length - 1];
  const chips = !esperando && !falla && ultimo?.rol === 'CANCHITA' && ultimo.opciones?.length ? ultimo.opciones : null;
  const vacio = !cargandoHist && mensajes.length === 0;
  const puedeEnviar = !esperando && !preparandoFoto && (texto.trim().length > 0 || foto !== null);
  const montado = fase !== 'cerrado';
  // Si la barra del flujo se despliega y ocupa media pantalla, el botón se aparta.
  const tapado = desplazo > window.innerHeight * 0.55;
  const estiloDesplazo = { '--cc-desplazo': `${tapado ? 0 : desplazo}px` } as CSSProperties;
  const estiloPanel = { ...estiloDesplazo, ...(vista ? { height: `${vista.alto}px`, top: `${vista.arriba}px` } : {}) } as CSSProperties;

  return createPortal(
    <div className={cx('cc', `cc-${modo}`)}>
      {!abierto && globo && !tapado && (
        <div className="cc-globo" style={estiloDesplazo}>
          <button type="button" className="cc-globo-txt" onClick={abrir}>
            ¿Te ayudo a reservar?
          </button>
          <button type="button" className="cc-globo-x" aria-label="Cerrar sugerencia" onClick={() => setGlobo(false)}>
            <X className="ico ico-14" aria-hidden />
          </button>
        </div>
      )}

      <button
        ref={fab}
        type="button"
        className={cx('cc-fab', modo === 'flujo' && 'en-flujo', (montado || tapado) && 'oculto')}
        style={estiloDesplazo}
        aria-label={sinLeer ? `Abrir chat con Canchita, ${sinLeer} mensaje${sinLeer === 1 ? '' : 's'} nuevo${sinLeer === 1 ? '' : 's'}` : 'Abrir chat con Canchita'}
        aria-haspopup="dialog"
        aria-expanded={abierto}
        tabIndex={montado || tapado ? -1 : 0}
        aria-hidden={montado || tapado ? true : undefined}
        onClick={abrir}
      >
        <img src={AVATAR} alt="" width={60} height={60} draggable={false} />
        {sinLeer > 0 && <span className="cc-fab-n">{sinLeer}</span>}
      </button>

      {montado && (
        <div className={cx('cc-capa', fase === 'saliendo' && 'saliendo')}>
          <div
            ref={caja}
            className="cc-panel"
            style={estiloPanel}
            role="dialog"
            aria-modal={esCelular ? true : undefined}
            aria-labelledby={idTitulo}
            tabIndex={-1}
          >
            <header className="cc-cab">
              <span className="cc-cab-av">
                <img src={AVATAR} alt="" width={40} height={40} />
                <i aria-hidden />
              </span>
              <div className="cc-cab-txt">
                <h2 id={idTitulo}>Canchita</h2>
                <span>Asistente de reservas</span>
              </div>
              <a className="cc-cab-btn" href={enlaceWhatsapp(whatsapp, 'Hola, necesito ayuda con una reserva.')} target="_blank" rel="noopener noreferrer" aria-label="Hablar con una persona por WhatsApp" title="Hablar con una persona">
                <MessageCircle className="ico ico-18" aria-hidden />
              </a>
              <button type="button" className="cc-cab-btn" aria-label="Cerrar chat" onClick={cerrar}>
                <X className="ico ico-18" aria-hidden />
              </button>
            </header>

            <div ref={lista} id={idLista} className="cc-lista" role="log" aria-live="polite" aria-relevant="additions" aria-busy={cargandoHist}>
              {cargandoHist && (
                <div className="cc-cargando">
                  <span className="spinner" aria-hidden />
                  <span>Cargando la conversación…</span>
                </div>
              )}
              {vacio && (
                <div className="cc-bienvenida">
                  <img src={SALUDO} alt="" width={132} height={132} />
                  <b>¡Hola! Soy Canchita</b>
                  <span>Te ayudo a reservar tu cancha en minutos.</span>
                </div>
              )}
              {mensajes.map((m, i) => {
                const anterior = mensajes[i - 1];
                const nuevoGrupo = !anterior || anterior.rol !== m.rol;
                return (
                  <Fragment key={m.id}>
                    {m.rol === 'EQUIPO' && nuevoGrupo && <span className="cc-quien">Equipo Alcanfores</span>}
                    <div className={cx('cc-msj', m.rol === 'CLIENTE' ? 'cc-yo' : m.rol === 'EQUIPO' ? 'cc-equipo' : 'cc-ella', nuevoGrupo && 'cc-grupo')}>
                      <div className="cc-burbuja">
                        {m.imagen &&
                          (m.imagen === true ? (
                            <span className="cc-foto-ref">
                              <ImagePlus className="ico ico-16" aria-hidden />
                              Foto del comprobante
                            </span>
                          ) : (
                            <img className="cc-foto" src={m.imagen} alt="Foto del comprobante enviada" />
                          ))}
                        {m.texto && <TextoMensaje texto={m.texto} quitar={m.enlace?.url} />}
                        {m.enlace && (
                          <button type="button" className="cc-enlace" onClick={() => abrirEnlace(m.enlace as EnlaceCanchita)}>
                            {m.enlace.url.includes('/pasarela/') ? <CreditCard className="ico ico-18" aria-hidden /> : m.enlace.url.includes('/mi-reserva') ? <Ticket className="ico ico-18" aria-hidden /> : <ExternalLink className="ico ico-18" aria-hidden />}
                            {m.enlace.texto}
                          </button>
                        )}
                      </div>
                    </div>
                  </Fragment>
                );
              })}
              {esperando && (
                <div className="cc-msj cc-ella cc-grupo cc-escribiendo" role="status">
                  <div className="cc-burbuja">
                    <span className="cc-puntos" aria-hidden>
                      <i />
                      <i />
                      <i />
                    </span>
                    <span>{textoEspera ?? 'Canchita está escribiendo…'}</span>
                  </div>
                </div>
              )}
              {falla && (
                <div className="cc-falla" role="alert">
                  <span>{falla.mensaje}</span>
                  <span className="cc-falla-acc">
                    {falla.reintentar && (
                      <button
                        type="button"
                        onClick={() => {
                          if (falla.reintentar) void mandar(falla.reintentar, null);
                          enfocarEntrada();
                        }}
                      >
                        <RotateCcw className="ico ico-16" aria-hidden />
                        Reintentar
                      </button>
                    )}
                    {falla.conWhatsapp && (
                      <a href={enlaceWhatsapp(whatsapp, 'Hola, quiero reservar una cancha.')} target="_blank" rel="noopener noreferrer">
                        <MessageCircle className="ico ico-16" aria-hidden />
                        WhatsApp
                      </a>
                    )}
                  </span>
                </div>
              )}
            </div>

            {(chips || (vacio && !esperando)) && (
              <div className="cc-chips" aria-label="Respuestas rápidas" role="group">
                {(chips ?? INICIOS).map((o) => (
                  <button key={o} type="button" className="cc-chip" onClick={() => enviarTexto(o)}>
                    {o}
                  </button>
                ))}
              </div>
            )}

            <form className="cc-entrada" onSubmit={alEnviar} noValidate>
              {(foto || preparandoFoto) && (
                <div className="cc-adjunto">
                  {foto ? <img src={foto.dataUrl} alt="Vista previa del comprobante" /> : <span className="spinner" aria-hidden />}
                  <span className="cc-adjunto-txt">
                    <b>{foto ? 'Comprobante listo' : 'Preparando la foto…'}</b>
                    {foto && <span>JPG · {fmtPeso(foto.bytes)}</span>}
                  </span>
                  {foto && (
                    <button
                      type="button"
                      className="cc-adjunto-x"
                      aria-label="Quitar la foto"
                      onClick={() => {
                        setFoto(null);
                        enfocarEntrada();
                      }}
                    >
                      <X className="ico ico-16" aria-hidden />
                    </button>
                  )}
                </div>
              )}
              {errorFoto && (
                <p className="cc-error-foto" role="alert">
                  {errorFoto}
                </p>
              )}
              <div className="cc-entrada-fila">
                <button type="button" className="cc-adjuntar" aria-label="Adjuntar foto del comprobante" title="Foto del comprobante" disabled={esperando || preparandoFoto} onClick={() => archivo.current?.click()}>
                  <ImagePlus className="ico" aria-hidden />
                </button>
                <input ref={archivo} type="file" accept="image/*" hidden tabIndex={-1} onChange={(e) => void elegirFoto(e.target.files?.[0])} />
                <textarea
                  ref={campo}
                  className="cc-campo"
                  rows={1}
                  value={texto}
                  maxLength={MAX_TEXTO}
                  placeholder={foto ? 'Agrega un mensaje (opcional)' : 'Escribe tu mensaje…'}
                  aria-label="Mensaje para Canchita"
                  aria-controls={idLista}
                  enterKeyHint="send"
                  onChange={(e) => setTexto(e.target.value)}
                  onKeyDown={alTeclearCampo}
                />
                <button type="submit" className="cc-enviar" aria-label="Enviar mensaje" disabled={!puedeEnviar} onMouseDown={(e) => e.preventDefault()}>
                  <SendHorizontal className="ico" aria-hidden />
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>,
    document.body,
  );
}
