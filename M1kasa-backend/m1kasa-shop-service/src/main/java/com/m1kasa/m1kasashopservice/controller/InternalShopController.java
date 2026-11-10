package com.m1kasa.m1kasashopservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasashopservice.service.ShopService;
import com.m1kasa.shop.api.ShopDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/internal/shop")
public class InternalShopController {
    private final ShopService shopService;

    public InternalShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/scan")
    public Result<List<ShopDTO>> scan(
        @RequestParam(name = "afterId",defaultValue = "0")
        long afterId,
        @RequestParam(name = "size",defaultValue = "200")
        int size
    ){
        //校验游标和单批数量
        if (afterId<0||size<1||size>200){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "afterId不能小于0，size必须在1到200之间"
            );
        }
        //分批查询店铺，返回统一响应
        return Result.ok(
                shopService.scanForSearch(afterId,size)
        );
    }
}
