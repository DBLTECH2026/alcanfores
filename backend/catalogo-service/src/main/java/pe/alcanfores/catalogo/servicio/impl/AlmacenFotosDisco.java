package pe.alcanfores.catalogo.servicio.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pe.alcanfores.catalogo.config.CatalogoPropiedades;
import pe.alcanfores.catalogo.servicio.AlmacenFotos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Fotos en disco, en {@code ${UPLOADS_DIR}/canchas}. Escritura atómica (archivo temporal + mover). */
@Component
public class AlmacenFotosDisco implements AlmacenFotos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenFotosDisco.class);
    private static final Pattern NOMBRE = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final Path carpeta;

    public AlmacenFotosDisco(CatalogoPropiedades propiedades) {
        this.carpeta = Path.of(propiedades.uploadsDir(), "canchas").toAbsolutePath().normalize();
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de fotos " + carpeta, e);
        }
        log.info("Fotos de canchas en {}", carpeta);
    }

    @Override
    public String nombreNuevo(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    @Override
    public boolean nombreValido(String archivo) {
        return archivo != null && NOMBRE.matcher(archivo).matches();
    }

    @Override
    public void guardar(String archivo, byte[] contenido) {
        Path destino = ruta(archivo).orElseThrow(() -> new IllegalArgumentException("Nombre de archivo no válido: " + archivo));
        try {
            Path temporal = Files.createTempFile(carpeta, "subida-", ".tmp");
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto " + archivo, e);
        }
    }

    @Override
    public Optional<Resource> leer(String archivo) {
        return ruta(archivo).filter(Files::isRegularFile).map(FileSystemResource::new);
    }

    @Override
    public void borrar(String archivo) {
        ruta(archivo).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("No se pudo borrar la foto {}: {}", archivo, e.getMessage());
            }
        });
    }

    @Override
    public boolean tieneMismoContenido(String archivo, byte[] contenido) {
        Optional<Path> p = ruta(archivo).filter(Files::isRegularFile);
        if (p.isEmpty()) {
            return false;
        }
        try {
            return Files.size(p.get()) == contenido.length && Arrays.equals(Files.readAllBytes(p.get()), contenido);
        } catch (IOException e) {
            return false;
        }
    }

    /** Solo nombres UUID dentro de la carpeta: bloquea «..», barras y rutas absolutas. */
    private Optional<Path> ruta(String archivo) {
        if (!nombreValido(archivo)) {
            return Optional.empty();
        }
        Path p = carpeta.resolve(archivo).normalize();
        return p.startsWith(carpeta) ? Optional.of(p) : Optional.empty();
    }
}
