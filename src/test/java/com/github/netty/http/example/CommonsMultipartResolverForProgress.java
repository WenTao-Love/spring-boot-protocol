package com.github.netty.http.example;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

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