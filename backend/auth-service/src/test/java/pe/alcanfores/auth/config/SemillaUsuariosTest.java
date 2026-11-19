package pe.alcanfores.auth.config;

import org.junit.jupiter.api.Test;
import pe.alcanfores.auth.PruebaIntegracion;
import pe.alcanfores.auth.modelo.Administrador;
import pe.alcanfores.auth.modelo.Rol;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SemillaUsuariosTest extends PruebaIntegracion {

    private SemillaUsuarios semilla(boolean habilitada) {
        return new SemillaUsuarios(administradores, codificador,
                new AuthPropiedades(4, 5, 15, new AuthPropiedades.Semilla(habilitada, "ClaveAdmin1", "ClaveRecep1", "ClaveDoc1")));
    }

    @Test
    void creaLosTresUsuariosDelSpecConSusClavesCifradas() {
        semilla(true).run();

        Map<String, Administrador> porUsuario = administradores.findAll().stream()
                .collect(Collectors.toMap(Administrador::getUsuario, Function.identity()));
        assertThat(porUsuario).containsOnlyKeys("rhuaman", "mtorres", "docente");
        assertThat(porUsuario.get("rhuaman").getRol()).isEqualTo(Rol.ADMIN);
        assertThat(porUsuario.get("rhuaman").nombreCompleto()).isEqualTo("Rosa Huamán");
        assertThat(porUsuario.get("mtorres").getRol()).isEqualTo(Rol.RECEPCION);
        assertThat(porUsuario.get("docente").getRol()).isEqualTo(Rol.ADMIN);
        assertThat(porUsuario.get("rhuaman").getContrasenaHash()).startsWith("$2").doesNotContain("ClaveAdmin1");
        assertThat(codificador.matches("ClaveRecep1", porUsuario.get("mtorres").getContrasenaHash())).isTrue();
        assertThat(codificador.matches("ClaveDoc1", porUsuario.get("docente").getContrasenaHash())).isTrue();
    }

    @Test
    void noHaceNadaSiYaHayUsuarios() {
        crearUsuario("existente", Rol.ADMIN);
        semilla(true).run();
        assertThat(administradores.count()).isEqualTo(1);
    }

    @Test
    void sePuedeDesactivar() {
        semilla(false).run();
        assertThat(administradores.count()).isZero();
    }
}
