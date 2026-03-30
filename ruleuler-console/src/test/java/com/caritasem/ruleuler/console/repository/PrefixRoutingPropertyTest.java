package com.caritasem.ruleuler.console.repository;

import com.bstek.urule.console.repository.DatabaseResourceProvider;
import com.bstek.urule.console.repository.RepositoryResourceProvider;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

// Feature: repository-prefix-refactor, Property 1: 前缀路由正确性
// Validates: Requirements 1.2, 2.3
class PrefixRoutingPropertyTest {

    private final RepositoryResourceProvider jcrProvider = new RepositoryResourceProvider();
    private final DatabaseResourceProvider dbrProvider = new DatabaseResourceProvider();

    @Property(tries = 100)
    void jcrPrefixRoutesToJcrProviderOnly(@ForAll("jcrPaths") String path) {
        assertTrue(jcrProvider.support(path));
        assertFalse(dbrProvider.support(path));
    }

    @Property(tries = 100)
    void dbrPrefixRoutesToDbrProviderOnly(@ForAll("dbrPaths") String path) {
        assertFalse(jcrProvider.support(path));
        assertTrue(dbrProvider.support(path));
    }

    @Property(tries = 100)
    void unknownPrefixRoutesToNeither(@ForAll("unknownPaths") String path) {
        assertFalse(jcrProvider.support(path));
        assertFalse(dbrProvider.support(path));
    }

    @Provide
    Arbitrary<String> jcrPaths() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                .map(s -> "jcr:" + s);
    }

    @Provide
    Arbitrary<String> dbrPaths() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                .map(s -> "dbr:" + s);
    }

    @Provide
    Arbitrary<String> unknownPaths() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.startsWith("jcr:") && !s.startsWith("dbr:"));
    }
}
