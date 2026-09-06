package com.caritasem.ruleuler.server.function;

import com.caritasem.ruleuler.server.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class FunctionJarService {

    private final FunctionJarDao jarDao;
    private final FunctionAlSupport alSupport;
    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;
    private final int maxSizeBytes;
    private final int maxEntries;
    private final int maxXmlBytes;

    public FunctionJarService(FunctionJarDao jarDao,
                              FunctionAlSupport alSupport,
                              JdbcTemplate jdbc,
                              AuditLogService auditLogService,
                              @Value("${ruleuler.function-jar.max-size-bytes}") int maxSizeBytes,
                              @Value("${ruleuler.function-jar.max-entries}") int maxEntries,
                              @Value("${ruleuler.function-jar.max-uncompressed-xml-bytes}") int maxXmlBytes) {
        this.jarDao = jarDao;
        this.alSupport = alSupport;
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
        this.maxSizeBytes = maxSizeBytes;
        this.maxEntries = maxEntries;
        this.maxXmlBytes = maxXmlBytes;
    }

    public Map<String, Object> upload(String project, MultipartFile file, String operator) throws Exception {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("project 必填");
        }
        assertProjectExists(project);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未上传 jar");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("jar 超过大小上限");
        }
        byte[] bytes = file.getBytes();
        FunctionJarExtractor.Extracted extracted = FunctionJarExtractor.extract(bytes, maxEntries, maxXmlBytes);
        FunctionAlSupport.ParsedAl parsed = alSupport.parse(extracted.alXml());
        String fileVersion = extracted.versions().get(parsed.functionPackage());
        if (fileVersion == null) {
            throw new IllegalArgumentException("jar 缺少 META-INF/ruleuler/" + parsed.functionPackage() + ".version");
        }
        if (!fileVersion.equals(parsed.functionVersion())) {
            throw new IllegalArgumentException("version 文件(" + fileVersion + ") 与 function-version("
                    + parsed.functionVersion() + ") 不一致");
        }
        String alPath = "/" + project + "/lib/" + parsed.functionPackage() + ".al.xml";
        alSupport.assertNoBeanIdClash(project, alPath, parsed.beanIds());
        upsertAlFile(project, alPath, parsed.functionPackage() + ".al.xml", parsed.xml(), operator);
        String checksum = sha256(bytes);
        jarDao.upsert(project, parsed.functionPackage(), parsed.functionVersion(), checksum, bytes, operator);
        auditLogService.log("UPLOAD", "FUNCTION_JAR", null, alPath, project, operator,
                Map.of("functionPackage", parsed.functionPackage(), "version", parsed.functionVersion(),
                        "checksum", checksum), null);
        return Map.of(
                "functionPackage", parsed.functionPackage(),
                "version", parsed.functionVersion(),
                "checksum", checksum,
                "alPath", alPath);
    }

    public List<Map<String, Object>> list(String project) {
        assertProjectExists(project);
        return jarDao.listByProject(project);
    }

    private void assertProjectExists(String project) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE project=? AND path=?",
                Integer.class, project, "/" + project);
        if (n == null || n == 0) {
            throw new IllegalArgumentException("项目不存在: " + project);
        }
    }

    private void upsertAlFile(String project, String path, String name, String content, String operator) {
        ensureDir(project, "/" + project + "/lib", "lib", operator);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?", Integer.class, path);
        long now = System.currentTimeMillis();
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE ruleuler_rule_file SET content=?, update_user=?, updated_at=? WHERE path=?",
                    content, operator, now, path);
            return;
        }
        jdbc.update("""
                INSERT INTO ruleuler_rule_file
                  (project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at)
                VALUES (?,?,?,0,?,?,?,?,?,?)
                """, project, path, name, content, operator, operator, null, now, now);
    }

    private void ensureDir(String project, String path, String name, String operator) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path=?", Integer.class, path);
        if (exists != null && exists > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO ruleuler_rule_file
                  (project,path,name,is_dir,content,create_user,update_user,company_id,created_at,updated_at)
                VALUES (?,?,?,1,NULL,?,?,?,?,?)
                """, project, path, name, operator, operator, null, now, now);
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
