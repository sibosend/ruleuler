package com.caritasem.ruleuler.function;

import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.service.RemoteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionDepsGateTest {

    @Test
    void statusMismatch() {
        assertEquals("mismatch", FunctionDepsGate.statusOf("1.2.0", "1.1.0"));
        assertEquals("mismatch", FunctionDepsGate.statusOf("1.2.0", null));
        assertEquals("ok", FunctionDepsGate.statusOf("1.2.0", "1.2.0"));
    }

    @Test
    void reportOnlyOnChange() {
        FunctionDepsGate gate = new FunctionDepsGate("http://localhost:16009", 5000);
        assertTrue(gate.noteAndShouldReport("p/pkg", "geo", "mismatch"));
        assertFalse(gate.noteAndShouldReport("p/pkg", "geo", "mismatch"));
        assertTrue(gate.noteAndShouldReport("p/pkg", "geo", "ok"));
        assertFalse(gate.noteAndShouldReport("p/pkg", "geo", "ok"));
    }

    @Test
    void projectOf() {
        assertEquals("airport", FunctionDepsGate.projectOf("airport/gate_pkg"));
        assertEquals("airport", FunctionDepsGate.projectOf("/airport/gate_pkg"));
    }

    @Test
    void remoteServiceReturnsNullWhenGateRejects() {
        FunctionDepsGate gate = mock(FunctionDepsGate.class);
        RemoteService delegate = mock(RemoteService.class);
        when(gate.ready("p/pkg")).thenReturn(false);
        FunctionAwareRemoteService svc = new FunctionAwareRemoteService(delegate, gate);
        assertNull(svc.getKnowledge("p/pkg", "1"));
        verify(delegate, never()).getKnowledge("p/pkg", "1");
    }

    @Test
    void remoteServiceDelegatesWhenReady() {
        FunctionDepsGate gate = mock(FunctionDepsGate.class);
        RemoteService delegate = mock(RemoteService.class);
        KnowledgePackage kp = mock(KnowledgePackage.class);
        when(gate.ready("p/pkg")).thenReturn(true);
        when(delegate.getKnowledge("p/pkg", "1")).thenReturn(kp);
        FunctionAwareRemoteService svc = new FunctionAwareRemoteService(delegate, gate);
        assertSame(kp, svc.getKnowledge("p/pkg", "1"));
    }
}
