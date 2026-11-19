// Punto de entrada de asistente-service (Canchita), puerto 8096.
import { ApiHttp } from "./api.js";
import { crearAplicacion } from "./app.js";
import { RepositorioMysql } from "./bd/repositorioMysql.js";
import { CerebroClaude } from "./cerebro/cerebroClaude.js";
import { leerConfiguracion, tipoCredencial } from "./config.js";
import { EvolutionHttp } from "./whatsapp/evolution.js";

process.env.TZ = "America/Lima";

async function principal(): Promise<void> {
  const cfg = leerConfiguracion();
  const repo = await RepositorioMysql.iniciar(cfg);
  const { app, sincronizador } = crearAplicacion({
    cfg, repo, api: new ApiHttp(cfg), cerebro: new CerebroClaude(cfg), wa: new EvolutionHttp(cfg), registrarSolicitudes: true,
  });
  await app.listen({ host: "0.0.0.0", port: cfg.puerto });
  // Nunca se registra la credencial: solo su tipo.
  app.log.info(`Canchita lista en el puerto ${cfg.puerto} · modelo ${cfg.ia.modelo} (${cfg.ia.esfuerzo}) · credencial ${tipoCredencial(cfg)}`);
  sincronizador.iniciar();

  const cerrar = async () => {
    sincronizador.detener();
    await app.close();
    await repo.cerrar();
    process.exit(0);
  };
  process.on("SIGTERM", () => void cerrar());
  process.on("SIGINT", () => void cerrar());
}

principal().catch((e) => {
  console.error("No se pudo arrancar asistente-service:", e instanceof Error ? e.message : e);
  process.exit(1);
});
