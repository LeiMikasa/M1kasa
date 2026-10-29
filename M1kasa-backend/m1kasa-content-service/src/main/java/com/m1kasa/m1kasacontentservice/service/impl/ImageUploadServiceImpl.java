package com.m1kasa.m1kasacontentservice.service.impl;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.content.api.ImageUploadDTO;
import com.m1kasa.m1kasacontentservice.service.ImageUploadService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.client.UserClient;
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
public class ImageUploadServiceImpl implements ImageUploadService {

    private static final long MAX_FILE_SIZE=5L*1024*1024;
    private static final long MAX_PIXELS=20_000_000L;
    private static int URL_EXPIRY_SECONDS=600;

    private final UserClient userClient;
    private final MinioTemplate minioTemplate;

    public ImageUploadServiceImpl(UserClient userClient, MinioTemplate minioTemplate) {
        this.userClient = userClient;
        this.minioTemplate = minioTemplate;
    }


    @Override
    public ImageUploadDTO upload(MultipartFile file) {
       //公共登录拦截器已经准备好当前用户
        Long userId= UserContext.getUserId();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("当前用户上下文缺失");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片不能超过 5MB");
        }
        //读取图片内容，识别格式和尺寸
        String format=detectFormat(file);
        String extension="jpeg".equals(format) ? "jpg" : "png";
        String contentType = "image/" + format;

        //服务端生成文件路径
        String objectKey="blog/"+userId+"/"+ UUID.randomUUID()+"."+extension;
        //上传文件
        try (InputStream inputStream = file.getInputStream()){
            minioTemplate.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    contentType
            );
        }catch (IOException ex){
            throw new IllegalStateException("读取上传文件失败",ex);
        }
        // 生成十分钟有效的预览链接
        String url = minioTemplate.getPresignedGetUrl(
                objectKey,
                URL_EXPIRY_SECONDS
        );

        return new ImageUploadDTO(objectKey, url);


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
