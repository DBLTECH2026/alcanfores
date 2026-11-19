package pe.alcanfores.auth.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro central de auditoría: quién hizo qué, en qué servicio, cuándo y desde qué IP (RNF-19). */
@Entity
@Table(name = "bitacora")
@Getter
@Setter
@NoArgsConstructor
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Long id;

    @Column(nullable = false, length = 40)
    private String servicio;

    @Column(name = "id_actor")
    private Long idActor;

    @Column(nullable = false, length = 60)
    private String actor;

    @Column(length = 20)
    private String rol;

    @Column(nullable = false, length = 60)
    private String accion;

    @Column(length = 60)
    private String entidad;

    @Column(name = "id_entidad", length = 60)
    private String idEntidad;

    @Column(length = 500)
    private String detalle;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
