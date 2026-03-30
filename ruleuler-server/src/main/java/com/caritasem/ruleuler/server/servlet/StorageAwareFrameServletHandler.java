package com.caritasem.ruleuler.server.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.bstek.urule.RuleException;
import com.bstek.urule.console.servlet.frame.FrameServletHandler;
import com.caritasem.ruleuler.server.repository.StorageContext;
import com.caritasem.ruleuler.server.repository.StorageType;

/**
 * 覆盖 FrameServletHandler.createProject，注入 storageType 到 StorageContext。
 */
public class StorageAwareFrameServletHandler extends FrameServletHandler {

    @Override
    public void createProject(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String storageType = req.getParameter("storageType");
        if (storageType == null || storageType.isEmpty()) {
            throw new RuleException("storageType parameter is required");
        }
        StorageContext.set(StorageType.fromString(storageType));
        super.createProject(req, resp);
    }
}
