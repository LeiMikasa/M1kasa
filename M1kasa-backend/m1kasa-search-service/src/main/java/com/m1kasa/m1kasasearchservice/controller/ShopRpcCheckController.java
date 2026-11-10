package com.m1kasa.m1kasasearchservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.client.ShopClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 仅在启用 local 环境时提供这个验证接口
@Profile("local")
@RestController
@RequestMapping("/internal/search")
public class ShopRpcCheckController {

    // 注入 Feign 为 ShopClient 创建的代理对象
    private final ShopClient shopClient;

    public ShopRpcCheckController(ShopClient shopClient) {
        this.shopClient = shopClient;
    }

    @GetMapping("/shop-scan")
    public Result<List<ShopDTO>> check() {

        // 从 ID 0 后开始查询，最多取两家店铺
        // 这里会通过 Feign 发起远程 HTTP 请求
        Result<List<ShopDTO>> result =
                shopClient.scanForSearch(0, 2);

        // 直接返回店铺服务的统一响应
        return result;
    }
}
