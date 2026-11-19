package pe.alcanfores.catalogo.servicio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import pe.alcanfores.catalogo.dto.FotoActualizacionSolicitud;
import pe.alcanfores.catalogo.dto.FotoRespuesta;
import pe.alcanfores.catalogo.mapeador.CanchaMapeador;
import pe.alcanfores.catalogo.modelo.Cancha;
import pe.alcanfores.catalogo.modelo.CanchaFoto;
import pe.alcanfores.catalogo.repositorio.CanchaFotoRepositorio;
import pe.alcanfores.catalogo.repositorio.CanchaRepositorio;
import pe.alcanfores.catalogo.servicio.AlmacenFotos;
import pe.alcanfores.catalogo.servicio.FotoServicio;
import pe.alcanfores.catalogo.util.Imagenes;
import pe.alcanfores.comun.auditoria.Auditor;
import pe.alcanfores.comun.error.NegocioExcepcion;
import pe.alcanfores.comun.util.Texto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FotoServicioImpl implements FotoServicio {

    static final String ARCHIVO_INVALIDO = "Sube una imagen JPG, PNG o WebP de hasta 5 MB.";
    static final String FOTO_NO_ENCONTRADA = "No encontramos esa foto.";

    private final CanchaRepositorio canchas;
    private final CanchaFotoRepositorio fotos;
    private final AlmacenFotos almacen;
    private final CanchaMapeador mapeador;
    private final Auditor auditor;

    @Override
    @Transactional
    public FotoRespuesta subir(Long idCancha, MultipartFile archivo, String leyenda) {
        Cancha cancha = canchas.findById(idCancha)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(CanchaServicioImpl.NO_ENCONTRADA));
        byte[] contenido = leerSubida(archivo);
        Imagenes.Tipo tipo = Imagenes.detectar(contenido)
                .orElseThrow(() -> NegocioExcepcion.validacion("archivo", ARCHIVO_INVALIDO));
        Optional<Imagenes.Dimensiones> dim = Imagenes.dimensiones(contenido);
        if (tipo != Imagenes.Tipo.WEBP) {
            // JPG y PNG siempre se pueden leer en Java: si no, el archivo está dañado o es otro formato disfrazado.
            Imagenes.Dimensiones d = dim.orElseThrow(() -> NegocioExcepcion.validacion("archivo", ARCHIVO_INVALIDO));
            if (d.ancho() < LADO_MINIMO || d.alto() < LADO_MINIMO) {
                throw NegocioExcepcion.validacion("archivo", "La foto debe medir al menos " + LADO_MINIMO + " px por lado.");
            }
        }
        if (fotos.countByIdCancha(idCancha) >= MAXIMO_POR_CANCHA) {
            throw NegocioExcepcion.regla("LIMITE_FOTOS", "Cada cancha admite hasta " + MAXIMO_POR_CANCHA
                    + " fotos. Borra alguna antes de subir otra.");
        }

        String nombre = almacen.nombreNuevo(tipo.extension());
        almacen.guardar(nombre, contenido);
        alRevertir(() -> almacen.borrar(nombre));

        CanchaFoto f = new CanchaFoto();
        f.setIdCancha(idCancha);
        f.setArchivo(nombre);
        f.setLeyenda(leyendaLimpia(leyenda));
        f.setOrden(fotos.maximoOrden(idCancha) + 1);
        f.setPrincipal(fotos.countByIdCancha(idCancha) == 0);
        dim.ifPresent(d -> {
            f.setAncho(d.ancho());
            f.setAlto(d.alto());
        });
        fotos.saveAndFlush(f);
        auditor.registrar("SUBIR_FOTO", "cancha_foto", f.getId(), cancha.getNombre() + " · " + nombre
                + (f.isPrincipal() ? " · principal" : ""));
        return mapeador.foto(f);
    }

    @Override
    @Transactional
    public FotoRespuesta actualizar(Long idCancha, Long idFoto, FotoActualizacionSolicitud cambios) {
        if (cambios == null || cambios.vacia()) {
            throw NegocioExcepcion.validacion("foto", "Envía al menos un cambio: leyenda, orden o principal.");
        }
        CanchaFoto f = buscar(idCancha, idFoto);
        if (Boolean.FALSE.equals(cambios.principal()) && f.isPrincipal()) {
            throw NegocioExcepcion.regla("FOTO_PRINCIPAL_REQUERIDA",
                    "La cancha necesita una foto principal. Marca otra como principal y esta dejará de serlo.");
        }
        if (Boolean.TRUE.equals(cambios.principal()) && !f.isPrincipal()) {
            // Una sola principal por cancha: se desmarcan las demás (la consulta limpia el contexto, se vuelve a leer).
            fotos.quitarPrincipalExcepto(idCancha, idFoto);
            f = buscar(idCancha, idFoto);
            f.setPrincipal(true);
        }
        if (cambios.leyenda() != null) {
            f.setLeyenda(leyendaLimpia(cambios.leyenda()));
        }
        if (cambios.orden() != null) {
            f.setOrden(cambios.orden());
        }
        fotos.saveAndFlush(f);
        auditor.registrar("EDITAR_FOTO", "cancha_foto", idFoto, "Cancha " + idCancha + " · " + f.getArchivo()
                + (f.isPrincipal() ? " · principal" : "") + " · orden " + f.getOrden());
        return mapeador.foto(f);
    }

    @Override
    @Transactional
    public void eliminar(Long idCancha, Long idFoto) {
        CanchaFoto f = buscar(idCancha, idFoto);
        String archivo = f.getArchivo();
        boolean eraPrincipal = f.isPrincipal();
        fotos.delete(f);
        fotos.flush();
        if (eraPrincipal) {
            // La siguiente foto por orden pasa a ser la principal: nunca queda una cancha con fotos y sin portada.
            List<CanchaFoto> restantes = fotos.findByIdCanchaOrderByOrdenAscIdAsc(idCancha);
            if (!restantes.isEmpty()) {
                restantes.get(0).setPrincipal(true);
            }
        }
        alConfirmar(() -> almacen.borrar(archivo));
        auditor.registrar("BORRAR_FOTO", "cancha_foto", idFoto, "Cancha " + idCancha + " · " + archivo);
    }

    @Override
    public ArchivoFoto leer(String archivo) {
        return almacen.leer(archivo)
                .map(r -> new ArchivoFoto(r, Imagenes.Tipo.porExtension(archivo).map(Imagenes.Tipo::mime)
                        .orElse("application/octet-stream")))
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(FOTO_NO_ENCONTRADA));
    }

    private CanchaFoto buscar(Long idCancha, Long idFoto) {
        return fotos.findByIdAndIdCancha(idFoto, idCancha)
                .orElseThrow(() -> NegocioExcepcion.noEncontrado(FOTO_NO_ENCONTRADA));
    }

    private static byte[] leerSubida(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > TAMANIO_MAXIMO) {
            throw NegocioExcepcion.validacion("archivo", ARCHIVO_INVALIDO);
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    private static String leyendaLimpia(String leyenda) {
        String l = Texto.limpiar(leyenda);
        return l == null || l.isEmpty() ? null : l;
    }

    /** Borra el archivo recién escrito si la transacción se revierte (no quedan huérfanos en disco). */
    private static void alRevertir(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado == STATUS_ROLLED_BACK) {
                        accion.run();
                    }
                }
            });
        }
    }

    /** Borra el archivo solo cuando el registro ya se eliminó de verdad. */
    private static void alConfirmar(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
