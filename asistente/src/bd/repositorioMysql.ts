// Repositorio sobre MySQL/MariaDB (mysql2). Crea la base si no existe y aplica las migraciones al arrancar.
import mysql, { type Pool, type RowDataPacket, type ResultSetHeader } from "mysql2/promise";
import type { Configuracion } from "../config.js";
import type {
  Canal, ContextoConversacion, ExtrasMensaje, Conversacion, Imagen, LecturaComprobante, Mensaje, NuevoMensaje, Repositorio,
  ResumenConversacion,
} from "../modelo.js";
import { ahora, fechaLima } from "../tiempo.js";
import { MIGRACIONES } from "./migraciones.js";

function json<T>(valor: unknown, porDefecto: T): T {
  if (valor == null) return porDefecto;
  if (typeof valor === "string") {
    try {
      return JSON.parse(valor) as T;
    } catch {
      return porDefecto;
    }
  }
  return valor as T;
}

function aConversacion(f: RowDataPacket): Conversacion {
  return {
    id: Number(f.id), canal: f.canal, clave: f.clave, nombre: f.nombre, estado: f.estado,
    pausadaHasta: f.pausada_hasta, codigoReserva: f.codigo_reserva, dni: f.dni,
    contexto: json<ContextoConversacion>(f.contexto, {}), creadaEn: f.creada_en, ultimaActividad: f.ultima_actividad,
  };
}

function aMensaje(f: RowDataPacket): Mensaje {
  return {
    id: Number(f.id), idConversacion: Number(f.id_conversacion), rol: f.rol, texto: f.texto, adjunto: f.adjunto,
    idExterno: f.id_externo, latenciaMs: f.latencia_ms,
    extras: json<ExtrasMensaje | null>(f.extras, null), atendido: Boolean(f.atendido), creadoEn: f.creado_en,
  };
}

function aImagen(f: RowDataPacket): Imagen {
  return {
    id: Number(f.id), idConversacion: Number(f.id_conversacion), mime: f.mime, datos: f.datos,
    lectura: json<LecturaComprobante | null>(f.lectura, null), idPago: f.id_pago == null ? null : Number(f.id_pago),
    validacion: f.validacion, creadoEn: f.creado_en,
  };
}

export class RepositorioMysql implements Repositorio {
  private constructor(private readonly pool: Pool) {}

