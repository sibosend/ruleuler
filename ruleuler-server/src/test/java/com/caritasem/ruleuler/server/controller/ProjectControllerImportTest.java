package com.caritasem.ruleuler.server.controller;

import com.bstek.urule.console.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectControllerImportTest {

    private RepositoryService repositoryService;
    private ProjectController controller;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        controller = new ProjectController(repositoryService);
    }

    @Test
    void importProjectUnzipsAndCallsImportXmlWithEntryData() throws Exception {
        byte[] testData = "test-import-payload-12345".getBytes(StandardCharsets.UTF_8);
        byte[] zipBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("project.dat"));
            zos.write(testData);
            zos.closeEntry();
            zos.finish();
            zipBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "project.zip",
                "application/zip", zipBytes);

        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        doAnswer(inv -> {
            InputStream is = inv.getArgument(0);
            byte[] actual = is.readAllBytes();
            assertArrayEquals(testData, actual, "传给importXml的数据应与ZIP entry内容一致");
            return null;
        }).when(repositoryService).importXml(captor.capture(), eq(true));

        controller.importProject(file, true);

        verify(repositoryService, times(1)).importXml(any(InputStream.class), eq(true));
    }
}
