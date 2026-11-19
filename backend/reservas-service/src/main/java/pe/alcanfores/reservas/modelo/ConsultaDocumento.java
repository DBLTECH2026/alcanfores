package pe.alcanfores.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Caché de las consultas a RENIEC/SUNAT para no repetir llamadas al proveedor: 30 días si se encontró el documento y
 * 1 día si el proveedor dijo que no existe ({@link #FUENTE_NO_ENCONTRADO}, respuesta {@code {}}).
 */
@Entity
@Table(name = "consulta_documento")
@Getter
@Setter
@NoArgsConstructor
public class ConsultaDocumento {

    /** Fuente de una consulta cuyo documento no existe (caché negativa). */
    public static final String FUENTE_NO_ENCONTRADO = "NO_ENCONTRADO";

    @EmbeddedId
    private Clave clave;

    /** Respuesta ya normalizada (JSON del DTO que devuelve este servicio). */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String respuesta;

    @Column(nullable = false, length = 40)
    private String fuente;

    @Column(nullable = false)
    private LocalDateTime consultadoEn;

    public boolean encontrado() {
        return !FUENTE_NO_ENCONTRADO.equals(fuente);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Clave implements Serializable {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private TipoDocumento tipo;

        @Column(nullable = false, length = 11)
        private String numero;

        public Clave(TipoDocumento tipo, String numero) {
            this.tipo = tipo;
            this.numero = numero;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && tipo == c.tipo && numero.equals(c.numero);
        }

        @Override
        public int hashCode() {
            return 31 * tipo.hashCode() + numero.hashCode();
        }
    }
}
