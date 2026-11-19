package pe.alcanfores.reservas.modelo;

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

/** Cliente que reserva (se identifica por su DNI; nombres en Título, celular de 9 dígitos y correo en minúsculas). */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id;

    @Column(nullable = false, columnDefinition = "CHAR(8)")
    private String dni;

    @Column(nullable = false, length = 60)
    private String nombres;

    @Column(nullable = false, length = 60)
    private String apellidos;

    @Column(nullable = false, columnDefinition = "CHAR(9)")
    private String telefono;

    @Column(nullable = false, length = 100)
    private String correo;

    @Column(name = "verificado_reniec", nullable = false)
    private boolean verificadoReniec;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
