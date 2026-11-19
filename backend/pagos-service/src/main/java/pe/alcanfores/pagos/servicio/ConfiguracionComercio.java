package pe.alcanfores.pagos.servicio;

import org.springframework.stereotype.Component;
import pe.alcanfores.comun.validacion.Documentos;
import pe.alcanfores.pagos.cliente.CatalogoCliente;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del complejo que usa pagos (Yape/Plin, % de adelanto, datos del emisor), leída de catalogo-service
 * con caché de 5 minutos. Si catálogo no responde se usan los valores por defecto y se reintenta en 30 s.
 */
@Component
public class ConfiguracionComercio {

    static final Duration VIGENCIA = Duration.ofMinutes(5);
    static final Duration REINTENTO = Duration.ofSeconds(30);

    static final Map<String, String> POR_DEFECTO = Map.ofEntries(
            Map.entry("yape_numero", "914995187"),
            Map.entry("yape_titular", "Complejo Deportivo Los Alcanfores"),
            Map.entry("plin_numero", "914995187"),
            Map.entry("adelanto_pct", "50"),
            Map.entry("ruc_emisor", "20612345679"),
            Map.entry("razon_social_emisor", "Los Alcanfores Sport E.I.R.L. (ficticia)"),
            Map.entry("direccion", "Carretera Central km 11.5, Santa Clara, Ate, Lima"),
            Map.entry("nombre_complejo", "Complejo Deportivo Los Alcanfores"));

    private final CatalogoCliente catalogo;
    private volatile Map<String, String> valores;
    private volatile Instant vence = Instant.EPOCH;

    public ConfiguracionComercio(CatalogoCliente catalogo) {
        this.catalogo = catalogo;
    }

    public String yapeNumero() {
        return valor("yape_numero");
    }

    public String yapeTitular() {
        return valor("yape_titular");
    }

    public String plinNumero() {
        return valor("plin_numero");
    }

    public String nombreComplejo() {
        return valor("nombre_complejo");
    }

    public int adelantoPct() {
        try {
            int pct = Integer.parseInt(valor("adelanto_pct").trim());
            return pct >= 1 && pct <= 100 ? pct : 50;
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /** Emisor ficticio de los comprobantes (si el RUC configurado no es válido se usa el del SPEC). */
    public Emisor emisor() {
        String ruc = valor("ruc_emisor").trim();
        if (!Documentos.rucValido(ruc)) {
            ruc = POR_DEFECTO.get("ruc_emisor");
        }
        return new Emisor(ruc, valor("razon_social_emisor"), valor("direccion"));
    }

    private String valor(String clave) {
        String v = actuales().get(clave);
        return v == null || v.isBlank() ? POR_DEFECTO.get(clave) : v;
    }

    private Map<String, String> actuales() {
        Instant ahora = Instant.now();
        if (valores == null || ahora.isAfter(vence)) {
            synchronized (this) {
                if (valores == null || ahora.isAfter(vence)) {
                    Map<String, String> remotos = catalogo.configuracion();
                    Map<String, String> mezcla = new HashMap<>(POR_DEFECTO);
                    mezcla.putAll(remotos);
                    valores = mezcla;
                    vence = ahora.plus(remotos.isEmpty() ? REINTENTO : VIGENCIA);
                }
            }
        }
        return valores;
    }

    public record Emisor(String ruc, String razonSocial, String direccion) {
    }
}
