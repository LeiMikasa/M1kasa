package com.m1kasa.m1kasaorderservice.controller;


import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasaorderservice.service.MqDeadLetterService;
import com.m1kasa.order.api.DeadLetterDetailDTO;
import com.m1kasa.order.api.DeadLetterPageDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@Profile("local")
@RequestMapping("/local/mq/dead-letters")
public class LocalDeadLetterController {
    private static final Set<String> LOOPBACK_ADDRESSES=
            Set.of(
                    "127.0.0.1",
                    "::1",
                    "0:0:0:0:0:0:0:1"
            );
    private final MqDeadLetterService deadLetterService;

    public LocalDeadLetterController(MqDeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping
    public ResponseEntity<Result<DeadLetterPageDTO>> findPage(
            @RequestParam(name = "status",required = false)
            Integer status,
            @RequestParam(name = "current",defaultValue = "1")
            int current,
            @RequestParam(name = "size",defaultValue = "10")
            int size,
            HttpServletRequest request
    ){
        //仅允许本机直接访问
        if (!LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail("仅允许本机访问该调试接口"));
        }
        if (status!=null&&(status<0||status>2)){
            return ResponseEntity.badRequest()
                    .body(Result.fail(
                            "状态只能为0,1或2"
                    ));
        }
        if (current<1||size<1||size>50){
            return ResponseEntity.badRequest()
                    .body(Result.fail(
                            "页码必须大于0，每页条数必须在1到50之间"
                    ));
        }
        return ResponseEntity.ok(
          Result.ok(
                  deadLetterService.findPage(status,current,size)
          )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<DeadLetterDetailDTO>> findById(
            @PathVariable("id") Long id,
            HttpServletRequest request
    ){
        if (!LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail("仅允许本机访问该吊事接口"));
        }
        if (id<=0){
            return ResponseEntity.badRequest()
                    .body(Result.fail("记录ID必须大于0"));
        }
        DeadLetterDetailDTO detail = deadLetterService.findById(id);
        if (detail==null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("死信记录不存在"));
        }
        return ResponseEntity.ok(Result.ok(detail));
    }


}
