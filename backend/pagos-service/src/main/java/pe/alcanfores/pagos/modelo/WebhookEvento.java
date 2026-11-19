package pe.alcanfores.pagos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bitácora de webhooks recibidos; (proveedor, id_externo) único garantiza procesar cada aviso una sola vez. */
@Entity
@Table(name = "webhook_evento")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProveedorPasarela proveedor;

    @Column(name = "id_externo", length = 80)
    private String idExterno;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "firma_valida", nullable = false)
    private boolean firmaValida;

    @Column(columnDefinition = "json")
    private String payload;

    @Column(nullable = false)
    private boolean procesado;

    @Column(length = 255)
    private String resultado;

    @Column(name = "recibido_en", nullable = false)
    private LocalDateTime recibidoEn;
}
