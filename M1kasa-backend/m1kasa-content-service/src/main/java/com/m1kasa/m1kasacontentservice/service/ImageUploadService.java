package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.ImageUploadDTO;
import org.springframework.web.multipart.MultipartFile;


public interface ImageUploadService {

    ImageUploadDTO upload(
       MultipartFile file
    );
}
