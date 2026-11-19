package pe.alcanfores.catalogo.modelo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de textos ↔ arreglo JSON en una columna {@code JSON}. Se usa un convertidor (y no el tipo JSON nativo de
 * Hibernate) porque MariaDB guarda JSON como LONGTEXT y MySQL 8 como JSON: ambos se leen como texto largo, así la
 * validación del esquema pasa en los dos motores.
 */
@Converter
public class ListaTextoJson implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> lista) {
        try {
            return JSON.writeValueAsString(lista == null ? List.of() : lista);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo convertir la lista a JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSON.readValue(json, LISTA));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La columna caracteristicas no tiene un arreglo JSON válido", e);
        }
    }
}
