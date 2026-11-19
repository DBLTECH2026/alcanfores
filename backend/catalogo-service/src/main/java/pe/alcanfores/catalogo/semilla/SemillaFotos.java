package pe.alcanfores.catalogo.semilla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.CanchaFoto;
import pe.alcanfores.catalogo.repositorio.CanchaFotoRepositorio;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.servicio.AlmacenFotos;
import pe.alcanfores.catalogo.util.Imagenes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fotos de demostración: copia a {@code uploads/canchas/} lo que haya en {@code classpath:semilla/fotos/} con nombres
 * {@code cancha<idCancha>-<n>.jpg} (también .png o .webp) y lo registra en {@code cancha_foto}; la {@code -1} es la principal.
 * <ul>
 *   <li>Solo registra fotos en canchas que aún no tienen ninguna (no pisa lo que subió el administrador).</li>
 *   <li>El nombre en disco es un UUID derivado del nombre de la semilla: si se reemplaza la imagen de la semilla y se
 *       reinicia el servicio, el archivo en disco se actualiza sin crear registros nuevos.</li>
 *   <li>Leyendas opcionales en {@code semilla/fotos/leyendas.properties} ({@code cancha1-1.jpg=Vista general}).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "alcanfores.catalogo", name = "semilla-fotos", havingValue = "true", matchIfMissing = true)
public class SemillaFotos implements ApplicationRunner {

    static final String CARPETA = "classpath*:semilla/fotos/*";
    static final Pattern NOMBRE = Pattern.compile("^cancha(\\d{1,4})-(\\d{1,2})\\.(jpe?g|png|webp)$", Pattern.CASE_INSENSITIVE);

    private static final Logger log = LoggerFactory.getLogger(SemillaFotos.class);

    private final CanchaRepositorio canchas;
    private final CanchaFotoRepositorio fotos;
    private final AlmacenFotos almacen;
    private final ResourcePatternResolver recursos;

    public SemillaFotos(CanchaRepositorio canchas, CanchaFotoRepositorio fotos, AlmacenFotos almacen) {
        this.canchas = canchas;
        this.fotos = fotos;
        this.almacen = almacen;
        this.recursos = new PathMatchingResourcePatternResolver(SemillaFotos.class.getClassLoader());
    }

    /** Una foto de la semilla ya leída y validada. */
    record FotoSemilla(String nombreOrigen, long idCancha, int numero, byte[] contenido, Imagenes.Tipo tipo) {
        String archivoDestino() {
            return UUID.nameUUIDFromBytes(("semilla/" + nombreOrigen.toLowerCase()).getBytes(StandardCharsets.UTF_8))
                    + "." + tipo.extension();
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Properties leyendas = leyendas();
        Map<Long, List<FotoSemilla>> porCancha = new TreeMap<>();
        for (Resource r : recursos.getResources(CARPETA)) {
            leer(r).ifPresent(f -> porCancha.computeIfAbsent(f.idCancha(), k -> new ArrayList<>()).add(f));
        }
        int registradas = 0;
        int actualizadas = 0;
        for (var entrada : porCancha.entrySet()) {
            Optional<Cancha> cancha = canchas.findById(entrada.getKey());
            if (cancha.isEmpty()) {
                log.warn("Semilla de fotos: no existe la cancha {}; se omiten sus fotos", entrada.getKey());
                continue;
            }
            List<FotoSemilla> lista = entrada.getValue();
            lista.sort(Comparator.comparingInt(FotoSemilla::numero));
            if (fotos.countByIdCancha(entrada.getKey()) == 0) {
                registradas += registrar(cancha.get(), lista, leyendas);
            } else {
                actualizadas += refrescar(lista);
            }
        }
        if (registradas + actualizadas > 0) {
            log.info("Semilla de fotos: {} registradas, {} archivos actualizados", registradas, actualizadas);
        }
    }

    private int registrar(Cancha cancha, List<FotoSemilla> lista, Properties leyendas) {
        boolean hayPrincipal = lista.stream().anyMatch(f -> f.numero() == 1);
        for (int i = 0; i < lista.size(); i++) {
            FotoSemilla s = lista.get(i);
            String archivo = s.archivoDestino();
            almacen.guardar(archivo, s.contenido());
            CanchaFoto f = fotos.findByArchivo(archivo).orElseGet(CanchaFoto::new);
            f.setIdCancha(cancha.getId());
            f.setArchivo(archivo);
            f.setLeyenda(leyendas.getProperty(s.nombreOrigen().toLowerCase(), cancha.getNombre()));
            f.setOrden(s.numero());
            f.setPrincipal(hayPrincipal ? s.numero() == 1 : i == 0);
            Imagenes.dimensiones(s.contenido()).ifPresent(d -> {
                f.setAncho(d.ancho());
                f.setAlto(d.alto());
            });
            fotos.save(f);
        }
        return lista.size();
    }

    /** La cancha ya tiene fotos: solo se reescriben en disco las de la semilla que siguen registradas y cambiaron. */
    private int refrescar(List<FotoSemilla> lista) {
        int n = 0;
        for (FotoSemilla s : lista) {
            String archivo = s.archivoDestino();
            Optional<CanchaFoto> registrada = fotos.findByArchivo(archivo);
            if (registrada.isPresent() && !almacen.tieneMismoContenido(archivo, s.contenido())) {
                almacen.guardar(archivo, s.contenido());
                Imagenes.dimensiones(s.contenido()).ifPresent(d -> {
                    registrada.get().setAncho(d.ancho());
                    registrada.get().setAlto(d.alto());
                });
                n++;
            }
        }
        return n;
    }

    private Optional<FotoSemilla> leer(Resource r) {
        String nombre = r.getFilename();
        Matcher m = nombre == null ? null : NOMBRE.matcher(nombre);
        if (m == null || !m.matches()) {
            return Optional.empty();
        }
        try (InputStream in = r.getInputStream()) {
            byte[] contenido = in.readAllBytes();
            Optional<Imagenes.Tipo> tipo = Imagenes.detectar(contenido);
            if (tipo.isEmpty()) {
                log.warn("Semilla de fotos: {} no es una imagen JPG, PNG ni WebP; se omite", nombre);
                return Optional.empty();
            }
            return Optional.of(new FotoSemilla(nombre, Long.parseLong(m.group(1)), Integer.parseInt(m.group(2)), contenido,
                    tipo.get()));
        } catch (IOException e) {
            log.warn("Semilla de fotos: no se pudo leer {}: {}", nombre, e.getMessage());
            return Optional.empty();
        }
    }

    private Properties leyendas() throws IOException {
        Properties p = new Properties();
        Resource[] encontrados = recursos.getResources("classpath*:semilla/fotos/leyendas.properties");
        if (encontrados.length > 0) {
            try (InputStreamReader lector = new InputStreamReader(encontrados[0].getInputStream(), StandardCharsets.UTF_8)) {
                p.load(lector);
            }
        }
        return p;
    }
}
