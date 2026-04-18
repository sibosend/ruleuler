package com.caritasem.ruleuler.server.repository;

import com.caritasem.ruleuler.server.audit.AuditLogService;
import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.RepositoryInteceptor;
import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.model.VersionFile;
import com.bstek.urule.console.repository.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DbRepositoryDelegateVersionTest extends BaseRepositoryTest {

    private DbRepositoryDelegate delegate;
    private User user;

    @BeforeEach
    void setUp() {
        delegate = new DbRepositoryDelegate();
        delegate.setJdbcTemplate(jdbcTemplate);

        PermissionService ps = mock(PermissionService.class);
        when(ps.isAdmin()).thenReturn(true);
        when(ps.fileHasWritePermission(anyString())).thenReturn(true);
        when(ps.fileHasReadPermission(anyString())).thenReturn(true);
        when(ps.projectHasPermission(anyString())).thenReturn(true);
        when(ps.projectPackageHasReadPermission(anyString())).thenReturn(true);
        when(ps.projectPackageHasWritePermission(anyString())).thenReturn(true);
        delegate.setPermissionService(ps);

        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBeansOfType(RepositoryInteceptor.class)).thenReturn(Collections.emptyMap());
        when(mockCtx.getBean(PermissionService.class)).thenReturn(ps);
        when(mockCtx.getBean(JdbcTemplate.class)).thenReturn(jdbcTemplate);
        when(mockCtx.getBean(AuditLogService.class)).thenReturn(mock(AuditLogService.class));
        delegate.setApplicationContext(mockCtx);

        DefaultUser u = new DefaultUser();
        u.setUsername("tester");
        u.setCompanyId("C001");
        u.setAdmin(true);
        user = u;
    }

    @Test
    void saveFile_newVersion_creates_version_record() throws Exception {
        delegate.createProject("vp1", user, false);
        delegate.createFile("/vp1/rule.xml", "initial", user);

        delegate.saveFile("/vp1/rule.xml", "versioned", true, "first version", user);

        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM ruleuler_rule_file WHERE path='/vp1/rule.xml'", Long.class);
        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file_version WHERE file_id=?", Integer.class, fileId);
        assertEquals(1, versionCount);

        String versionName = jdbcTemplate.queryForObject(
                "SELECT version_name FROM ruleuler_rule_file_version WHERE file_id=?", String.class, fileId);
        assertEquals("V1", versionName);
    }

    @Test
    void readFile_with_version_returns_correct_content() throws Exception {
        delegate.createProject("vp2", user, false);
        delegate.createFile("/vp2/rule.xml", "v0", user);

        delegate.saveFile("/vp2/rule.xml", "content-v1", true, "ver1", user);
        delegate.saveFile("/vp2/rule.xml", "content-v2", true, "ver2", user);

        InputStream is1 = delegate.readFile("/vp2/rule.xml", "V1");
        assertEquals("content-v1", new String(is1.readAllBytes(), StandardCharsets.UTF_8));

        InputStream is2 = delegate.readFile("/vp2/rule.xml", "V2");
        assertEquals("content-v2", new String(is2.readAllBytes(), StandardCharsets.UTF_8));

        InputStream isCurrent = delegate.readFile("/vp2/rule.xml");
        assertEquals("content-v2", new String(isCurrent.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void getVersionFiles_returns_ordered_list() throws Exception {
        delegate.createProject("vp3", user, false);
        delegate.createFile("/vp3/rule.xml", "init", user);

        delegate.saveFile("/vp3/rule.xml", "c1", true, "comment1", user);
        delegate.saveFile("/vp3/rule.xml", "c2", true, "comment2", user);
        delegate.saveFile("/vp3/rule.xml", "c3", true, "comment3", user);

        List<VersionFile> versions = delegate.getVersionFiles("/vp3/rule.xml");
        assertEquals(3, versions.size());

        assertEquals("V1", versions.get(0).getName());
        assertEquals("V2", versions.get(1).getName());
        assertEquals("V3", versions.get(2).getName());

        assertEquals("comment1", versions.get(0).getComment());
        assertEquals("tester", versions.get(0).getCreateUser());
        assertEquals("/vp3/rule.xml", versions.get(0).getPath());
    }
}
