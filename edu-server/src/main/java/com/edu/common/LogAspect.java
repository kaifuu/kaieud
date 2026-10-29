package com.edu.common;

import com.edu.entity.SysLog;
import com.edu.mapper.SysLogMapper;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/** 操作日志切面：记录@Log标记的接口调用 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(logAnn)")
    public Object around(ProceedingJoinPoint pjp, Log logAnn) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            save(pjp, logAnn.type(), logAnn.value(), System.currentTimeMillis() - start, 0, null);
            return result;
        } catch (Throwable e) {
            save(pjp, logAnn.type(), logAnn.value(), System.currentTimeMillis() - start, 1, e.getMessage());
            throw e;
        }
    }

    private void save(ProceedingJoinPoint pjp, int logType, String operation, long duration, int status, String errorMsg) {
        try {
            SysLog sysLog = new SysLog();
            sysLog.setOperation(operation);
            sysLog.setLogType(logType);
            sysLog.setMethod(pjp.getSignature().toShortString());
            try {
                Object[] args = pjp.getArgs();
                if (args != null && args.length > 0) {
                    String params = objectMapper.writeValueAsString(args[0]);
                    sysLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
                }
            } catch (Exception ignore) { }
            sysLog.setDuration((int) duration);
            sysLog.setStatus(status);
            sysLog.setErrorMsg(errorMsg);
            sysLog.setCreateTime(LocalDateTime.now());

            LoginUser lu = AuthContext.get();
            if (lu != null) {
                sysLog.setUserId(lu.getId());
                sysLog.setTenantId(lu.getTenantId());
                sysLog.setUsername(lu.getUsername());
            }
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                sysLog.setIp(req.getRemoteAddr());
            }
            logMapper.insert(sysLog);
        } catch (Exception e) {
            log.warn("保存操作日志失败", e);
        }
    }
}
