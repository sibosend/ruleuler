package com.caritasem.ruleuler.rea;

import java.util.List;

/**
 * 自定义函数测试 bean。行为与 ruleuler-admin functionExecSpec.ts 的约定一致：
 *   score(s, n) = s.trim().length() * n
 *   isHigh(s)   = s.trim().length() > 5
 *   addTag(list, t) → void
 */
public class ReaTestRiskService {

    public Integer score(String airline, Integer factor) {
        return airline.trim().length() * factor;
    }

    public Boolean isHigh(String airline) {
        return airline.trim().length() > 5;
    }

    public void addTag(List<Object> list, Object tag) {
        list.add(tag);
    }
}
