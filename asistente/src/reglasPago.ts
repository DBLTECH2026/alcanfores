// Reglas de auto-validación del comprobante. Son CÓDIGO, no criterio del modelo: el modelo solo transcribe la foto
// (lectura) y aquí se decide si todo calza. Si algo no calza, el pago queda PENDIENTE para el equipo.
import type { LecturaComprobante } from "./modelo.js";
import { instanteDesdeLima } from "./tiempo.js";

export interface DatosEsperados {
  metodo: "YAPE" | "PLIN";
  codigoOperacion: string;
  montoAdelanto: number;
  /** Lo que falta pagar de la reserva (total − pagado). */
  saldo: number;
  yapeNumero: string;
  yapeTitular: string;
  plinNumero: string;
}

export interface Veredicto {
  calza: boolean;
  motivos: string[];
}

const VENTANA_MS = 24 * 60 * 60 * 1000;
const TOLERANCIA_FUTURO_MS = 15 * 60 * 1000;
const PALABRAS_GENERICAS = new Set(["sac", "eirl", "srl", "sa", "sociedad", "anonima", "cerrada", "de", "del", "la", "los", "las", "y"]);

function normalizar(texto: string): string {
  return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Palabras significativas de un nombre («Complejo Deport* Alcanfores» → complejo, deport, alcanfores). */
function palabras(texto: string): { palabra: string; truncada: boolean }[] {
  return normalizar(texto)
    .split(/[^a-z0-9*]+/)
    .filter(Boolean)
    .map((p) => ({ palabra: p.replace(/\*+/g, ""), truncada: p.includes("*") }))
    .filter((p) => p.palabra.length >= 2 && !PALABRAS_GENERICAS.has(p.palabra));
}

/**
 * El destinatario leído calza con el titular si TODAS sus palabras aparecen en el titular (completas o como inicio de
 * una palabra cuando la app las recorta con asteriscos) y son al menos dos. Así «Complejo Deportivo (cuenta de
 * ejemplo)» NO calza con «Complejo Deportivo Alcanfores».
 */
export function destinatarioCalza(leido: string | null, titular: string): boolean {
  if (!leido || !titular) return false;
  const delTitular = palabras(titular).map((p) => p.palabra);
  const leidas = palabras(leido);
  if (leidas.length < 2) return false;
  return leidas.every((l) => delTitular.some((t) => t === l.palabra || ((l.truncada || l.palabra.length >= 3) && t.startsWith(l.palabra))));
}

/** El número visible (completo o sus últimos 3 dígitos) calza con el número de Yape o Plin del complejo. */
export function numeroCalza(leido: string | null, numeroComplejo: string): boolean {
  const l = (leido ?? "").replace(/\D/g, "");
  const n = (numeroComplejo ?? "").replace(/\D/g, "");
  if (l.length < 3 || n.length < 3) return false;
  return l.length >= 9 ? l.slice(-9) === n.slice(-9) : n.endsWith(l);
}

function igualesEnSoles(a: number, b: number): boolean {
  return Math.abs(a - b) < 0.005;
}

export function evaluarComprobante(lectura: LecturaComprobante | null, esperado: DatosEsperados, momento: Date): Veredicto {
  const motivos: string[] = [];
  if (!lectura) return { calza: false, motivos: ["No hay lectura de la imagen."] };
  if (!lectura.esComprobante) motivos.push("La imagen no parece un comprobante de pago.");
  if (lectura.marcaDeAgua) motivos.push("La imagen dice EJEMPLO, MUESTRA o similar.");
  if (lectura.pareceEditada) motivos.push("La imagen parece editada.");

  if (lectura.monto == null) {
    motivos.push("No se leyó el monto.");
  } else if (!igualesEnSoles(lectura.monto, esperado.montoAdelanto) && !igualesEnSoles(lectura.monto, esperado.saldo)) {
    motivos.push(`El monto S/ ${lectura.monto.toFixed(2)} no es el adelanto (S/ ${esperado.montoAdelanto.toFixed(2)}) `
      + `ni el saldo pendiente (S/ ${esperado.saldo.toFixed(2)}).`);
  }

  const operacionLeida = (lectura.codigoOperacion ?? "").replace(/\D/g, "");
  if (!operacionLeida) {
    motivos.push("No se leyó el número de operación.");
  } else if (operacionLeida !== esperado.codigoOperacion.replace(/\D/g, "")) {
    motivos.push("El número de operación no coincide con el de la imagen.");
  }

  const titular = esperado.metodo === "YAPE" ? esperado.yapeTitular : "";
  const numero = esperado.metodo === "YAPE" ? esperado.yapeNumero : esperado.plinNumero;
  // Se acepta cualquiera de los dos números del complejo: hay clientes que yapean al número de Plin y viceversa.
  const numeroOk = numeroCalza(lectura.numeroDestino, numero)
    || numeroCalza(lectura.numeroDestino, esperado.yapeNumero) || numeroCalza(lectura.numeroDestino, esperado.plinNumero);
  const nombreOk = destinatarioCalza(lectura.destinatario, titular || esperado.yapeTitular);
  if (!numeroOk && !nombreOk) motivos.push("El destinatario no es el titular ni el número del complejo.");

  if (lectura.app && lectura.app !== "OTRO" && lectura.app !== esperado.metodo) {
    motivos.push(`La imagen es de ${lectura.app === "YAPE" ? "Yape" : "Plin"} y se declaró ${esperado.metodo === "YAPE" ? "Yape" : "Plin"}.`);
  }

  const fecha = instanteDesdeLima(lectura.fechaHora);
  if (!fecha) {
    motivos.push("No se leyó la fecha del pago.");
  } else {
    const diferencia = momento.getTime() - fecha.getTime();
    if (diferencia > VENTANA_MS) motivos.push("El pago tiene más de 24 horas.");
    if (diferencia < -TOLERANCIA_FUTURO_MS) motivos.push("La fecha del pago está en el futuro.");
  }

  return { calza: motivos.length === 0, motivos };
}
