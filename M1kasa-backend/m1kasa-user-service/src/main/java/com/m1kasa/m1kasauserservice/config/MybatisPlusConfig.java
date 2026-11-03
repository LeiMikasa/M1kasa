package com.m1kasa.m1kasauserservice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(DbType.MYSQL);

        // 每页最多查询 50 条
        pagination.setMaxLimit(50L);

        // 超出总页数时返回空列表
        pagination.setOverflow(false);

        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
