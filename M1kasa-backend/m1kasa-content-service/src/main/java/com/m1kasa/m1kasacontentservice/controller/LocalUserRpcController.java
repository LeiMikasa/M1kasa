package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.client.UserClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Profile("local")
@RestController
@RequestMapping("/local/rpc/users")
public class LocalUserRpcController {
    private final UserClient userClient;

    public LocalUserRpcController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping
    public Result<List<UserDTO>> query(
        @RequestParam("ids") List<Long> ids,
        @RequestHeader("Authorization") String authorization
    ){
          //通过Feign代理，远程调用用户服务
          return userClient.listByIds(ids,authorization);
    }
}
