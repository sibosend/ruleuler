package com.caritasem.ruleuler.server.auth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 拦截 @RequirePermission 注解，校验当前用户是否具备所需权限码。
 */
@Aspect
@Component
public class RequirePermissionAspect {

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                   RequirePermission requirePermission) throws Throwable {
        AuthContext.UserInfo user = AuthContext.get();
        if (user == null) {
            throw new SecurityException("未认证");
        }

        String requiredCode = requirePermission.value();
        List<String> permissions = user.getPermissions();

        // 通配符放行
        if (permissions != null && permissions.contains("*")) {
            return joinPoint.proceed();
        }

        // 校验具体权限码
        if (permissions == null || !permissions.contains(requiredCode)) {
            throw new SecurityException("无操作权限: " + requiredCode);
        }

        return joinPoint.proceed();
    }
}
