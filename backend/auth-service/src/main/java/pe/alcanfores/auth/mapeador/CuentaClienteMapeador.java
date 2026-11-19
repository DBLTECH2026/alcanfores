package pe.alcanfores.auth.mapeador;

import pe.alcanfores.auth.dto.ClienteSesion;
import pe.alcanfores.auth.modelo.CuentaCliente;
import pe.alcanfores.comun.seguridad.UsuarioToken;

/** Conversión de {@link CuentaCliente} a su vista y a los datos del JWT (rol CLIENTE + claim dni). */
public final class CuentaClienteMapeador {

    private CuentaClienteMapeador() {
    }

    public static ClienteSesion aSesion(CuentaCliente c) {
        return new ClienteSesion(c.getId(), c.getDni(), c.getNombres(), c.getApellidos(), c.getCorreo(), c.getTelefono());
    }

    /** {@code sub} = id de la cuenta, {@code usuario} = correo, {@code nombre} = «Nombres Apellidos», {@code dni}. */
    public static UsuarioToken aToken(CuentaCliente c) {
        return new UsuarioToken(c.getId(), c.getCorreo(), c.nombreCompleto(), UsuarioToken.CLIENTE, c.getDni());
    }
}
