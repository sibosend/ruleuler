package com.caritasem.ruleuler.server.repository;

import com.caritasem.ruleuler.server.audit.AuditLogService;
import com.bstek.urule.RuleException;
import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.RepositoryInteceptor;
import com.bstek.urule.console.User;
import com.bstek.urule.console.repository.NodeLockException;
import com.bstek.urule.console.repository.permission.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DbRepositoryDelegateLockTest extends BaseRepositoryTest {

    private DbRepositoryDelegate delegate;
    private User user1;
    private User user2;

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

        DefaultUser u1 = new DefaultUser();
        u1.setUsername("alice");
        u1.setCompanyId("C001");
        u1.setAdmin(true);
        user1 = u1;

        DefaultUser u2 = new DefaultUser();
        u2.setUsername("bob");
        u2.setCompanyId("C001");
        u2.setAdmin(true);
        user2 = u2;
    }

    @Test
    void lockPath_sets_lock_user() throws Exception {
        delegate.createProject("lk1", user1, false);
        delegate.createFile("/lk1/rule.xml", "<r/>", user1);

        delegate.lockPath("/lk1/rule.xml", user1);

        String lockUser = jdbcTemplate.queryForObject(
                "SELECT lock_user FROM ruleuler_rule_file WHERE path='/lk1/rule.xml'", String.class);
        assertEquals("alice", lockUser);
    }

    @Test
    void lockPath_already_locked_throws_NodeLockException() throws Exception {
        delegate.createProject("lk2", user1, false);
        delegate.createFile("/lk2/rule.xml", "<r/>", user1);
        delegate.lockPath("/lk2/rule.xml", user1);

        assertThrows(NodeLockException.class, () -> delegate.lockPath("/lk2/rule.xml", user2));
    }

    @Test
    void unlockPath_by_non_holder_throws_NodeLockException() throws Exception {
        delegate.createProject("lk3", user1, false);
        delegate.createFile("/lk3/rule.xml", "<r/>", user1);
        delegate.lockPath("/lk3/rule.xml", user1);

        assertThrows(NodeLockException.class, () -> delegate.unlockPath("/lk3/rule.xml", user2));
    }

    @Test
    void saveFile_optimistic_lock_conflict_throws() throws Exception {
        delegate.createProject("lk4", user1, false);
        delegate.createFile("/lk4/rule.xml", "v1", user1);

        delegate.saveFile("/lk4/rule.xml", "v2", false, null, user1);

        JdbcTemplate spyJdbc = spy(jdbcTemplate);
        doReturn(99999L).when(spyJdbc).queryForObject(
                eq("SELECT updated_at FROM ruleuler_rule_file WHERE path=?"),
                eq(Long.class),
                eq("/lk4/rule.xml"));

        DbRepositoryDelegate conflictDelegate = new DbRepositoryDelegate();
        conflictDelegate.setJdbcTemplate(spyJdbc);

        PermissionService ps2 = mock(PermissionService.class);
        when(ps2.isAdmin()).thenReturn(true);
        when(ps2.fileHasWritePermission(anyString())).thenReturn(true);
        when(ps2.projectPackageHasWritePermission(anyString())).thenReturn(true);
        conflictDelegate.setPermissionService(ps2);

        ApplicationContext ctx2 = mock(ApplicationContext.class);
        when(ctx2.getBeansOfType(RepositoryInteceptor.class)).thenReturn(Collections.emptyMap());
        when(ctx2.getBean(PermissionService.class)).thenReturn(ps2);
        when(ctx2.getBean(JdbcTemplate.class)).thenReturn(spyJdbc);
        conflictDelegate.setApplicationContext(ctx2);

        assertThrows(RuleException.class,
                () -> conflictDelegate.saveFile("/lk4/rule.xml", "v3", false, null, user1));
    }
}
