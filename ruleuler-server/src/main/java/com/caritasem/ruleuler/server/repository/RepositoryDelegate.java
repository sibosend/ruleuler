package com.caritasem.ruleuler.server.repository;

import com.bstek.urule.console.repository.RepositoryService;

/**
 * 委托接口，用于类型区分 JcrRepositoryDelegate 和 DbRepositoryDelegate。
 * RepositoryService 已继承 RepositoryReader，所有方法签名自动覆盖。
 */
public interface RepositoryDelegate extends RepositoryService {
}
