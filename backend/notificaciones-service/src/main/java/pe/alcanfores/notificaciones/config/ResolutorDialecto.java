package pe.alcanfores.notificaciones.config;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;
import org.hibernate.type.SqlTypes;

import java.sql.Types;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solo para el MariaDB 10.4 de XAMPP (desarrollo local). Con el driver {@code mysql-connector-j} el servidor informa la
 * versión «5.5.5-10.4.32-MariaDB» y MariaDB guarda las columnas {@code JSON} como LONGTEXT, por lo que
 * {@code ddl-auto: validate} fallaría. Si la versión dice «MariaDB», se usa un {@link MariaDBDialect} con la versión real
 * que acepta LONGTEXT donde la entidad espera JSON. En MySQL 8.4 (producción) devuelve {@code null} y Hibernate resuelve
 * el dialecto como siempre. Se registra con {@code hibernate.dialect_resolvers}.
 */
public class ResolutorDialecto implements DialectResolver {

    private static final Pattern VERSION_MARIADB = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-mariadb");

    @Override
    public Dialect resolveDialect(DialectResolutionInfo info) {
        String version = info.getDatabaseVersion() == null ? "" : info.getDatabaseVersion().toLowerCase(Locale.ROOT);
        if (!version.contains("mariadb")) {
            return null;
        }
        Matcher m = VERSION_MARIADB.matcher(version);
        DatabaseVersion real = m.find()
                ? DatabaseVersion.make(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
                : DatabaseVersion.make(10, 4);
        return new MariaDbConJson(real);
    }

    /** MariaDB con JSON ≡ LONGTEXT para la validación del esquema. */
    static class MariaDbConJson extends MariaDBDialect {

        MariaDbConJson(DatabaseVersion version) {
            super(version);
        }

        @Override
        public boolean equivalentTypes(int tipoEntidad, int tipoBase) {
            return super.equivalentTypes(tipoEntidad, tipoBase)
                    || tipoEntidad == SqlTypes.JSON && tipoBase == Types.LONGVARCHAR;
        }
    }
}
