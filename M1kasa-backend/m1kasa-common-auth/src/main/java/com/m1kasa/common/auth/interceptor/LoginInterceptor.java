package com.m1kasa.common.auth.interceptor;

import com.m1kasa.common.auth.annotation.AllowAnonymous;
import com.m1kasa.common.base.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;

public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {

        // 清理当前线程可能残留的用户信息
        UserContext.clear();
        //当前controller方法运行匿名访问，直接放行
        if (handler instanceof HandlerMethod handlerMethod
                &&handlerMethod.hasMethodAnnotation(AllowAnonymous.class)){
            return true;
        }
        String userIdHeader = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userIdHeader)){
            return unauthorized(response);
        }
        try {
            long userId=Long.parseLong(userIdHeader);
            if (userId<=0){
                return unauthorized(response);
            }
            UserContext.setUserId(userId);
            return true;
        }catch (NumberFormatException ex){
            return unauthorized(response);
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        UserContext.clear();
    }

    private boolean unauthorized(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType("application/json");

        response.getWriter().write("""
                {
                  "success": false,
                  "errorMsg": "未登录或登录已过期，请重新登录",
                  "data": null,
                  "total": null
                }
                """);

        return false;
    }
}
