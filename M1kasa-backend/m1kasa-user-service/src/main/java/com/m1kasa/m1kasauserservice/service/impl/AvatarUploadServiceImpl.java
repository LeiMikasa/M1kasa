package com.m1kasa.m1kasauserservice.service.impl;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.m1kasauserservice.service.AvatarUploadService;
import com.m1kasa.user.api.AvatarUploadDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class AvatarUploadServiceImpl implements AvatarUploadService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final int URL_EXPIRY_SECONDS = 600;

    private final MinioTemplate minioTemplate;

    public AvatarUploadServiceImpl(MinioTemplate minioTemplate) {
        this.minioTemplate = minioTemplate;
    }
    @Override
    public AvatarUploadDTO upload(MultipartFile file) {
        // 1. 获取当前用户，公共登录拦截器已经完成身份校验
        Long userId = UserContext.getUserId();

        if (userId == null || userId <= 0) {
            throw new IllegalStateException("当前登录用户上下文缺失");
        }

        // 2. 校验文件大小
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("头像不能超过 5MB");
        }

        // 3. 根据文件内容识别格式和尺寸
        String format = detectFormat(file);

        String extension = "jpeg".equals(format) ? "jpg" : "png";
        String contentType = "image/" + format;

        // 4. 服务端生成路径，与修改资料接口的校验规则一致
        String objectKey = "avatar/"
                + userId
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;

        // 5. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioTemplate.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    contentType
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取头像文件失败", ex);
        }

        // 6. 返回临时预览地址
        String url = minioTemplate.getPresignedGetUrl(
                objectKey,
                URL_EXPIRY_SECONDS
        );

        return new AvatarUploadDTO(objectKey, url);
    }
    private String detectFormat(MultipartFile file) {
        try (
                InputStream inputStream = file.getInputStream();
                MemoryCacheImageInputStream imageInput =
                        new MemoryCacheImageInputStream(inputStream)
        ) {
            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "无法识别图片，请上传 JPEG 或 PNG 图片"
                );
            }

            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName()
                        .toLowerCase(Locale.ROOT);

                if (!"jpeg".equals(format)
                        && !"png".equals(format)) {
                    throw new IllegalArgumentException(
                            "仅支持 JPEG、PNG 图片"
                    );
                }

                reader.setInput(imageInput, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0
                        || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "图片尺寸不合法或超过 2000 万像素"
                    );
                }

                return format;

            } finally {
                reader.dispose();
            }

        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "图片识别失败，请检查文件是否损坏",
                    ex
            );
        }
    }
}
