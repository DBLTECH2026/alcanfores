package pe.alcanfores.gateway.limite;

/**
 * Token bucket: arranca llena con {@code capacidad} fichas y se recarga de forma continua a {@code capacidad} por
 * minuto. Cada solicitud consume una ficha; sin fichas, se informa cuántos segundos faltan para la siguiente.
 */
final class CubetaTokens {

    private static final double NANOS_POR_MINUTO = 60_000_000_000d;
    private static final long NANOS_POR_SEGUNDO = 1_000_000_000L;

    private final int capacidad;
    private final double recargaPorNano;
    private double fichas;
    private long ultimaRecarga;
    private long ultimoUso;

    CubetaTokens(int capacidad, long ahoraNanos) {
        this.capacidad = capacidad;
        this.recargaPorNano = capacidad / NANOS_POR_MINUTO;
        this.fichas = capacidad;
        this.ultimaRecarga = ahoraNanos;
        this.ultimoUso = ahoraNanos;
    }

    synchronized Consumo consumir(long ahoraNanos) {
        recargar(ahoraNanos);
        ultimoUso = ahoraNanos;
        if (fichas >= 1) {
            fichas -= 1;
            return new Consumo(true, (int) Math.floor(fichas), 0);
        }
        long esperaNanos = (long) Math.ceil((1 - fichas) / recargaPorNano);
        long segundos = Math.max(1, (esperaNanos + NANOS_POR_SEGUNDO - 1) / NANOS_POR_SEGUNDO);
        return new Consumo(false, 0, segundos);
    }

    /** Llena y sin uso desde hace un rato: se puede descartar sin cambiar el comportamiento del límite. */
    synchronized boolean ociosa(long ahoraNanos, long inactividadNanos) {
        recargar(ahoraNanos);
        return fichas >= capacidad && ahoraNanos - ultimoUso >= inactividadNanos;
    }

    private void recargar(long ahoraNanos) {
        long transcurrido = Math.max(0, ahoraNanos - ultimaRecarga);
        fichas = Math.min(capacidad, fichas + transcurrido * recargaPorNano);
        ultimaRecarga = ahoraNanos;
    }

    /** Resultado de pedir una ficha. */
    record Consumo(boolean permitido, int restantes, long esperaSegundos) {
    }
}
