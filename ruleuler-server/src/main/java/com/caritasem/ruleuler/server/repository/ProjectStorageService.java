package com.caritasem.ruleuler.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bstek.urule.RuleException;

@Component
public class ProjectStorageService {

    private final JdbcTemplate jdbcTemplate;

    public ProjectStorageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void register(String projectName, StorageType type) {
        jdbcTemplate.update(
                "INSERT INTO ruleuler_project_storage (project_name, storage_type, created_at) VALUES (?, ?, ?)",
                projectName, type.name().toLowerCase(), System.currentTimeMillis());
    }

    public void unregister(String projectName) {
        jdbcTemplate.update("DELETE FROM ruleuler_project_storage WHERE project_name = ?", projectName);
    }

    public StorageType getStorageType(String projectName) {
        StorageType type = getStorageTypeOrNull(projectName);
        if (type == null) {
            throw new RuleException("No storage type registered for project: " + projectName);
        }
        return type;
    }

    public StorageType getStorageTypeOrNull(String projectName) {
        return jdbcTemplate.query(
                "SELECT storage_type FROM ruleuler_project_storage WHERE project_name = ?",
                rs -> rs.next() ? StorageType.fromString(rs.getString(1)) : null,
                projectName);
    }
}
