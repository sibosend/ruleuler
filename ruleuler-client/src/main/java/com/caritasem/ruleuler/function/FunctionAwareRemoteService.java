package com.caritasem.ruleuler.function;

import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.service.RemoteService;

/**
 * 覆盖 urule.remoteService。返回包之前走 FunctionDepsGate。
 * 不齐/失败 return null，不改 core，不拦 KnowledgeCache。
 */
public class FunctionAwareRemoteService implements RemoteService {

    private final RemoteService delegate;
    private final FunctionDepsGate gate;

    public FunctionAwareRemoteService(RemoteService delegate, FunctionDepsGate gate) {
        this.delegate = delegate;
        this.gate = gate;
    }

    @Override
    public KnowledgePackage getKnowledge(String packageId, String timestamp) {
        if (!gate.ready(packageId)) {
            return null;
        }
        return delegate.getKnowledge(packageId, timestamp);
    }
}
