package com.m1kasa.shop.api.client;

import com.m1kasa.common.dto.Result;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.ShopTypeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @GetMapping("/shop/{id}")
    Result<ShopDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/shop/of/type")
    Result<List<ShopDTO>> findNearbyByType(
            @RequestParam("typeId") long typeId,
            @RequestParam("current") long current,
            @RequestParam("x") double x,
            @RequestParam("y") double y
    );

    @GetMapping("/shop-type/list")
    Result<List<ShopTypeDTO>> listShopTypes();
}
