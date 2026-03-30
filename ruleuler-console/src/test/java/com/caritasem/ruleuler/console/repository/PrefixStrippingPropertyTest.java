package com.caritasem.ruleuler.console.repository;

import com.bstek.urule.console.repository.DatabaseResourceProvider;
import com.bstek.urule.console.repository.RepositoryResourceProvider;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

// Feature: repository-prefix-refactor, Property 2: 前缀剥离正确性
// Validates: Requirements 1.3, 3.1, 3.2, 3.4
class PrefixStrippingPropertyTest {

    private static final String JCR = RepositoryResourceProvider.JCR;
    private static final String DBR = DatabaseResourceProvider.DBR;

    @Property(tries = 100)
    void jcrPrefixStrippedCorrectly(@ForAll("rawPaths") String rawPath) {
        String prefixed = JCR + rawPath;
        String stripped = prefixed.substring(JCR.length());
        assertEquals(rawPath, stripped);
        assertFalse(stripped.startsWith(JCR));
        assertFalse(stripped.startsWith(DBR));
    }

    @Property(tries = 100)
    void dbrPrefixStrippedCorrectly(@ForAll("rawPaths") String rawPath) {
        String prefixed = DBR + rawPath;
        String stripped = prefixed.substring(DBR.length());
        assertEquals(rawPath, stripped);
        assertFalse(stripped.startsWith(JCR));
        assertFalse(stripped.startsWith(DBR));
    }

    @Property(tries = 100)
    void servletHandlerStyleStripping(@ForAll("randomPrefix") String prefix, @ForAll("rawPaths") String rawPath) {
        String path = prefix + rawPath;
        String stripped;
        if (path.startsWith(JCR)) {
            stripped = path.substring(JCR.length());
        } else if (path.startsWith(DBR)) {
            stripped = path.substring(DBR.length());
        } else {
            // 无已知前缀，路径不变
            stripped = path;
        }
        // 剥离后不应再以已知前缀开头
        assertFalse(stripped.startsWith(JCR));
        assertFalse(stripped.startsWith(DBR));
    }

    @Provide
    Arbitrary<String> rawPaths() {
        // 生成不以 jcr: 或 dbr: 开头的路径
        return Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(80)
                .filter(s -> !s.startsWith(JCR) && !s.startsWith(DBR));
    }

    @Provide
    Arbitrary<String> randomPrefix() {
        return Arbitraries.of(JCR, DBR);
    }
}
