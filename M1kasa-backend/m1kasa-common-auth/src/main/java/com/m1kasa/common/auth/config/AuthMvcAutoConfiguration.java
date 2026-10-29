package com.m1kasa.common.auth.config;

import com.m1kasa.common.auth.interceptor.LoginInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET  //限定这份配置用于 Servlet Web 应用，结合 WebMvcConfigurer 的类条件，应用于你的 MVC 业务服务。WebFlux 网关不会加载这份 MVC 认证配置。
)
@EnableConfigurationProperties(AuthProperties.class)
@ConditionalOnProperty(
        prefix = "m1kasa.auth",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuthMvcAutoConfiguration {

    // 创建公共登录拦截器
    @Bean
    @ConditionalOnMissingBean(LoginInterceptor.class)
    public LoginInterceptor m1kasaLoginInterceptor(
            StringRedisTemplate stringRedisTemplate
    ) {
        return new LoginInterceptor(stringRedisTemplate);
    }

    // 将拦截器注册到 MVC 请求处理流程
    @Bean
    public WebMvcConfigurer m1kasaAuthWebMvcConfigurer(
            LoginInterceptor loginInterceptor,
            AuthProperties properties
    ) {
        Assert.notEmpty(
                properties.getIncludePaths(),
                "认证拦截路径不能为空"
        );
        Assert.notNull(
                properties.getExcludePaths(),
                "认证放行路径配置不能为 null"
        );

        return new WebMvcConfigurer() {

            @Override
            public void addInterceptors(
                    InterceptorRegistry registry
            ) {
                registry.addInterceptor(loginInterceptor)
                        .addPathPatterns(
                                properties.getIncludePaths()
                        )
                        .excludePathPatterns(
                                properties.getExcludePaths()
                        )
                        .order(-100);
            }
        };
    }
}
