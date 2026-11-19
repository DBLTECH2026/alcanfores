package pe.alcanfores.notificaciones.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bitácora de envíos: un registro por aviso y por canal, con el cuerpo tal como salió (para verlo en el panel). */
@Entity
@Table(name = "notificacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacion {

    public static final int MAX_ERROR = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_reserva", length = 12)
    private String codigoReserva;

    /** Routing key del evento que lo originó ({@code reserva.confirmada}…), {@code recordatorio} o {@code whatsapp.prueba}. */
    @Column(nullable = false, length = 40)
    private String evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalNotificacion canal;

    /** Correo del cliente o número de WhatsApp en formato internacional (51XXXXXXXXX). */
    @Column(nullable = false, length = 120)
    private String destino;

    @Column(nullable = false, length = 200)
    private String asunto;

    /** HTML del correo (con el logo como {@code cid:}) o texto del WhatsApp. */
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String cuerpo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Adjunto> adjuntos = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNotificacion estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = MAX_ERROR)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    /**
     * Cuerpo real que se envía cuando difiere del guardado (un código de verificación se guarda como «******»). No se
     * persiste: por eso esos avisos no se reintentan.
     */
    @Transient
    private String cuerpoEnvio;

    public Notificacion(String codigoReserva, String evento, CanalNotificacion canal, String destino, String asunto,
                        String cuerpo, List<Adjunto> adjuntos, LocalDateTime creadoEn) {
        this.codigoReserva = codigoReserva;
        this.evento = evento;
        this.canal = canal;
        this.destino = destino;
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.adjuntos = adjuntos == null ? new ArrayList<>() : new ArrayList<>(adjuntos);
        this.estado = EstadoNotificacion.PENDIENTE;
        this.intentos = 0;
        this.creadoEn = creadoEn;
    }

    /** Resultado de un intento de envío: suma el intento y deja el estado final. */
    public void registrarResultado(EstadoNotificacion nuevoEstado, String error, LocalDateTime momento) {
        this.intentos++;
        this.estado = nuevoEstado;
        if (nuevoEstado == EstadoNotificacion.FALLIDO) {
            this.ultimoError = recortar(error);
        } else {
            this.ultimoError = null;
            this.enviadoEn = momento;
        }
    }

    public void fijarCuerpoEnvio(String cuerpoReal) {
        this.cuerpoEnvio = cuerpoReal;
    }

    /** Lo que sale por el canal: el cuerpo real si hay uno distinto del guardado. */
    public String cuerpoParaEnviar() {
        return cuerpoEnvio != null ? cuerpoEnvio : cuerpo;
    }

    /** La tarjeta no se pudo enviar por WhatsApp y el aviso salió solo con el texto: queda anotado para el panel. */
    public void marcarImagenNoEnviada(String motivo) {
        this.adjuntos = new ArrayList<>(getAdjuntos().stream()
                .map(a -> a.tipo() == TipoAdjunto.IMAGEN ? a.noDisponible(motivo) : a).toList());
    }

    public void actualizarAdjuntos(List<Adjunto> nuevos) {
        this.adjuntos = new ArrayList<>(nuevos);
    }

    public boolean reintentable(int maxIntentos) {
        return estado == EstadoNotificacion.FALLIDO && intentos < maxIntentos;
    }

    public List<Adjunto> getAdjuntos() {
        return adjuntos == null ? List.of() : List.copyOf(adjuntos);
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "Error desconocido.";
        }
        return texto.length() > MAX_ERROR ? texto.substring(0, MAX_ERROR - 3) + "..." : texto;
    }
}
