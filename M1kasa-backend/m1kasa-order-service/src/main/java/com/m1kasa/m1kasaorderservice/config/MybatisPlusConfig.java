package com.m1kasa.m1kasaorderservice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // MyBatis-Plus 的插件入口
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        // 添加分页插件，指定数据库为 MySQL
        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(DbType.MYSQL);

        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

}
