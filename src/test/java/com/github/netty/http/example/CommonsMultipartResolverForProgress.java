package com.github.netty.http.example;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 防止spring触发body解析
 * https://github.com/wangzihaogithub/spring-boot-protocol/issues/12
 */
@Component
public class CommonsMultipartResolverForProgress extends StandardServletMultipartResolver {
    @Override
    public boolean isMultipart(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/test/uploadForApache")) {
            return false;
        }
        return super.isMultipart(request);
    }
}