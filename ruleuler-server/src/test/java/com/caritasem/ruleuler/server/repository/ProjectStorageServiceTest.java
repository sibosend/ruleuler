package com.caritasem.ruleuler.server.repository;

import com.bstek.urule.RuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStorageServiceTest extends BaseRepositoryTest {

    private ProjectStorageService service;

    @BeforeEach
    void setUp() {
        service = new ProjectStorageService(jdbcTemplate);
    }

    @Test
    void register_then_getStorageType_returns_correct_type() {
        service.register("proj-db", StorageType.DB);
        assertEquals(StorageType.DB, service.getStorageType("proj-db"));

        service.register("proj-jcr", StorageType.JCR);
        assertEquals(StorageType.JCR, service.getStorageType("proj-jcr"));
    }

    @Test
    void getStorageType_throws_when_project_not_found() {
        assertThrows(RuleException.class, () -> service.getStorageType("nonexistent"));
    }

    @Test
    void getStorageTypeOrNull_returns_null_when_project_not_found() {
        assertNull(service.getStorageTypeOrNull("nonexistent"));
    }

    @Test
    void register_duplicate_project_throws() {
        service.register("dup-proj", StorageType.DB);
        assertThrows(DuplicateKeyException.class, () -> service.register("dup-proj", StorageType.JCR));
    }
}
