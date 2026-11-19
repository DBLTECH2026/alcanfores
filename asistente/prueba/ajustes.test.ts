// Interruptores de Canchita leídos de GET /interno/configuracion (catálogo V6), con valores por defecto si faltan.
import { describe, expect, it } from "vitest";
import { Ajustes, interruptoresDesde } from "../src/ajustes.js";
import { ApiFalsa } from "./apoyo.js";

describe("interruptores de Canchita", () => {
  it("lee las claves reales del catálogo (texto true/false)", () => {
    expect(interruptoresDesde({ ia_whatsapp_activa: "true", ia_web_activa: "false", ia_valida_pagos: "FALSE" }))
      .toEqual({ whatsapp: true, web: false, validaPagos: false });
  });

  it("si faltan las claves usa los valores por defecto del contrato", () => {
    expect(interruptoresDesde({ adelanto_pct: "50" })).toEqual({ whatsapp: false, web: true, validaPagos: true });
    expect(interruptoresDesde({ ia_web_activa: "" })).toEqual({ whatsapp: false, web: true, validaPagos: true });
  });

  it("si catálogo no responde mantiene lo último conocido", async () => {
    const api = new ApiFalsa();
    api.configuracion = { ia_whatsapp_activa: "true", ia_web_activa: "true", ia_valida_pagos: "false" };
    const ajustes = new Ajustes(api, 0);
    expect(await ajustes.interruptores()).toEqual({ whatsapp: true, web: true, validaPagos: false });
    api.configuracionInterna = async () => {
      throw new Error("catálogo caído");
    };
    expect(await ajustes.interruptores()).toEqual({ whatsapp: true, web: true, validaPagos: false });
  });
});
