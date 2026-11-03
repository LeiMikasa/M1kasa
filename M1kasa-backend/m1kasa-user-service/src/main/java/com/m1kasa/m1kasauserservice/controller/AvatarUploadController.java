package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.AvatarUploadService;
import com.m1kasa.user.api.AvatarUploadDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user/avatar")
public class AvatarUploadController {

    private static final Logger log =
            LoggerFactory.getLogger(AvatarUploadController.class);

    private final AvatarUploadService avatarUploadService;

    public AvatarUploadController(
            AvatarUploadService avatarUploadService
    ) {
        this.avatarUploadService = avatarUploadService;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Result<AvatarUploadDTO>> upload(
            @RequestParam(
                    value = "file",
                    required = false
            ) MultipartFile file
    ) {
        try {
            AvatarUploadDTO result = avatarUploadService.upload(file);

            return ResponseEntity.ok(Result.ok(result));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException ex) {
            log.error("头像上传失败", ex);

            return ResponseEntity.status(503)
                    .body(Result.fail("头像上传暂不可用，请稍后重试"));
        }
    }
}
