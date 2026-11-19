package pe.alcanfores.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades comunes ({@code alcanfores.*}). En cada application.yml:
 * <pre>
 * alcanfores:
 *   jwt: { secreto: ${JWT_SECRETO:...}, horas: ${JWT_HORAS:8} }
 *   interno-token: ${INTERNO_TOKEN:...}
 *   front-url: ${FRONT_URL:http://localhost:5186}
 *   public-api-url: ${PUBLIC_API_URL:http://localhost:8086}
 * </pre>
 */
@ConfigurationProperties(prefix = "alcanfores")
public class AlcanforesPropiedades {

    private final Jwt jwt = new Jwt();
    private String internoToken = "token-interno-solo-para-desarrollo-local";
    private String frontUrl = "http://localhost:5186";
    private String publicApiUrl = "http://localhost:8086";

    public Jwt getJwt() {
        return jwt;
    }

    public String getInternoToken() {
        return internoToken;
    }

    public void setInternoToken(String internoToken) {
        this.internoToken = internoToken;
    }

    public String getFrontUrl() {
        return frontUrl;
    }

    public void setFrontUrl(String frontUrl) {
        this.frontUrl = frontUrl;
    }

    public String getPublicApiUrl() {
        return publicApiUrl;
    }

    public void setPublicApiUrl(String publicApiUrl) {
        this.publicApiUrl = publicApiUrl;
    }

    public static class Jwt {
        private String secreto = "secreto-de-desarrollo-local-alcanfores-2026-cambiar-en-produccion";
        private long horas = 8;

        public String getSecreto() {
            return secreto;
        }

        public void setSecreto(String secreto) {
            this.secreto = secreto;
        }

        public long getHoras() {
            return horas;
        }

        public void setHoras(long horas) {
            this.horas = horas;
        }
    }
}
