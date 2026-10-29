package com.m1kasa.common.auth;

import com.m1kasa.common.auth.config.AuthMvcAutoConfiguration;
import com.m1kasa.common.base.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthMvcAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthMvcAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void preservesUserServiceLoginExclusionsAndInitializesContext() {
        runner.withPropertyValues(
                "m1kasa.auth.include-paths[0]=/user/**",
                "m1kasa.auth.exclude-paths[0]=/user/code",
                "m1kasa.auth.exclude-paths[1]=/user/login"
        ).run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                    context.getSourceApplicationContext()).build();
            mvc.perform(post("/user/code")).andExpect(status().isOk());
            mvc.perform(post("/user/login")).andExpect(status().isOk());
            mvc.perform(get("/user/me")).andExpect(status().isUnauthorized());
            mvc.perform(get("/user/me").header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk()).andExpect(content().string("2474"));
            assertNull(UserContext.getUserId());
        });
    }

    @Test
    void preservesOrderScopeAndRejectsForgedIdentity() {
        runner.withPropertyValues("m1kasa.auth.include-paths[0]=/order/**")
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                            context.getSourceApplicationContext()).build();
                    mvc.perform(get("/outside")).andExpect(status().isOk());
                    mvc.perform(get("/order/probe").header("X-User-Id", "2474"))
                            .andExpect(status().isUnauthorized());
                    mvc.perform(get("/order/probe").header("Authorization", "Bearer valid"))
                            .andExpect(status().isOk()).andExpect(content().string("2474"));
                    assertNull(UserContext.getUserId());
                });
    }

    @Test
    void preservesVoucherReserveProtectionAndPublicList() {
        runner.withPropertyValues(
                "m1kasa.auth.include-paths[0]=/voucher/seckill/*/reserve"
        ).run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                    context.getSourceApplicationContext()).build();
            mvc.perform(get("/voucher/list/1")).andExpect(status().isOk());
            mvc.perform(get("/voucher/seckill/1/reserve"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/voucher/seckill/1/reserve")
                            .header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk()).andExpect(content().string("2474"));
            assertNull(UserContext.getUserId());
        });
    }

    @Test
    void defaultsToProtectingAllPaths() {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                    context.getSourceApplicationContext()).build();
            mvc.perform(get("/outside")).andExpect(status().isUnauthorized());
        });
    }

    @Test
    void explicitDisableDoesNotRegisterAuthentication() {
        runner.withPropertyValues("m1kasa.auth.enabled=false").run(context -> {
            assertFalse(context.containsBean("m1kasaLoginInterceptor"));
            assertFalse(context.containsBean("m1kasaAuthWebMvcConfigurer"));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                    context.getSourceApplicationContext()).build();
            mvc.perform(get("/user/me"))
                    .andExpect(status().isOk()).andExpect(content().string("anonymous"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestConfiguration {

        @Bean
        @SuppressWarnings("unchecked")
        StringRedisTemplate redisTemplate() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            ValueOperations<String, String> tokens = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(tokens);
            when(tokens.get("m1kasa:login:token:valid")).thenReturn("2474");
            return redis;
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping({"/user/me", "/order/probe", "/voucher/seckill/1/reserve",
                "/voucher/list/1", "/outside"})
        public String currentUser() {
            Long userId = UserContext.getUserId();
            return userId == null ? "anonymous" : userId.toString();
        }

        @PostMapping({"/user/code", "/user/login"})
        public String login() {
            return "public";
        }
    }
}
