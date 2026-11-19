// Convierte el texto final del modelo en mensajes para el cliente: separa mensajes («---»), saca los botones
// («OPCIONES: a | b») y detecta el enlace de pago o de «Mi reserva» para mostrarlo como botón en la web.
import type { ExtrasMensaje } from "./modelo.js";

export interface MensajeSalida {
  texto: string;
  opciones?: string[];
  enlace?: { url: string; texto: string };
}

const MAX_MENSAJES = 3;
const MAX_OPCIONES = 5;

function enlaceDe(texto: string, frontUrl: string): MensajeSalida["enlace"] {
  const urls = texto.match(/https?:\/\/[^\s<>()]+/g) ?? [];
  for (const crudo of urls) {
    const url = crudo.replace(/[.,;:!?*]+$/, "");
    if (!url.startsWith(frontUrl)) continue;
    if (url.includes("/pasarela/")) return { url, texto: "Pagar en línea" };
    if (url.includes("/mi-reserva")) return { url, texto: "Ver mi reserva" };
  }
  return undefined;
}

/** Normaliza el formato: **negrita** → *negrita*, sin títulos markdown ni enlaces [texto](url). */
export function limpiarFormato(texto: string): string {
  return texto
    .replace(/\*\*(.+?)\*\*/g, "*$1*")
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, "$1: $2")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function interpretarRespuesta(bruto: string, frontUrl: string): MensajeSalida[] {
  const lineas = limpiarFormato(bruto).split("\n");
  let opciones: string[] | undefined;
  const resto: string[] = [];
  for (const linea of lineas) {
    const m = /^\s*OPCIONES\s*:\s*(.+)$/i.exec(linea);
    if (m) {
      opciones = m[1].split("|").map((o) => o.trim().replace(/^\*|\*$/g, "")).filter((o) => o && o.length <= 60).slice(0, MAX_OPCIONES);
    } else {
      resto.push(linea);
    }
  }
  const bloques = resto.join("\n").split(/^\s*---+\s*$/m).map((b) => b.trim()).filter(Boolean);
  if (bloques.length > MAX_MENSAJES) {
    bloques.splice(MAX_MENSAJES - 1, bloques.length, bloques.slice(MAX_MENSAJES - 1).join("\n\n"));
  }
  if (bloques.length === 0) bloques.push("Disculpa, no te entendí bien. ¿Me lo repites?");
  return bloques.map((texto, i) => {
    const salida: MensajeSalida = { texto };
    const enlace = enlaceDe(texto, frontUrl);
    if (enlace) salida.enlace = enlace;
    if (i === bloques.length - 1 && opciones?.length) salida.opciones = opciones;
    return salida;
  });
}

export function extrasDe(m: MensajeSalida): ExtrasMensaje | null {
  if (!m.opciones && !m.enlace) return null;
  return { ...(m.opciones ? { opciones: m.opciones } : {}), ...(m.enlace ? { enlace: m.enlace } : {}) };
}
