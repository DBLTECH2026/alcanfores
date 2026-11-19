// Lectura del comprobante: si TODO calza se valida por el endpoint interno; si no, queda PENDIENTE para el equipo.
import { afterEach, describe, expect, it } from "vitest";
import { evaluarComprobante, destinatarioCalza, numeroCalza } from "../src/reglasPago.js";
import { fechaLima, instanteDesdeLima, sumarDias } from "../src/tiempo.js";
import { crearEntorno, idImagenDelPrompt, lecturaQueCalza, PNG_BASE64, type Guion } from "./apoyo.js";

const SESION = "sesion-comprobante-01";

/** El cliente ya tiene reserva; manda la foto y Canchita la registra con lo que leyó. */
const guionComprobante: Guion = async ({ entrada, llamar }) => {
  const id = idImagenDelPrompt(entrada.prompt.split("<<<")[1] ?? "");
  if (!id) return "Mándame la foto de tu comprobante.";
  const operacion = /operación (\d+)/.exec(entrada.prompt)?.[1] ?? "000000";
  const r = await llamar("registrar_comprobante", {
    codigo: "ALC-A2345X", dni: "45678912", metodo: "YAPE", codigoOperacion: operacion, idImagen: id,
  });
  return r.estadoPago === "VALIDADO" ? "¡Pago validado! Tu reserva está *confirmada*." : "Recibimos tu comprobante; el equipo lo revisa en minutos.";
};

async function prepararReserva(entorno: ReturnType<typeof crearEntorno>) {
  const manana = sumarDias(fechaLima(), 1);
  const turno = entorno.api.turnos.find((t) => t.fecha === manana && t.horaInicio === "19:00" && t.idCancha === 1)!;
  await entorno.api.crearReserva([turno.idTurno], {
    dni: "45678912", nombres: "Juan", apellidos: "Prueba", telefono: "987654321", correo: "juan@example.com",
  }, "YAPE");
  const conv = await entorno.repo.obtenerOCrearConversacion("WEB", SESION);
  await entorno.repo.actualizarConversacion(conv.id, { codigoReserva: "ALC-A2345X", dni: "45678912", contexto: { comprobante: { tipo: "BOLETA" } } });
}

const enviarFoto = (entorno: ReturnType<typeof crearEntorno>) => entorno.app.inject({
  method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "Ya pagué", imagen: `data:image/png;base64,${PNG_BASE64}` },
});

describe("comprobante por el chat", () => {
  let entorno: ReturnType<typeof crearEntorno>;
  afterEach(async () => entorno?.app.close());

  it("calza: registra el pago y lo valida por el endpoint interno con la lectura de la foto", async () => {
    entorno = crearEntorno(guionComprobante);
    await prepararReserva(entorno);
    entorno.cerebro.lecturas.push(lecturaQueCalza(40, "77310254"));
    const r = await enviarFoto(entorno);
    expect(r.json().mensajes[0].texto).toContain("confirmada");
    expect(entorno.api.pagos).toHaveLength(1);
    expect(entorno.api.pagos[0].comprobante).toEqual({ tipo: "BOLETA" });
    expect(entorno.api.validaciones).toHaveLength(1);
    expect(entorno.api.validaciones[0].lectura.codigoOperacion).toBe("77310254");
    // El modelo vio la lectura como texto, no tuvo que adivinar.
    expect(entorno.cerebro.turnos[0].prompt).toContain("operación 77310254");
    const img = await entorno.repo.imagen(1);
    expect(img?.idPago).toBe(500);
    expect(JSON.parse(img!.validacion!).estadoPago).toBe("VALIDADO");
  });

  it("no calza (monto distinto y dice EJEMPLO): queda PENDIENTE y no se valida", async () => {
    entorno = crearEntorno(guionComprobante);
    await prepararReserva(entorno);
    entorno.cerebro.lecturas.push({ ...lecturaQueCalza(45, "04821937"), marcaDeAgua: true, destinatario: "Complejo Deportivo (cuenta de ejemplo)" });
    const r = await enviarFoto(entorno);
    expect(r.json().mensajes[0].texto).toContain("el equipo lo revisa");
    expect(entorno.api.pagos).toHaveLength(1);
    expect(entorno.api.validaciones).toHaveLength(0);
    const motivos = JSON.parse((await entorno.repo.imagen(1))!.validacion!).motivos as string[];
    expect(motivos.join(" ")).toMatch(/EJEMPLO/);
    expect(motivos.join(" ")).toMatch(/monto/);
  });

  it("con el interruptor «Valida pagos» apagado queda PENDIENTE aunque calce", async () => {
    entorno = crearEntorno(guionComprobante);
    entorno.api.configuracion.ia_valida_pagos = "false";
    await prepararReserva(entorno);
    entorno.cerebro.lecturas.push(lecturaQueCalza(40));
    await enviarFoto(entorno);
    expect(entorno.api.validaciones).toHaveLength(0);
  });

  it("si pagos no responde queda PENDIENTE y validar_pago_ia lo reintenta luego", async () => {
    let paso = 0;
    entorno = crearEntorno(async (t) => {
      paso++;
      if (paso === 1) return guionComprobante(t);
      const r = await t.llamar("validar_pago_ia", { idPago: 500 });
      return String(r.estadoPago);
    });
    await prepararReserva(entorno);
    entorno.cerebro.lecturas.push(lecturaQueCalza(40));
    entorno.api.fallarValidacion = true;
    await enviarFoto(entorno);
    expect(entorno.api.validaciones).toHaveLength(0);
    entorno.api.fallarValidacion = false;
    const r = await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "¿y?" } });
    expect(r.json().mensajes[0].texto).toBe("VALIDADO");
    expect(entorno.api.validaciones).toHaveLength(1);
  });

  it("la foto se lee una sola vez aunque siga en el historial", async () => {
    entorno = crearEntorno(async () => "ok");
    await prepararReserva(entorno);
    entorno.cerebro.lecturas.push(lecturaQueCalza(40));
    await enviarFoto(entorno);
    await entorno.app.inject({ method: "POST", url: "/api/asistente/web/mensaje", payload: { sesion: SESION, texto: "gracias" } });
    expect(entorno.cerebro.lecturasPedidas).toBe(1);
    expect(entorno.cerebro.turnos[1].prompt).toContain("idImagen=1");
  });
});

