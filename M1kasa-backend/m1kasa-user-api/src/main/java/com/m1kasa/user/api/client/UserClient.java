package com.m1kasa.user.api.client;


import com.m1kasa.common.config.UserContextFeignConfiguration;
import com.m1kasa.common.dto.Result;
import com.m1kasa.user.api.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "m1kasa-user-service",configuration = UserContextFeignConfiguration.class)
public interface UserClient {
    @GetMapping("/user/batch")
    Result<List<UserDTO>> listByIds(
         @RequestParam("ids") List<Long> ids
    );

    @GetMapping("/user/me")
    Result<UserDTO> getCurrentUser(
            @RequestHeader("Authorization") String authorization
    );
    @GetMapping("/user/public/batch")
    Result<List<UserDTO>> listPublicByIds(
            @RequestParam("ids") List<Long> ids
    );
}
