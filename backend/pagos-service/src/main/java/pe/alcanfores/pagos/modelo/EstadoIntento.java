package pe.alcanfores.pagos.modelo;

/** Estado de una sesión de pago en línea (intento). */
public enum EstadoIntento {
    CREADO, REQUIERE_3DS, APROBADO, RECHAZADO, EXPIRADO;

    /** Todavía puede recibir un pago (los rechazos permiten reintentar con otra tarjeta mientras no expire). */
    public boolean abierto() {
        return this == CREADO || this == REQUIERE_3DS || this == RECHAZADO;
    }
}