describe("reglas de auto-validación", () => {
  const ahora = instanteDesdeLima("2026-11-19T10:00")!;
  const esperado = {
    metodo: "YAPE" as const, codigoOperacion: "12345678", montoAdelanto: 40, saldo: 80,
    yapeNumero: "900111222", yapeTitular: "Complejo Deportivo Alcanfores", plinNumero: "900111222",
  };

  it("acepta el adelanto o el total pendiente, con nombre recortado o con los últimos 3 dígitos", () => {
    expect(evaluarComprobante(lecturaQueCalza(40, "12345678", "2026-11-19T09:30"), esperado, ahora).calza).toBe(true);
    expect(evaluarComprobante(lecturaQueCalza(80, "12345678", "2026-11-18T10:30"), esperado, ahora).calza).toBe(true);
    expect(destinatarioCalza("Complejo Deport* Alcanf*", esperado.yapeTitular)).toBe(true);
    expect(destinatarioCalza("COMPLEJO DEPORTIVO ALCANFORES", esperado.yapeTitular)).toBe(true);
    expect(numeroCalza("*** *** 222", "900111222")).toBe(true);
  });

  it("rechaza destinatario ajeno, fecha vieja o futura, operación distinta y app cambiada", () => {
    const ajeno = { ...lecturaQueCalza(40, "12345678", "2026-11-19T09:30"), destinatario: "Juan Perez", numeroDestino: "999" };
    expect(evaluarComprobante(ajeno, esperado, ahora).motivos).toContain("El destinatario no es el titular ni el número del complejo.");
    expect(destinatarioCalza("Complejo Deportivo (cuenta de ejemplo)", esperado.yapeTitular)).toBe(false);
    expect(evaluarComprobante(lecturaQueCalza(40, "12345678", "2026-11-17T09:00"), esperado, ahora).motivos).toContain("El pago tiene más de 24 horas.");
    expect(evaluarComprobante(lecturaQueCalza(40, "12345678", "2026-11-19T12:00"), esperado, ahora).motivos).toContain("La fecha del pago está en el futuro.");
    expect(evaluarComprobante(lecturaQueCalza(40, "99999999", "2026-11-19T09:30"), esperado, ahora).calza).toBe(false);
    expect(evaluarComprobante({ ...lecturaQueCalza(40, "12345678", "2026-11-19T09:30"), app: "PLIN" }, esperado, ahora).calza).toBe(false);
    expect(evaluarComprobante(null, esperado, ahora).calza).toBe(false);
  });
});
