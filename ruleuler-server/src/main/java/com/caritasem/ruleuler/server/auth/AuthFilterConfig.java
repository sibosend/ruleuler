package com.caritasem.ruleuler.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtUtil jwtUtil, UserDao userDao, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new JwtAuthFilter(jwtUtil, userDao, objectMapper));
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RbacPermissionFilter> rbacPermissionFilterRegistration(
            ObjectMapper objectMapper) {
        FilterRegistrationBean<RbacPermissionFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RbacPermissionFilter(objectMapper));
        reg.addUrlPatterns("/*");
        reg.setOrder(2);
        return reg;
    }
}
