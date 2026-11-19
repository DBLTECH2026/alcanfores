package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/** Registro de eventos ya consumidos (idempotencia: un mismo evento repetido no se aplica dos veces). */
@Entity
@Table(name = "evento_procesado")
@Getter
@NoArgsConstructor
public class EventoProcesado implements Persistable<String> {

    @Id
    @Column(name = "id_evento", columnDefinition = "CHAR(36)")
    private String idEvento;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false)
    private LocalDateTime procesadoEn;

    public EventoProcesado(String idEvento, String tipo, LocalDateTime procesadoEn) {
        this.idEvento = idEvento;
        this.tipo = tipo;
        this.procesadoEn = procesadoEn;
    }

    @Override
    public String getId() {
        return idEvento;
    }

    /** Siempre es nuevo: se inserta directo (si ya existía, la PK lo rechaza). */
    @Override
    public boolean isNew() {
        return true;
    }
}
