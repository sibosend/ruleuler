package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectControllerExportTest {

    private RepositoryService repositoryService;
    private ProjectController controller;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        controller = new ProjectController(repositoryService);
    }

    @Test
    void exportProducesValidZipWithCorrectEntry() throws Exception {
        String projectName = "test_project_fixture_001";
        byte[] fakeData = "hello-export-data".getBytes(StandardCharsets.UTF_8);

        doAnswer(inv -> {
            OutputStream os = inv.getArgument(1);
            os.write(fakeData);
            return null;
        }).when(repositoryService).exportXml(eq("/" + projectName), any(OutputStream.class));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.export(projectName, resp);

        byte[] body = resp.getContentAsByteArray();

        assertTrue(body.length >= 4, "响应体太短，不是合法ZIP");
        assertEquals(0x50, body[0] & 0xFF);
        assertEquals(0x4B, body[1] & 0xFF);
        assertEquals(0x03, body[2] & 0xFF);
        assertEquals(0x04, body[3] & 0xFF);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry, "ZIP应包含至少一个entry");
            assertEquals(projectName + ".dat", entry.getName());
            byte[] content = zis.readAllBytes();
            assertArrayEquals(fakeData, content, "entry内容应与exportXml写入的数据一致");
            assertNull(zis.getNextEntry(), "ZIP应只包含一个entry");
        }
    }
}
