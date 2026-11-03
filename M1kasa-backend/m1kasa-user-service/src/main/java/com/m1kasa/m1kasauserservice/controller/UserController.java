package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.UserProfileUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/batch")
    public ResponseEntity<Result<List<UserDTO>>> listByIds(
            @RequestParam("ids") List<Long> ids
    ){
        try {
            List<UserDTO> users = userService.listByIds(ids);
            return ResponseEntity.ok(Result.ok(users));
        }catch (IllegalArgumentException ex){
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));
        }
    }
    //公开接口
    @GetMapping("/public/batch")
    public ResponseEntity<Result<List<UserDTO>>> listPublicByIds(
            @RequestParam("ids") List<Long> ids
    ){
        try {
            //查询指定作者的公开资料
            List<UserDTO> users = userService.listByIds(ids);
            return ResponseEntity.ok(Result.ok(users));
        }catch (IllegalArgumentException ex){
            return ResponseEntity.badRequest().body(Result.fail(ex.getMessage()));
        }
    }
    private static final Logger log =
            LoggerFactory.getLogger(UserController.class);

    @PutMapping(
            value = "/me",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Result<Void>> updateProfile(
            @RequestBody UserProfileUpdateRequest request
    ) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUserId();
        if (userId == null || userId <= 0) {
            return ResponseEntity.status(401)
                    .body(Result.fail("未登录或登录已过期"));
        }
        try {
            // 2.调用 Service，执行校验和更新
            boolean updated = userService.updateProfile(request);
            // 3.登录态可能还在，但数据库里的用户已不存在
            if (!updated) {
                return ResponseEntity.status(404)
                        .body(Result.fail("用户不存在"));
            }
            // 4.保存成功，不返回业务数据
            return ResponseEntity.ok(Result.ok());

        } catch (IllegalArgumentException ex) {
            //昵称不合法、头像路径不合法、头像文件不存在等
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));
        } catch (IllegalStateException | DataAccessException ex) {
            // MinIO 或数据库操作发生异常
            log.error(
                    "修改个人资料失败，userId={}",
                    userId,
                    ex
            );
            return ResponseEntity.status(503)
                    .body(Result.fail(
                            "资料保存结果暂未确认，请刷新个人资料后确认"
                    ));
        }
    }
}
