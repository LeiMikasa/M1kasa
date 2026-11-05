package com.m1kasa.m1kasavoucherservice.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "xxl.job.executor",
        name = "enabled",
        havingValue = "true"
)
public class XxlJobConfig {
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
        @Value("${xxl.job.admin.addresses}") String adminAddresses,
        @Value("${xxl.job.admin.accessToken}") String accessToken,
        @Value("${xxl.job.admin.timeout}") int timeout,
        @Value("${xxl.job.executor.appname}") String appname,
        @Value("${xxl.job.executor.ip}") String ip,
        @Value("${xxl.job.executor.port}") int port,
        @Value("${xxl.job.executor.logpath}") String logPath,
        @Value("${xxl.job.executor.logretentiondays}") int logRetentionDays
    ){
        XxlJobSpringExecutor executor=new XxlJobSpringExecutor();
        executor.setEnabled(true);
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setTimeout(timeout);
        executor.setAppname(appname);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
