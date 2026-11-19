package pe.alcanfores.notificaciones.servicio.impl;

import org.springframework.stereotype.Service;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;
import pe.alcanfores.comun.util.Tiempo;
import pe.alcanfores.notificaciones.cliente.EvolutionCliente;
import pe.alcanfores.notificaciones.cliente.EvolutionExcepcion;
import pe.alcanfores.notificaciones.dto.ConexionWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.EstadoWhatsappRespuesta;
import pe.alcanfores.notificaciones.dto.MensajeRespuesta;
import pe.alcanfores.notificaciones.dto.NotificacionDetalle;
import pe.alcanfores.notificaciones.mapeador.NotificacionMapeador;
import pe.alcanfores.notificaciones.modelo.CanalNotificacion;
import pe.alcanfores.notificaciones.modelo.Notificacion;
import pe.alcanfores.notificaciones.plantilla.TipoAviso;
import pe.alcanfores.notificaciones.repositorio.NotificacionRepositorio;
import pe.alcanfores.notificaciones.servicio.EnvioServicio;
import pe.alcanfores.notificaciones.servicio.WhatsappServicio;

import java.util.List;
import java.util.function.Supplier;

@Service
public class WhatsappServicioImpl implements WhatsappServicio {

    static final String NO_CONFIGURADO = "NO_CONFIGURADO";

    private final EvolutionCliente evolution;
    private final NotificacionRepositorio notificaciones;
    private final EnvioServicio envio;
    private final NotificacionMapeador mapeador;
    private final Auditor auditor;

    public WhatsappServicioImpl(EvolutionCliente evolution, NotificacionRepositorio notificaciones, EnvioServicio envio,
                                NotificacionMapeador mapeador, Auditor auditor) {
        this.evolution = evolution;
        this.notificaciones = notificaciones;
        this.envio = envio;
        this.mapeador = mapeador;
        this.auditor = auditor;
    }

    @Override
    public EstadoWhatsappRespuesta estado() {
        if (!evolution.configurado()) {
            return new EstadoWhatsappRespuesta(false, evolution.instancia(), NO_CONFIGURADO, null);
        }
        EvolutionCliente.EstadoConexion e = protegido(evolution::estado);
        return new EstadoWhatsappRespuesta(true, evolution.instancia(), e.estado(), e.numero());
    }

    @Override
    public ConexionWhatsappRespuesta conectar(String telefono) {
        exigirConfiguracion();
        String numero = telefono == null || telefono.isBlank() ? null : Texto.celularWhatsapp(telefono);
        EvolutionCliente.CodigoConexion c = protegido(() -> evolution.conectar(numero));
        String mensaje;
        if ("open".equals(c.estado())) {
            mensaje = "El WhatsApp ya está conectado.";
        } else if (c.qrBase64() != null) {
            mensaje = "Abre WhatsApp en el celular del complejo > Dispositivos vinculados > Vincular un dispositivo y escanea el código.";
        } else {
            mensaje = "Evolution API aún está preparando el código QR. Vuelve a intentarlo en unos segundos.";
        }
        auditor.registrar("CONECTAR_WHATSAPP", "whatsapp", evolution.instancia(),
                "Solicitud de QR" + (numero == null ? "" : " con código de emparejamiento para " + numero) + " · " + c.estado());
        return new ConexionWhatsappRespuesta(c.qrBase64(), c.codigoEmparejamiento(), c.estado(), mensaje);
    }

    @Override
    public MensajeRespuesta desconectar() {
        exigirConfiguracion();
        protegido(() -> {
            evolution.desconectar();
            return null;
        });
        auditor.registrar("DESCONECTAR_WHATSAPP", "whatsapp", evolution.instancia(), "Sesión de WhatsApp cerrada");
        return new MensajeRespuesta("close",
                "Se cerró la sesión de WhatsApp. Para volver a enviar avisos, conecta el número escaneando un nuevo QR.");
    }

    @Override
    public NotificacionDetalle prueba(String telefono, String mensaje) {
        String numero = Texto.celularWhatsapp(telefono);
        Notificacion n = notificaciones.save(new Notificacion(null, TipoAviso.PRUEBA_WHATSAPP.evento(),
                CanalNotificacion.WHATSAPP, numero, TipoAviso.PRUEBA_WHATSAPP.texto(), mensaje.strip(), List.of(),
                Tiempo.ahora()));
        Notificacion enviada = envio.enviar(n);
        auditor.registrar("PRUEBA_WHATSAPP", "notificacion", enviada.getId(),
                "Mensaje de prueba a " + numero + " · resultado " + enviada.getEstado());
        return mapeador.aDetalle(enviada);
    }

    private void exigirConfiguracion() {
        if (!evolution.configurado()) {
            throw NegocioExcepcion.conflicto("WHATSAPP_NO_CONFIGURADO",
                    "El WhatsApp no está configurado: define EVOLUTION_URL y EVOLUTION_APIKEY en el servidor.");
        }
    }

    private static <T> T protegido(Supplier<T> accion) {
        try {
            return accion.get();
        } catch (EvolutionExcepcion e) {
            throw NegocioExcepcion.noDisponible("WHATSAPP_NO_DISPONIBLE", e.getMessage());
        }
    }
}
