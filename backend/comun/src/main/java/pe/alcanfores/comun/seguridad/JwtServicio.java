package pe.alcanfores.comun.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Emite y valida JWT HS256 (RFC 7519). El secreto lo comparten auth-service, el gateway y cada servicio. */
public class JwtServicio {

    public static final String EMISOR = "alcanfores-auth";
    private final SecretKey clave;
    private final long horas;

    public JwtServicio(String secreto, long horas) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRETO debe tener al menos 32 bytes.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.horas = horas;
    }

    public String generar(UsuarioToken u) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(u.id()))
                .claim("usuario", u.usuario())
                .claim("nombre", u.nombre())
                .claim("rol", u.rol())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(horas, ChronoUnit.HOURS)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /** @throws JwtException si el token es inválido, está alterado o venció. */
    public UsuarioToken validar(String token) {
        Claims c = Jwts.parser().verifyWith(clave).requireIssuer(EMISOR).build().parseSignedClaims(token).getPayload();
        return new UsuarioToken(Long.valueOf(c.getSubject()), c.get("usuario", String.class), c.get("nombre", String.class),
                c.get("rol", String.class));
    }

    /** Momento en que vencería un token emitido ahora. */
    public Instant expiraEn() {
        return Instant.now().plus(horas, ChronoUnit.HOURS);
    }

    public long getHoras() {
        return horas;
    }
}
