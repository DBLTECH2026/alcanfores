package pe.alcanfores.comun.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Emisión y validación de JWT HS256. */
class JwtServicioTest {

    static final String SECRETO = "clave-de-prueba-de-al-menos-32-bytes-123456";
    private final JwtServicio jwt = new JwtServicio(SECRETO, 8);
    private final UsuarioToken rosa = new UsuarioToken(7L, "rhuaman", "Rosa Huamán", UsuarioToken.RECEPCION);

    @Test
    void emiteYValidaConTodosLosDatos() {
        UsuarioToken u = jwt.validar(jwt.generar(rosa));
        assertThat(u).isEqualTo(rosa);
        assertThat(u.esAdmin()).isFalse();
    }

    @Test
    void elTokenLlevaEmisorSujetoYVencimientoEnHoras() {
        String token = jwt.generar(rosa);
        Claims c = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload();
        assertThat(c.getIssuer()).isEqualTo(JwtServicio.EMISOR);
        assertThat(c.getSubject()).isEqualTo("7");
        assertThat(c.get("rol", String.class)).isEqualTo("RECEPCION");
        long segundos = (c.getExpiration().getTime() - c.getIssuedAt().getTime()) / 1000;
        assertThat(segundos).isEqualTo(8 * 3600);
    }

    @Test
    void expiraEnYHoras() {
        assertThat(jwt.getHoras()).isEqualTo(8);
        assertThat(jwt.expiraEn()).isCloseTo(Instant.now().plus(Duration.ofHours(8)), within(Duration.ofSeconds(5)));
    }

    @Test
    void tokenVencidoLanzaExpiredJwtException() {
        JwtServicio vencido = new JwtServicio(SECRETO, -1); // vence una hora antes de emitirse
        String token = vencido.generar(rosa);
        assertThatThrownBy(() -> jwt.validar(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void firmaConOtraClaveEsRechazada() {
        String token = new JwtServicio("otra-clave-distinta-de-al-menos-32-bytes-99", 8).generar(rosa);
        assertThatThrownBy(() -> jwt.validar(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void tokenAlteradoOMalFormadoEsRechazado() {
        String token = jwt.generar(rosa);
        String[] partes = token.split("\\.");
        String payloadAjeno = Jwts.builder().subject("1").claim("rol", "ADMIN").issuer(JwtServicio.EMISOR)
                .compact().split("\\.")[1];
        String alterado = partes[0] + "." + payloadAjeno + "." + partes[2];
        assertThatThrownBy(() -> jwt.validar(alterado)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.validar("no-es-un-jwt")).isInstanceOf(MalformedJwtException.class);
        assertThatThrownBy(() -> jwt.validar("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emisorDistintoEsRechazado() {
        String token = Jwts.builder().issuer("otro-sistema").subject("7").claim("rol", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> jwt.validar(token)).isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void tokenSinFirmaEsRechazado() {
        String sinFirma = Jwts.builder().issuer(JwtServicio.EMISOR).subject("7").claim("rol", "ADMIN").compact();
        assertThatThrownBy(() -> jwt.validar(sinFirma)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretoCortoONuloNoSeAcepta() {
        assertThatThrownBy(() -> new JwtServicio("corto", 8)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtServicio(null, 8)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtServicio("a".repeat(31), 8)).isInstanceOf(IllegalStateException.class);
        assertThat(new JwtServicio("a".repeat(32), 8).getHoras()).isEqualTo(8);
    }

    @Test
    void usuarioTokenEsAdminSoloConRolAdmin() {
        assertThat(new UsuarioToken(1L, "a", "A", UsuarioToken.ADMIN).esAdmin()).isTrue();
        assertThat(new UsuarioToken(1L, "a", "A", "admin").esAdmin()).isFalse();
        assertThat(new UsuarioToken(1L, "a", "A", null).esAdmin()).isFalse();
    }
}
