// Reloj de Lima (UTC-5 todo el año, Perú no tiene horario de verano). Se puede fijar en las pruebas.

let fijo: Date | null = null;

export function fijarReloj(fecha: Date | null): void {
  fijo = fecha;
}

export function ahora(): Date {
  return fijo ? new Date(fijo.getTime()) : new Date();
}

const DESFASE_LIMA_MS = -5 * 60 * 60 * 1000;

/** Partes de la fecha y hora de Lima para un instante. */
function partesLima(d: Date): { anio: number; mes: number; dia: number; hora: number; minuto: number; diaSemana: number } {
  const l = new Date(d.getTime() + DESFASE_LIMA_MS);
  return {
    anio: l.getUTCFullYear(),
    mes: l.getUTCMonth() + 1,
    dia: l.getUTCDate(),
    hora: l.getUTCHours(),
    minuto: l.getUTCMinutes(),
    diaSemana: l.getUTCDay(),
  };
}

const dos = (n: number) => String(n).padStart(2, "0");

/** YYYY-MM-DD de Lima. */
export function fechaLima(d: Date = ahora()): string {
  const p = partesLima(d);
  return `${p.anio}-${dos(p.mes)}-${dos(p.dia)}`;
}

/** YYYY-MM-DDTHH:mm de Lima. */
export function fechaHoraLima(d: Date = ahora()): string {
  const p = partesLima(d);
  return `${fechaLima(d)}T${dos(p.hora)}:${dos(p.minuto)}`;
}

/** Interpreta «YYYY-MM-DDTHH:mm[:ss]» como hora de Lima. Devuelve null si no se entiende. */
export function instanteDesdeLima(texto: string | null | undefined): Date | null {
  if (!texto) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/.exec(texto.trim());
  if (!m) return null;
  const utc = Date.UTC(+m[1], +m[2] - 1, +m[3], +(m[4] ?? 0), +(m[5] ?? 0), +(m[6] ?? 0));
  return new Date(utc - DESFASE_LIMA_MS);
}

const DIAS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "setiembre", "octubre", "noviembre", "diciembre"];

/** «miércoles 19 de noviembre de 2026, 2:20 p. m.» */
export function textoAhoraLima(d: Date = ahora()): string {
  const p = partesLima(d);
  return `${DIAS[p.diaSemana]} ${p.dia} de ${MESES[p.mes - 1]} de ${p.anio}, ${hora12(p.hora, p.minuto)}`;
}

/** «viernes 20 de noviembre» para una fecha YYYY-MM-DD. */
export function textoFecha(fecha: string): string {
  const d = instanteDesdeLima(`${fecha}T12:00`);
  if (!d) return fecha;
  const p = partesLima(d);
  return `${DIAS[p.diaSemana]} ${p.dia} de ${MESES[p.mes - 1]}`;
}

export function hora12(hora: number, minuto: number): string {
  const sufijo = hora < 12 || hora === 24 ? "a. m." : "p. m.";
  const h = hora % 12 === 0 ? 12 : hora % 12;
  return `${h}:${dos(minuto)} ${sufijo}`;
}

/** «19:00» → «7:00 p. m.»; «23:59»/«24:00» → «12:00 a. m.». */
export function hora12Texto(hhmm: string): string {
  const [h, m] = hhmm.split(":").map(Number);
  if (h === 23 && m === 59) return "12:00 a. m.";
  return hora12(h, m);
}

/** Suma días a una fecha YYYY-MM-DD. */
export function sumarDias(fecha: string, dias: number): string {
  const d = instanteDesdeLima(`${fecha}T12:00`)!;
  return fechaLima(new Date(d.getTime() + dias * 86_400_000));
}
