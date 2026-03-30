package com.caritasem.ruleuler.server.repository;

import com.bstek.urule.console.DefaultUser;
import com.bstek.urule.console.User;
import com.urule.server.repository.RepositoryServiceRouter;
import com.urule.server.repository.JcrRepositoryDelegate;
import com.bstek.urule.console.repository.model.RepositoryFile;
import com.bstek.urule.console.servlet.permission.UserPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryServiceImplRouterTest {

    private JcrRepositoryDelegate jcrDelegate;
    private DbRepositoryDelegate dbDelegate;
    private ProjectStorageService pss;
    private RepositoryServiceRouter router;

    @BeforeEach
    void setUp() {
        jcrDelegate = mock(JcrRepositoryDelegate.class);
        dbDelegate = mock(DbRepositoryDelegate.class);
        pss = mock(ProjectStorageService.class);

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(JcrRepositoryDelegate.class)).thenReturn(jcrDelegate);
        when(ctx.getBean(DbRepositoryDelegate.class)).thenReturn(dbDelegate);
        when(ctx.getBean(ProjectStorageService.class)).thenReturn(pss);

        router = new RepositoryServiceRouter();
        router.setApplicationContext(ctx);
    }

    @Test
    void jcrProjectRoutesToJcrDelegate() throws Exception {
        when(pss.getStorageTypeOrNull("jcr-proj")).thenReturn(StorageType.JCR);
        InputStream expected = new ByteArrayInputStream("<xml/>".getBytes());
        when(jcrDelegate.readFile("/jcr-proj/file.xml")).thenReturn(expected);

        InputStream result = router.readFile("/jcr-proj/file.xml");

        assertSame(expected, result);
        verify(jcrDelegate).readFile("/jcr-proj/file.xml");
        verifyNoInteractions(dbDelegate);
    }

    @Test
    void dbProjectRoutesToDbDelegate() throws Exception {
        when(pss.getStorageTypeOrNull("db-proj")).thenReturn(StorageType.DB);
        DefaultUser user = new DefaultUser();
        user.setUsername("tester");

        router.saveFile("/db-proj/file.xml", "<content/>", false, null, user);

        verify(dbDelegate).saveFile("/db-proj/file.xml", "<content/>", false, null, user);
        verifyNoInteractions(jcrDelegate);
    }

    @Test
    void loadProjectsMergesBothDelegates() throws Exception {
        RepositoryFile jcrFile = new RepositoryFile();
        jcrFile.setName("jcr-proj");
        RepositoryFile dbFile = new RepositoryFile();
        dbFile.setName("db-proj");

        when(jcrDelegate.loadProjects("c1")).thenReturn(Collections.singletonList(jcrFile));
        when(dbDelegate.loadProjects("c1")).thenReturn(Collections.singletonList(dbFile));

        List<RepositoryFile> result = router.loadProjects("c1");

        assertEquals(2, result.size());
    }

    @Test
    void loadResourceSecurityConfigsAlwaysUsesJcr() throws Exception {
        UserPermission perm = new UserPermission();
        when(jcrDelegate.loadResourceSecurityConfigs("c1"))
                .thenReturn(Collections.singletonList(perm));

        List<UserPermission> result = router.loadResourceSecurityConfigs("c1");

        assertEquals(1, result.size());
        verify(jcrDelegate).loadResourceSecurityConfigs("c1");
        verifyNoInteractions(dbDelegate);
    }

    @Test
    void createProjectReadsStorageContextAndRoutes() throws Exception {
        DefaultUser user = new DefaultUser();
        user.setUsername("tester");
        RepositoryFile expected = new RepositoryFile();
        expected.setName("new-proj");

        StorageContext.set(StorageType.DB);
        when(dbDelegate.createProject("new-proj", user, false)).thenReturn(expected);

        RepositoryFile result = router.createProject("new-proj", user, false);

        assertSame(expected, result);
        verify(pss).register("new-proj", StorageType.DB);
        verify(dbDelegate).createProject("new-proj", user, false);
        verifyNoInteractions(jcrDelegate);

        // StorageContext 应已被 clear
        assertThrows(IllegalStateException.class, StorageContext::get);
    }
}
