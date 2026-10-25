package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.UserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService){
        this.userService=userService;
    }
    @GetMapping("/{id}")
    public Result<UserDTO> getById(@PathVariable Long id){
        if (id<=0){
            return Result.fail("用户id必须大于0");
        }
        UserDTO user=userService.getById(id);
        return user==null?Result.fail("用户不存在"):
                Result.ok(user);
    }
    @GetMapping("/me")
    public ResponseEntity<Result<UserDTO>> me() {
        Long userId = UserContext.getUserId();

        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Result.fail("未登录或登录已过期"));
        }

        UserDTO user = userService.getById(userId);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body(Result.fail("用户不存在"));
        }

        return ResponseEntity.ok(Result.ok(user));
    }
}
