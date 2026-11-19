package pe.alcanfores.pagos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** El contexto arranca con Flyway + validación del esquema (ddl-auto: validate) sobre alcanfores_pagos_test. */
@SpringBootTest
@ActiveProfiles("test")
class ArranqueTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void arrancaYCreaLasSeries() {
        assertThat(jdbc.queryForList("SELECT serie FROM serie ORDER BY serie", String.class))
                .containsExactly("B001", "BC01", "F001", "FC01");
    }
}
