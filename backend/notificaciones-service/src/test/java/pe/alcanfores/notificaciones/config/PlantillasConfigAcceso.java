package pe.alcanfores.notificaciones.config;

import org.thymeleaf.spring6.SpringTemplateEngine;

/** Da a las pruebas sin Spring los mismos motores Thymeleaf que usa el servicio. */
public final class PlantillasConfigAcceso {

    private static final PlantillasConfig CONFIG = new PlantillasConfig();

    private PlantillasConfigAcceso() {
    }

    public static SpringTemplateEngine motorCorreo() {
        return CONFIG.motorCorreo();
    }

    public static SpringTemplateEngine motorWhatsapp() {
        return CONFIG.motorWhatsapp();
    }
}
