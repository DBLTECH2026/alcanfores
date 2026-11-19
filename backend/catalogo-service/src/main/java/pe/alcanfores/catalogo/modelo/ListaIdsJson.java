package pe.alcanfores.catalogo.modelo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/** Lista de ids ↔ arreglo JSON; {@code null} se guarda como NULL (= todas las canchas). Igual que {@link ListaTextoJson}. */
@Converter
public class ListaIdsJson implements AttributeConverter<List<Long>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Long>> LISTA = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Long> lista) {
        if (lista == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(lista);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo convertir la lista de canchas a JSON", e);
        }
    }

    @Override
    public List<Long> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return null;
        }
        try {
            return List.copyOf(JSON.readValue(json, LISTA));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La columna canchas no tiene un arreglo JSON válido", e);
        }
    }
}
