package pe.alcanfores.pagos.servicio;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.pagos.modelo.Serie;
import pe.alcanfores.pagos.repositorio.SerieRepositorio;

/**
 * Correlativos sin huecos ni duplicados: bloquea la fila de la serie ({@code SELECT … FOR UPDATE}) dentro de la MISMA
 * transacción que guarda el comprobante; si esa transacción se revierte, el número no se consume.
 */
@Component
public class NumeracionComprobantes {

    private final SerieRepositorio series;

    public NumeracionComprobantes(SerieRepositorio series) {
        this.series = series;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int siguiente(String serie) {
        Serie s = series.bloquear(serie)
                .orElseThrow(() -> new IllegalStateException("No existe la serie de comprobantes " + serie));
        s.setUltimoCorrelativo(s.getUltimoCorrelativo() + 1);
        return s.getUltimoCorrelativo();
    }
}
