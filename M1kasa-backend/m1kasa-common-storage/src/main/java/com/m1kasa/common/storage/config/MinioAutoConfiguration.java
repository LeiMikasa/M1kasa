package com.m1kasa.common.storage.config;

import com.m1kasa.common.storage.MinioTemplate;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(
        prefix = "m1kasa.minio",
        name = "enabled",
        havingValue = "true"
)
public class MinioAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(MinioTemplate.class)
    public MinioTemplate minioTemplate(
            MinioClient minioClient,
            MinioProperties properties
    ) {
        return new MinioTemplate(
                minioClient,
                properties.getBucket()
        );
    }
}
