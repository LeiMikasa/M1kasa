package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.ImageUploadDTO;
import com.m1kasa.m1kasacontentservice.service.ImageUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/blog/images")
public class ImageUploadController {

    private static final Logger log =
            LoggerFactory.getLogger(ImageUploadController.class);

    private final ImageUploadService imageUploadService;

    public ImageUploadController(
            ImageUploadService imageUploadService
    ) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Result<ImageUploadDTO>> upload(
            @RequestParam(
                    value = "file",
                    required = false
            ) MultipartFile file
    ) {
        try {
            ImageUploadDTO result =
                    imageUploadService.upload(file);

            return ResponseEntity.ok(Result.ok(result));

        } catch (IllegalArgumentException ex) {
            // 文件为空、格式不支持、图片尺寸不合法等
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException ex) {
            // 原始异常记录到服务端日志
            log.error("图片上传失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "图片上传暂不可用，请稍后重试"
                    ));
        }
    }
}
