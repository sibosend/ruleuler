package com.caritasem.ruleuler.server.function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class FunctionDepsDao {

    private final JdbcTemplate jdbc;

    public FunctionDepsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(String project, String packageId, List<Map<String, String>> deps) {
        jdbc.update("DELETE FROM ruleuler_function_deps WHERE project=? AND package_id=?",
                project, packageId);
        for (Map<String, String> dep : deps) {
            jdbc.update("""
                    INSERT INTO ruleuler_function_deps
                      (project, package_id, function_package, version, checksum)
                    VALUES (?,?,?,?,?)
                    """,
                    project, packageId,
                    dep.get("functionPackage"), dep.get("version"), dep.get("checksum"));
        }
    }

    public List<Map<String, Object>> findByPackage(String project, String packageId) {
        return jdbc.queryForList("""
                SELECT function_package, version, checksum
                FROM ruleuler_function_deps WHERE project=? AND package_id=?
                """, project, packageId);
    }

    public List<Map<String, Object>> findAllCurrent() {
        return jdbc.queryForList("""
                SELECT function_package, version, checksum
                FROM ruleuler_function_deps
                """);
    }
}