  static async iniciar(cfg: Configuracion): Promise<RepositorioMysql> {
    const base = { host: cfg.bd.host, port: cfg.bd.puerto, user: cfg.bd.usuario, password: cfg.bd.clave, timezone: "-05:00" };
    const inicial = await mysql.createConnection(base);
    await inicial.query(
      `CREATE DATABASE IF NOT EXISTS \`${cfg.bd.nombre.replace(/`/g, "")}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
    await inicial.end();
    const pool = mysql.createPool({ ...base, database: cfg.bd.nombre, connectionLimit: 5, charset: "utf8mb4" });
    const repo = new RepositorioMysql(pool);
    await repo.migrar();
    return repo;
  }

  private async migrar(): Promise<void> {
    await this.pool.query(`CREATE TABLE IF NOT EXISTS migracion (
      version INT NOT NULL PRIMARY KEY, descripcion VARCHAR(200) NOT NULL, aplicada_en DATETIME NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT version FROM migracion");
    const aplicadas = new Set(filas.map((f) => Number(f.version)));
    for (const m of MIGRACIONES) {
      if (aplicadas.has(m.version)) continue;
      for (const sql of m.sql) await this.pool.query(sql);
      await this.pool.query("INSERT INTO migracion (version, descripcion, aplicada_en) VALUES (?, ?, ?)",
        [m.version, m.descripcion, ahora()]);
    }
  }

  async obtenerOCrearConversacion(canal: Canal, clave: string, nombre?: string | null): Promise<Conversacion> {
    const existente = await this.buscarConversacion(canal, clave);
    if (existente) {
      if (nombre && !existente.nombre) return this.actualizarConversacion(existente.id, { nombre });
      return existente;
    }
    const momento = ahora();
    // INSERT IGNORE: si otro mensaje del mismo chat la creó en paralelo, no falla (la clave canal+clave es única).
    await this.pool.query(
      `INSERT IGNORE INTO conversacion (canal, clave, nombre, estado, contexto, creada_en, ultima_actividad)
       VALUES (?, ?, ?, 'ACTIVA', '{}', ?, ?)`,
      [canal, clave, nombre ?? null, momento, momento]);
    return (await this.buscarConversacion(canal, clave))!;
  }

  async conversacion(id: number): Promise<Conversacion | null> {
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT * FROM conversacion WHERE id = ?", [id]);
    return filas[0] ? aConversacion(filas[0]) : null;
  }

  async buscarConversacion(canal: Canal, clave: string): Promise<Conversacion | null> {
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT * FROM conversacion WHERE canal = ? AND clave = ?", [canal, clave]);
    return filas[0] ? aConversacion(filas[0]) : null;
  }

  async actualizarConversacion(id: number, cambios: Partial<Conversacion>): Promise<Conversacion> {
    const columnas: Record<string, string> = {
      nombre: "nombre", estado: "estado", pausadaHasta: "pausada_hasta", codigoReserva: "codigo_reserva",
      dni: "dni", contexto: "contexto",
    };
    const sets: string[] = [];
    const valores: unknown[] = [];
    for (const [campo, columna] of Object.entries(columnas)) {
      if (!(campo in cambios)) continue;
      const v = (cambios as Record<string, unknown>)[campo];
      sets.push(`${columna} = ?`);
      valores.push(campo === "contexto" ? JSON.stringify(v ?? {}) : v);
    }
    if (sets.length) await this.pool.query(`UPDATE conversacion SET ${sets.join(", ")} WHERE id = ?`, [...valores, id]);
    const c = await this.conversacion(id);
    if (!c) throw new Error(`No existe la conversación ${id}`);
    return c;
  }

  async agregarMensaje(m: NuevoMensaje): Promise<Mensaje> {
    const momento = ahora();
    const [r] = await this.pool.query<ResultSetHeader>(
      `INSERT INTO mensaje (id_conversacion, rol, texto, adjunto, id_externo, latencia_ms, extras, atendido, creado_en)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [m.idConversacion, m.rol, m.texto, m.adjunto ?? null, m.idExterno ?? null, m.latenciaMs ?? null,
        m.extras ? JSON.stringify(m.extras) : null,
        (m.atendido ?? m.rol !== "CLIENTE") ? 1 : 0, momento]);
    await this.pool.query("UPDATE conversacion SET ultima_actividad = ? WHERE id = ?", [momento, m.idConversacion]);
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT * FROM mensaje WHERE id = ?", [r.insertId]);
    return aMensaje(filas[0]);
  }

  async existeMensajeExterno(idExterno: string): Promise<boolean> {
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT 1 FROM mensaje WHERE id_externo = ? LIMIT 1", [idExterno]);
    return filas.length > 0;
  }

  async anotarIdExterno(idMensaje: number, idExterno: string): Promise<void> {
    await this.pool.query("UPDATE mensaje SET id_externo = ? WHERE id = ?", [idExterno, idMensaje]);
  }

  async historial(idConversacion: number, limite: number): Promise<Mensaje[]> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      "SELECT * FROM mensaje WHERE id_conversacion = ? ORDER BY id DESC LIMIT ?", [idConversacion, limite]);
    return filas.map(aMensaje).reverse();
  }

  async pendientes(idConversacion: number): Promise<Mensaje[]> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      "SELECT * FROM mensaje WHERE id_conversacion = ? AND rol = 'CLIENTE' AND atendido = 0 ORDER BY id", [idConversacion]);
    return filas.map(aMensaje);
  }

  async marcarAtendidos(ids: number[]): Promise<void> {
    if (ids.length) await this.pool.query("UPDATE mensaje SET atendido = 1 WHERE id IN (?)", [ids]);
  }

  async guardarImagen(idConversacion: number, mime: string, datos: Buffer): Promise<Imagen> {
    const [r] = await this.pool.query<ResultSetHeader>(
      "INSERT INTO imagen (id_conversacion, mime, datos, creado_en) VALUES (?, ?, ?, ?)", [idConversacion, mime, datos, ahora()]);
    return (await this.imagen(r.insertId))!;
  }

  async imagen(id: number): Promise<Imagen | null> {
    const [filas] = await this.pool.query<RowDataPacket[]>("SELECT * FROM imagen WHERE id = ?", [id]);
    return filas[0] ? aImagen(filas[0]) : null;
  }

  async imagenPorPago(idPago: number): Promise<Imagen | null> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      "SELECT * FROM imagen WHERE id_pago = ? ORDER BY id DESC LIMIT 1", [idPago]);
    return filas[0] ? aImagen(filas[0]) : null;
  }

  async actualizarImagen(id: number, cambios: Partial<Imagen>): Promise<void> {
    const sets: string[] = [];
    const valores: unknown[] = [];
    if ("lectura" in cambios) { sets.push("lectura = ?"); valores.push(cambios.lectura ? JSON.stringify(cambios.lectura) : null); }
    if ("idPago" in cambios) { sets.push("id_pago = ?"); valores.push(cambios.idPago); }
    if ("validacion" in cambios) { sets.push("validacion = ?"); valores.push(cambios.validacion ?? null); }
    if (sets.length) await this.pool.query(`UPDATE imagen SET ${sets.join(", ")} WHERE id = ?`, [...valores, id]);
  }

  async ultimasConversaciones(limite: number): Promise<ResumenConversacion[]> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      "SELECT * FROM conversacion ORDER BY ultima_actividad DESC, id DESC LIMIT ?", [limite]);
    const resultado: ResumenConversacion[] = [];
    for (const f of filas) {
      const [m] = await this.pool.query<RowDataPacket[]>(
        "SELECT * FROM mensaje WHERE id_conversacion = ? ORDER BY id DESC LIMIT 1", [f.id]);
      resultado.push({ conversacion: aConversacion(f), ultimoMensaje: m[0] ? aMensaje(m[0]) : null });
    }
    return resultado;
  }

  async contarConversaciones(): Promise<{ activas: number; pausadas: number; hoy: number }> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      `SELECT SUM(estado = 'ACTIVA') AS activas, SUM(estado = 'PAUSADA') AS pausadas,
              SUM(DATE(ultima_actividad) = ?) AS hoy FROM conversacion`, [fechaLima()]);
    return { activas: Number(filas[0].activas ?? 0), pausadas: Number(filas[0].pausadas ?? 0), hoy: Number(filas[0].hoy ?? 0) };
  }

  async latenciaPromedioMs(ultimos: number): Promise<number | null> {
    const [filas] = await this.pool.query<RowDataPacket[]>(
      `SELECT AVG(latencia_ms) AS promedio FROM (
         SELECT latencia_ms FROM mensaje WHERE latencia_ms IS NOT NULL ORDER BY id DESC LIMIT ?) ultimos`, [ultimos]);
    return filas[0].promedio == null ? null : Math.round(Number(filas[0].promedio));
  }

  async cerrar(): Promise<void> {
    await this.pool.end();
  }
}
