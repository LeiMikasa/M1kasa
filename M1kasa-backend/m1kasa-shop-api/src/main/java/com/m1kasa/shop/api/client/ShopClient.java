package com.m1kasa.shop.api.client;

import com.m1kasa.common.dto.Result;
import com.m1kasa.shop.api.ShopDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// 声明要调用的目标服务名称
@FeignClient(name = "m1kasa-shop-service")
public interface ShopClient {

    // 与店铺服务 Controller 中的请求方式和路径对应
    @GetMapping("/internal/shop/scan")
    Result<List<ShopDTO>> scanForSearch(

            // 转换成 URL 查询参数 afterId
            @RequestParam("afterId")
            long afterId,

            // 转换成 URL 查询参数 size
            @RequestParam("size")
            int size
    );
}
