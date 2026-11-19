package pe.alcanfores.catalogo.modelo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Claves de la tabla {@code configuracion} con su tipo, límites, valor por defecto (SPEC §5.2) y descripción.
 * Si una clave falta en la BD se responde con su valor por defecto.
 */
public enum ClaveConfiguracion {

    ADELANTO_PCT("adelanto_pct", Tipo.PORCENTAJE, 0, 100, "50",
            "Porcentaje del precio que se paga como adelanto (0 a 100)."),
    DEVOLUCION_HORAS("devolucion_horas", Tipo.ENTERO, 1, 168, "24",
            "Horas mínimas de anticipación para cancelar con devolución."),
    PRIMER_TURNO("primer_turno", Tipo.HORA, 0, 0, "08:00", "Hora de inicio del primer turno del día (HH:mm)."),
    ULTIMO_TURNO("ultimo_turno", Tipo.HORA, 0, 0, "23:00",
            "Hora de cierre: el último turno empieza una duración antes (HH:mm)."),
    DURACION_MIN("duracion_min", Tipo.ENTERO, 30, 180, "60", "Duración de cada turno en minutos."),
    INICIO_NOCHE("inicio_noche", Tipo.HORA, 0, 0, "18:00", "Desde esta hora se cobra la tarifa de noche (HH:mm)."),
    DIAS_VISIBLES("dias_visibles", Tipo.ENTERO, 1, 60, "14", "Días hacia adelante que se pueden reservar."),
    MINUTOS_PARA_PAGAR("minutos_para_pagar", Tipo.ENTERO, 5, 1440, "20",
            "Minutos que tiene el cliente para enviar el comprobante del adelanto."),
    RECORDATORIO_HORAS("recordatorio_horas", Tipo.ENTERO, 1, 48, "2",
            "Horas antes del partido en que se envía el recordatorio."),
    YAPE_NUMERO("yape_numero", Tipo.CELULAR, 0, 0, "900111222", "Celular de Yape del complejo (ficticio)."),
    YAPE_TITULAR("yape_titular", Tipo.TEXTO, 3, 60, "Complejo Deportivo Alcanfores", "Titular de la cuenta de Yape."),
    PLIN_NUMERO("plin_numero", Tipo.CELULAR, 0, 0, "900111222", "Celular de Plin del complejo (ficticio)."),
    WHATSAPP("whatsapp", Tipo.CELULAR, 0, 0, "914995187", "WhatsApp de atención del complejo."),
    CORREO("correo", Tipo.CORREO, 0, 100, "informes.alcanfores@gmail.com", "Correo de informes del complejo."),
    DIRECCION("direccion", Tipo.TEXTO, 5, 200, "Carretera Central km 11.5, Santa Clara, Ate, Lima",
            "Dirección del complejo."),
    REFERENCIA("referencia", Tipo.TEXTO, 5, 200, "Paradero Caseta, a dos cuadras de la UTP Santa Clara",
            "Referencia para llegar."),
    MAPA_URL("mapa_url", Tipo.URL, 0, 500,
            "https://www.google.com/maps/search/?api=1&query=Complejo+Deportivo+Los+Alcanfores+Santa+Clara+Ate",
            "Enlace al mapa."),
    LATITUD("latitud", Tipo.LATITUD, -90, 90, "-12.0196", "Latitud aproximada del complejo."),
    LONGITUD("longitud", Tipo.LONGITUD, -180, 180, "-76.8865", "Longitud aproximada del complejo."),
    NOMBRE_COMPLEJO("nombre_complejo", Tipo.TEXTO, 3, 80, "Complejo Deportivo Los Alcanfores",
            "Nombre comercial del complejo."),
    LEMA("lema", Tipo.TEXTO, 3, 120, "Reserva tu cancha. Juega cuando quieras.", "Lema del portal."),
    RUC_EMISOR("ruc_emisor", Tipo.RUC, 0, 0, "20000000001", "RUC del emisor de comprobantes (ficticio)."),
    RAZON_SOCIAL_EMISOR("razon_social_emisor", Tipo.TEXTO, 3, 150, "Los Alcanfores Sport E.I.R.L. (ficticia)",
            "Razón social del emisor (ficticia).");

    public enum Tipo {
        PORCENTAJE, ENTERO, HORA, CELULAR, TEXTO, CORREO, URL, LATITUD, LONGITUD, RUC
    }

    private final String clave;
    private final Tipo tipo;
    private final int minimo;
    private final int maximo;
    private final String porDefecto;
    private final String descripcion;

    ClaveConfiguracion(String clave, Tipo tipo, int minimo, int maximo, String porDefecto, String descripcion) {
        this.clave = clave;
        this.tipo = tipo;
        this.minimo = minimo;
        this.maximo = maximo;
        this.porDefecto = porDefecto;
        this.descripcion = descripcion;
    }

    public static Optional<ClaveConfiguracion> de(String clave) {
        return Arrays.stream(values()).filter(c -> c.clave.equals(clave)).findFirst();
    }

    public String clave() {
        return clave;
    }

    public Tipo tipo() {
        return tipo;
    }

    public int minimo() {
        return minimo;
    }

    public int maximo() {
        return maximo;
    }

    public String porDefecto() {
        return porDefecto;
    }

    public String descripcion() {
        return descripcion;
    }
}
