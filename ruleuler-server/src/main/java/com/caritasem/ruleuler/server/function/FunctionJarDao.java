package com.caritasem.ruleuler.server.function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FunctionJarDao {

    private final JdbcTemplate jdbc;

    public FunctionJarDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String project, String functionPackage, String version,
                       String checksum, byte[] blob, String uploadedBy) {
        long now = System.currentTimeMillis();
        jdbc.update("""
                        INSERT INTO ruleuler_function_jar
                          (project, function_package, version, checksum, `blob`, uploaded_by, uploaded_at)
                        VALUES (?,?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE checksum=VALUES(checksum), `blob`=VALUES(`blob`),
                          uploaded_by=VALUES(uploaded_by), uploaded_at=VALUES(uploaded_at)
                        """,
                project, functionPackage, version, checksum, blob, uploadedBy, now);
    }

    public Optional<String> findChecksum(String project, String functionPackage, String version) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT checksum FROM ruleuler_function_jar
                WHERE project=? AND function_package=? AND version=?
                """, project, functionPackage, version);
        if (rows.isEmpty() || rows.get(0).get("checksum") == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(rows.get(0).get("checksum")));
    }

    public Optional<byte[]> findBlob(String project, String functionPackage, String version, String checksum) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT `blob` FROM ruleuler_function_jar
                WHERE project=? AND function_package=? AND version=? AND checksum=?
                """, project, functionPackage, version, checksum);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((byte[]) rows.get(0).get("blob"));
    }

    public List<Map<String, Object>> listByProject(String project) {
        return jdbc.queryForList("""
                SELECT function_package, version, checksum, uploaded_by, uploaded_at
                FROM ruleuler_function_jar WHERE project=?
                ORDER BY function_package, uploaded_at DESC
                """, project);
    }
}
