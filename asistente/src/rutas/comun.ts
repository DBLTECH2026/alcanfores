// Utilidades HTTP: cuerpo de error estándar del sistema, verificación del JWT HS256 de auth-service y fechas de Lima.
import { createHmac, timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import { fechaHoraLima } from "../tiempo.js";

export interface ErrorCampo {
  campo: string;
  mensaje: string;
}

export function responderError(req: FastifyRequest, rep: FastifyReply, status: number, codigo: string, mensaje: string,
  errores: ErrorCampo[] = []): FastifyReply {
  return rep.status(status).send({
    timestamp: `${fechaHoraLima()}:00`, status, codigo, mensaje, ruta: req.url.split("?")[0], errores,
  });
}

/** «2026-11-20T19:00:00» (hora de Lima, sin zona, como el resto del sistema). */
export function isoLima(d: Date | null | undefined): string | null {
  if (!d) return null;
  const segundos = String(Math.floor((d.getTime() / 1000) % 60)).padStart(2, "0");
  return `${fechaHoraLima(d)}:${segundos}`;
}

export interface UsuarioToken {
  id: string;
  usuario: string;
  nombre: string;
  rol: string;
}

function base64url(buf: Buffer): string {
  return buf.toString("base64").replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
}

/** Verifica un JWT HS256 firmado con JWT_SECRETO (igual que JwtServicio de comun). Devuelve null si no es válido. */
export function verificarJwt(token: string, secreto: string, ahoraMs = Date.now()): UsuarioToken | null {
  const partes = token.split(".");
  if (partes.length !== 3) return null;
  try {
    const cabecera = JSON.parse(Buffer.from(partes[0], "base64url").toString("utf8")) as { alg?: string };
    if (cabecera.alg !== "HS256") return null;
    const esperada = Buffer.from(base64url(createHmac("sha256", Buffer.from(secreto, "utf8")).update(`${partes[0]}.${partes[1]}`).digest()));
    const recibida = Buffer.from(partes[2]);
    if (esperada.length !== recibida.length || !timingSafeEqual(esperada, recibida)) return null;
    const c = JSON.parse(Buffer.from(partes[1], "base64url").toString("utf8")) as Record<string, unknown>;
    if (typeof c.exp !== "number" || c.exp * 1000 <= ahoraMs) return null;
    return { id: String(c.sub ?? ""), usuario: String(c.usuario ?? ""), nombre: String(c.nombre ?? ""), rol: String(c.rol ?? "") };
  } catch {
    return null;
  }
}

/** Firma un JWT HS256 (solo para las pruebas y para probar a mano en local). */
export function firmarJwt(claims: Record<string, unknown>, secreto: string): string {
  const cabecera = base64url(Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const cuerpo = base64url(Buffer.from(JSON.stringify(claims)));
  const firma = base64url(createHmac("sha256", Buffer.from(secreto, "utf8")).update(`${cabecera}.${cuerpo}`).digest());
  return `${cabecera}.${cuerpo}.${firma}`;
}
