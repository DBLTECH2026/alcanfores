package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Eventos de RabbitMQ ya consumidos (idempotencia ante reentregas). */
@Entity
@Table(name = "evento_procesado")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventoProcesado {

    @Id
    @Column(name = "id_evento", columnDefinition = "char(36)")
    private String idEvento;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;
}
