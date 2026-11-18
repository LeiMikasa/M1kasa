package com.m1kasa.common.config;

import com.m1kasa.common.base.context.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class UserContextFeignConfiguration {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public RequestInterceptor userIdRequestInterceptor() {
        return template -> {
            template.removeHeader(USER_ID_HEADER);

            Long userId = UserContext.getUserId();
            if (userId != null && userId > 0) {
                template.header(USER_ID_HEADER, userId.toString());
            }
        };
    }
}
