package pe.alcanfores.notificaciones.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Idempotencia de los consumidores: si el {@code idEvento} ya está aquí, el mensaje repetido se ignora.
 * Es {@link Persistable} para que {@code save} haga INSERT directo: si dos copias del mismo evento llegan a la vez,
 * la clave primaria rechaza la segunda.
 */
@Entity
@Table(name = "evento_procesado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcesado implements Persistable<String> {

    @Id
    @Column(name = "id_evento", length = 36, columnDefinition = "char(36)")
    private String idEvento;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;

    @Transient
    private boolean nuevo = true;

    public EventoProcesado(String idEvento, String tipo, LocalDateTime procesadoEn) {
        this.idEvento = idEvento;
        this.tipo = tipo;
        this.procesadoEn = procesadoEn;
    }

    @Override
    public String getId() {
        return idEvento;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void marcarExistente() {
        this.nuevo = false;
    }
}
