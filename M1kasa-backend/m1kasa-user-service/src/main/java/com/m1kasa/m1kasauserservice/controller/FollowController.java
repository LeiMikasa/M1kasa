package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.FollowService;
import com.m1kasa.user.api.FollowPageDTO;
import com.m1kasa.user.api.FollowRequest;
import com.m1kasa.user.api.FollowStateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class FollowController {
    private static final Logger log=
            LoggerFactory.getLogger(FollowController.class);
    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }
    /*
    * 查询当前用户是否关注目标作者
    * */
    @GetMapping("/{targetUserId}/follow")
    public ResponseEntity<Result<FollowStateDTO>> getFollowState(
            @PathVariable("targetUserId") Long targetUserId
    ) {
        try {
            FollowStateDTO state =
                    followService.getFollowState(targetUserId);

            return ResponseEntity.ok(Result.ok(state));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException
                 | DataAccessException
                 | TransactionException ex) {
            log.error(
                    "查询关注状态失败，targetUserId={}",
                    targetUserId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "关注状态暂不可用，请稍后重试"
                    ));
        }
    }
    /*
    * 设置关注状态
    * */
    @PutMapping(
       value = "/{targetUserId}/follow",
       consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Result<FollowStateDTO>> setFollowed(
            @PathVariable("targetUserId") Long targetUserId,
            @RequestBody FollowRequest request
            ){
        // false 是合法的取消关注，只有 null 表示漏传
        if (request == null || request.followed() == null) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(
                            "请明确指定 followed 为 true 或 false"
                    ));
        }

        try {
            FollowStateDTO state = followService.setFollowed(
                    targetUserId,
                    request.followed()
            );

            return ResponseEntity.ok(Result.ok(state));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException
                 | DataAccessException
                 | TransactionException ex) {
            log.error(
                    "修改关注状态失败，targetUserId={}, followed={}",
                    targetUserId,
                    request.followed(),
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "关注操作结果暂未确认，请刷新关注状态"
                    ));
        }
    }
    /**
     * 我的关注列表。
     */
    @GetMapping("/me/following")
    public ResponseEntity<Result<FollowPageDTO>> findFollowing(
            @RequestParam(
                    name = "current",
                    defaultValue = "1"
            ) int current,
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            ) int size
    ) {
        try {
            FollowPageDTO page = followService.findFollowing(
                    current,
                    size
            );

            return ResponseEntity.ok(Result.ok(page));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException
                 | DataAccessException
                 | TransactionException ex) {
            log.error("查询我的关注列表失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "关注列表暂不可用，请稍后重试"
                    ));
        }
    }

    /**
     * 我的粉丝列表。
     */
    @GetMapping("/me/followers")
    public ResponseEntity<Result<FollowPageDTO>> findFollowers(
            @RequestParam(
                    name = "current",
                    defaultValue = "1"
            ) int current,
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            ) int size
    ) {
        try {
            FollowPageDTO page = followService.findFollowers(
                    current,
                    size
            );

            return ResponseEntity.ok(Result.ok(page));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException
                 | DataAccessException
                 | TransactionException ex) {
            log.error("查询我的粉丝列表失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "粉丝列表暂不可用，请稍后重试"
                    ));
        }
    }
}
