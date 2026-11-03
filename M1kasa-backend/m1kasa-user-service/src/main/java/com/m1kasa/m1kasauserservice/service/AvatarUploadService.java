package com.m1kasa.m1kasauserservice.service;

import com.m1kasa.user.api.AvatarUploadDTO;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarUploadService {

    AvatarUploadDTO upload(MultipartFile file);
}
