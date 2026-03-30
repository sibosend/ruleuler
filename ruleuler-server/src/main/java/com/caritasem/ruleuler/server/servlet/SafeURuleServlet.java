package com.caritasem.ruleuler.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bstek.urule.console.servlet.URuleServlet;

/**
 * 修复 URuleServlet 的异常处理：原版在 catch 中写完 response 后仍 throw ServletException，
 * 导致 Spring Boot ErrorPageFilter 再次尝试写 response，触发 getWriter()/getOutputStream() 冲突。
 * 
 * 本类覆盖 service 方法，捕获已处理的异常，仅记录日志不再重抛。
 */
public class SafeURuleServlet extends URuleServlet {

    private static final Logger log = LoggerFactory.getLogger(SafeURuleServlet.class);

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            super.service(req, resp);
        } catch (ServletException e) {
            if (resp.isCommitted()) {
                // response 已提交（URuleServlet 已写入错误信息），不再重抛
                log.warn("URuleServlet 已处理异常，不再传播: {}", e.getMessage(), e);
                return;
            }
            throw e;
        }
    }
}
