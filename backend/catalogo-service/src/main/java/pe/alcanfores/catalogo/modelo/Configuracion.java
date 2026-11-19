package pe.alcanfores.catalogo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Parámetro del complejo en formato clave/valor (tarifas, reglas, datos de pago y de contacto). */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Configuracion {

    @Id
    @Column(length = 40)
    private String clave;

    @Column(nullable = false, length = 500)
    private String valor;

    @Column(length = 255)
    private String descripcion;
}
