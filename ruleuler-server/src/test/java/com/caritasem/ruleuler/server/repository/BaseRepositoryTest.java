package com.caritasem.ruleuler.server.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.UUID;

public abstract class BaseRepositoryTest {

    protected static DataSource dataSource;
    protected static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initDb() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM ruleuler_rule_file_version");
        jdbcTemplate.execute("DELETE FROM ruleuler_rule_file");
        jdbcTemplate.execute("DELETE FROM ruleuler_project_storage");
    }
}
