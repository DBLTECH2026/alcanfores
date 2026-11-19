// Repositorio sobre MySQL/MariaDB real. Solo corre con PRUEBAS_MYSQL=1 (usa la base alcanfores_asistente_test).
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import mysql from "mysql2/promise";
import { RepositorioMysql } from "../src/bd/repositorioMysql.js";
import { leerConfiguracion } from "../src/config.js";
import { lecturaQueCalza } from "./apoyo.js";

describe.skipIf(!process.env.PRUEBAS_MYSQL)("repositorio MySQL", () => {
  const base = leerConfiguracion();
  const cfg = { ...base, bd: { ...base.bd, nombre: "alcanfores_asistente_test" } };
  let repo: RepositorioMysql;

  beforeAll(async () => {
    const c = await mysql.createConnection({ host: cfg.bd.host, port: cfg.bd.puerto, user: cfg.bd.usuario, password: cfg.bd.clave });
    await c.query("DROP DATABASE IF EXISTS alcanfores_asistente_test");
    await c.end();
    repo = await RepositorioMysql.iniciar(cfg);
  });
  afterAll(async () => repo?.cerrar());

  it("crea la base, migra y guarda conversaciones, mensajes e imágenes", async () => {
    const c = await repo.obtenerOCrearConversacion("WHATSAPP", "51987650001", "Cliente Ficticio");
    const otra = await repo.obtenerOCrearConversacion("WHATSAPP", "51987650001", "Otro nombre");
    expect(otra.id).toBe(c.id);
    expect(otra.nombre).toBe("Cliente Ficticio");

    const m = await repo.agregarMensaje({ idConversacion: c.id, rol: "CLIENTE", texto: "Hola, ¿hay cancha mañana?", idExterno: "W1" });
    expect(await repo.existeMensajeExterno("W1")).toBe(true);
    expect((await repo.pendientes(c.id)).map((x) => x.id)).toEqual([m.id]);
    const r = await repo.agregarMensaje({
      idConversacion: c.id, rol: "CANCHITA", texto: "Sí", latenciaMs: 4200, extras: { opciones: ["7:00 p. m."] },
    });
    await repo.anotarIdExterno(r.id, "3EB0X");
    await repo.marcarAtendidos([m.id]);
    expect(await repo.pendientes(c.id)).toHaveLength(0);
    const h = await repo.historial(c.id, 10);
    expect(h.map((x) => x.rol)).toEqual(["CLIENTE", "CANCHITA"]);
    expect(h[1].extras).toEqual({ opciones: ["7:00 p. m."] });
    expect(h[1].idExterno).toBe("3EB0X");

    const act = await repo.actualizarConversacion(c.id, {
      estado: "PAUSADA", pausadaHasta: new Date(Date.now() + 3_600_000), codigoReserva: "ALC-A2345X", dni: "45678912",
      contexto: { comprobante: { tipo: "BOLETA" } },
    });
    expect(act.estado).toBe("PAUSADA");
    expect(act.contexto).toEqual({ comprobante: { tipo: "BOLETA" } });
    expect(Math.abs(act.pausadaHasta!.getTime() - Date.now() - 3_600_000)).toBeLessThan(5_000);

    const img = await repo.guardarImagen(c.id, "image/png", Buffer.from([1, 2, 3]));
    await repo.actualizarImagen(img.id, { lectura: lecturaQueCalza(40), idPago: 77, validacion: '{"estadoPago":"PENDIENTE"}' });
    const leida = await repo.imagenPorPago(77);
    expect(leida?.lectura?.monto).toBe(40);
    expect([...leida!.datos]).toEqual([1, 2, 3]);

    expect((await repo.ultimasConversaciones(5))[0].ultimoMensaje?.texto).toBe("Sí");
    expect(await repo.contarConversaciones()).toEqual({ activas: 0, pausadas: 1, hoy: 1 });
    expect(await repo.latenciaPromedioMs(10)).toBe(4200);
  });
});
