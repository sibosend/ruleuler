package com.caritasem.ruleuler.server.function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class FunctionStatusDao {

    private final JdbcTemplate jdbc;

    public FunctionStatusDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String clientHost, String packageId, String functionPackage,
                       String expectedVersion, String actualVersion, String status) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO ruleuler_function_client_status
                  (client_host, package_id, function_package, expected_version, actual_version, status, reported_at)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE expected_version=VALUES(expected_version),
                  actual_version=VALUES(actual_version), status=VALUES(status), reported_at=VALUES(reported_at)
                """,
                clientHost, packageId, functionPackage, expectedVersion, actualVersion, status, now);
    }

    public List<Map<String, Object>> listByProject(String project) {
        return jdbc.queryForList("""
                SELECT client_host, package_id, function_package, expected_version, actual_version, status, reported_at
                FROM ruleuler_function_client_status
                WHERE package_id LIKE ?
                ORDER BY reported_at DESC
                """, project + "/%");
    }
}
