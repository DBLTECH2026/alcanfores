// Contrato del «cerebro» de Canchita. El real usa el SDK de agentes de Claude; en las pruebas se inyecta uno simulado
// que decide qué herramientas llamar sin gastar la suscripción.
import type { z } from "zod";
import type { LecturaComprobante } from "../modelo.js";

export interface DefinicionHerramienta {
  nombre: string;
  descripcion: string;
  esquema: z.ZodRawShape;
  /** Ejecuta la herramienta y devuelve un objeto que se envía al modelo como JSON. */
  ejecutar(args: Record<string, unknown>): Promise<unknown>;
}

export interface EntradaTurno {
  sistema: string;
  prompt: string;
  herramientas: DefinicionHerramienta[];
  /** Aviso cada vez que el modelo usa una herramienta (para «Revisando turnos…» en la web). */
  alUsarHerramienta?: (nombre: string) => void;
}

export interface Cerebro {
  /** ¿Hay credencial para hablar con el modelo? */
  disponible(): boolean;
  /** Corre un turno completo (con herramientas) y devuelve el texto final de Canchita. */
  responder(entrada: EntradaTurno): Promise<string>;
  /** Lee la foto de un comprobante (visión, sin herramientas) y devuelve lo que se ve. */
  leerComprobante(imagen: Buffer, mime: string, hoy: string): Promise<LecturaComprobante>;
}
