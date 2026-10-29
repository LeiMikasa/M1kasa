package com.m1kasa.common.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.util.Assert;

import java.io.InputStream;

public class MinioTemplate {

    private static final int MAX_EXPIRY_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient minioClient;
    private final String bucket;

    public MinioTemplate(MinioClient minioClient, String bucket) {
        Assert.notNull(minioClient, "MinIO 客户端不能为空");
        Assert.hasText(bucket, "MinIO 存储桶不能为空");

        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    /**
     * 上传文件，输入流由调用方负责关闭。
     */
    public void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        Assert.hasText(objectKey, "文件路径不能为空");
        Assert.notNull(inputStream, "文件输入流不能为空");
        Assert.isTrue(size > 0, "文件大小必须大于 0");
        Assert.hasText(contentType, "文件内容类型不能为空");

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, size, null)
                            .contentType(contentType)
                            .build()
            );
        } catch (MinioException ex) {
            throw new IllegalStateException("上传文件失败", ex);
        }
    }

    /**
     * 生成临时读取链接，expirySeconds 的单位为秒。
     */
    public String getPresignedGetUrl(
            String objectKey,
            int expirySeconds
    ) {
        Assert.hasText(objectKey, "文件路径不能为空");
        Assert.isTrue(
                expirySeconds >= 1
                        && expirySeconds <= MAX_EXPIRY_SECONDS,
                "链接有效期必须在 1 秒到 7 天之间"
        );

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (MinioException ex) {
            throw new IllegalStateException("生成文件访问链接失败", ex);
        }
    }

    /**
     * 删除文件。
     */
    public void delete(String objectKey) {
        Assert.hasText(objectKey, "文件路径不能为空");

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (MinioException ex) {
            throw new IllegalStateException("删除文件失败", ex);
        }
    }
}
