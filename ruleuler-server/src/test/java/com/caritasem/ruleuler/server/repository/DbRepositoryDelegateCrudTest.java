package com.caritasem.ruleuler.server.repository;

import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.RepositoryInteceptor;
import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.Repository;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.repository.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DbRepositoryDelegateCrudTest extends BaseRepositoryTest {

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
        delegate.setApplicationContext(mockCtx);

        DefaultUser u = new DefaultUser();
        u.setUsername("tester");
        u.setCompanyId("C001");
        u.setAdmin(true);
        user = u;
    }

    @Test
    void createProject_inserts_dir_and_init_files() throws Exception {
        RepositoryFile pf = delegate.createProject("demo", user, false);

        assertEquals("demo", pf.getName());
        assertEquals("/demo", pf.getFullPath());

        Integer dirCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path='/demo' AND is_dir=1", Integer.class);
        assertEquals(1, dirCount);

        Integer resCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path='/demo/___res__package__file__'", Integer.class);
        assertEquals(1, resCount);

        Integer clientCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path='/demo/___client_config__file__'", Integer.class);
        assertEquals(1, clientCount);
    }

    @Test
    void createFile_then_readFile_roundtrip() throws Exception {
        delegate.createProject("proj1", user, false);
        delegate.createFile("/proj1/rule.xml", "<rule>hello</rule>", user);

        InputStream is = delegate.readFile("/proj1/rule.xml");
        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("<rule>hello</rule>", content);
    }

    @Test
    void saveFile_then_readFile_content_updated() throws Exception {
        delegate.createProject("proj2", user, false);
        delegate.createFile("/proj2/test.xml", "v1", user);

        delegate.saveFile("/proj2/test.xml", "v2", false, null, user);

        InputStream is = delegate.readFile("/proj2/test.xml");
        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("v2", content);
    }

    @Test
    void deleteFile_removes_record_and_cascades_versions() throws Exception {
        delegate.createProject("proj3", user, false);
        delegate.createFile("/proj3/del.xml", "data", user);
        delegate.saveFile("/proj3/del.xml", "data-v1", true, "first version", user);

        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM ruleuler_rule_file WHERE path='/proj3/del.xml'", Long.class);
        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file_version WHERE file_id=?", Integer.class, fileId);
        assertEquals(1, versionCount);

        delegate.deleteFile("/proj3/del.xml", user);

        Integer afterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file WHERE path='/proj3/del.xml'", Integer.class);
        assertEquals(0, afterCount);

        Integer afterVersionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_rule_file_version WHERE file_id=?", Integer.class, fileId);
        assertEquals(0, afterVersionCount);
    }

    @Test
    void createDir_then_loadRepository_shows_in_tree() throws Exception {
        delegate.createProject("proj4", user, false);
        delegate.createDir("/proj4/subdir", user);
        delegate.createFile("/proj4/subdir/a.xml", "aaa", user);

        Repository repo = delegate.loadRepository("proj4", user, false, null, null);
        RepositoryFile root = repo.getRootFile();
        assertNotNull(root);

        assertFalse(root.getChildren().isEmpty());
        RepositoryFile projNode = root.getChildren().get(0);
        assertEquals("proj4", projNode.getName());
    }
}
