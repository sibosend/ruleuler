package com.caritasem.ruleuler.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NewWork {
 
    @Value("${name}")
    private String name;
 
    @RequestMapping("/ping")
    public String t1() {
        System.out.println("在此打印name：" + name);
        return name;
    }
}
